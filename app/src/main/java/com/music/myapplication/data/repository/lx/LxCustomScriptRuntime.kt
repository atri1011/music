package com.music.myapplication.data.repository.lx

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.math.max
import kotlin.math.min
import kotlin.text.Charsets.UTF_8
import java.util.concurrent.TimeUnit

@Singleton
class LxCustomScriptRuntime @Inject constructor(
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val alertCoordinator: LxCustomUpdateAlertCoordinator
) {
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val runtimeMutex = Mutex()
    private val requestInvocationSeq = AtomicInteger(0)
    private val pendingRequestInvocations = ConcurrentHashMap<Int, CompletableDeferred<LxInvocationCompletion>>()

    private var activeSession: ActiveLxSession? = null

    suspend fun validate(
        scriptId: String,
        rawScript: String
    ): LxScriptValidationResult {
        val metadata = parseMetadata(scriptId = scriptId, rawScript = rawScript)
            ?: return LxScriptValidationResult(
                metadata = LxScriptMetadata(id = scriptId, rawScript = rawScript),
                declaredSources = emptyList(),
                updateAlert = null,
                validationError = "脚本头部元信息注释格式不合法，至少需要 `@name`"
            )

        return runCatching {
            val session = createSession(
                scriptId = scriptId,
                metadata = metadata,
                rawScript = rawScript
            )
            session.close()
            LxScriptValidationResult(
                metadata = metadata,
                declaredSources = session.declaredSources.values.toList(),
                updateAlert = session.updateAlert,
                validationError = null
            )
        }.getOrElse { error ->
            LxScriptValidationResult(
                metadata = metadata,
                declaredSources = emptyList(),
                updateAlert = null,
                validationError = error.message?.trim().orEmpty().ifBlank { "脚本校验失败" }
            )
        }
    }

    suspend fun invalidate(scriptId: String? = null) {
        runtimeMutex.withLock {
            val current = activeSession ?: return
            if (scriptId != null && current.script.id != scriptId) return
            current.close()
            activeSession = null
        }
        cancelPendingRequestInvocations("落雪脚本会话已失效")
    }

    suspend fun warmUp(script: LxCustomScript) {
        ensureSession(script = script, showUpdateAlert = true)
    }

    fun dismissUpdateAlert() {
        alertCoordinator.dismiss()
    }

    suspend fun resolveMusicUrl(
        script: LxCustomScript,
        sourceId: String,
        payload: LxMusicRequestPayload
    ): Result<String> {
        return try {
            val session = ensureSession(script = script, showUpdateAlert = true)
            val source = session.declaredSources[sourceId]
                ?: return Result.Error(
                    AppError.Parse(
                        message = "当前落雪脚本未声明支持${LxKnownSource.fromId(sourceId)?.displayName ?: sourceId}"
                    )
                )
            if (!source.qualities.contains(payload.type)) {
                return Result.Error(AppError.Parse(message = "当前落雪脚本不支持 ${payload.type}"))
            }

            val url = invokeRequestHandler(
                session = session,
                request = LxRuntimeRequest(
                    source = sourceId,
                    action = "musicUrl",
                    info = payload
                )
            )
            val playableUrl = extractHttpUrl(url)
            if (playableUrl.isNullOrBlank()) {
                Result.Error(AppError.Parse(message = "落雪脚本未返回有效的 HTTP 播放地址"))
            } else {
                Result.Success(playableUrl)
            }
        } catch (error: Exception) {
            Log.e(TAG, "LX 脚本执行失败: scriptId=${script.id}, sourceId=$sourceId", error)
            Result.Error(AppError.Parse(message = error.message?.trim().orEmpty().ifBlank { "落雪脚本执行失败" }, cause = error))
        }
    }

    private suspend fun ensureSession(
        script: LxCustomScript,
        showUpdateAlert: Boolean
    ): ActiveLxSession {
        val key = script.runtimeKey
        var reusableSession: ActiveLxSession? = null
        val previousSession = runtimeMutex.withLock {
            val current = activeSession
            if (current != null && current.script.runtimeKey == key && !current.quickJs.isClosed) {
                reusableSession = current
                null
            } else {
                activeSession = null
                current
            }
        }

        reusableSession?.let { current ->
            if (showUpdateAlert) {
                current.showUpdateAlertIfNeeded(alertCoordinator)
            }
            return current
        }

        previousSession?.close()
        val metadata = parseMetadata(script.id, script.rawScript)
            ?: throw IllegalStateException("当前脚本头部元信息注释格式不合法")
        // Script initialization can synchronously re-enter host callbacks, so session creation must stay outside the mutex.
        val created = createSession(
            scriptId = script.id,
            metadata = metadata,
            rawScript = script.rawScript
        ).copy(script = script)

        return runtimeMutex.withLock {
            val current = activeSession
            if (current != null && current.script.runtimeKey == key && !current.quickJs.isClosed) {
                created.close()
                if (showUpdateAlert) {
                    current.showUpdateAlertIfNeeded(alertCoordinator)
                }
                current
            } else {
                current?.close()
                activeSession = created
                if (showUpdateAlert) {
                    created.showUpdateAlertIfNeeded(alertCoordinator)
                }
                created
            }
        }
    }

    private suspend fun createSession(
        scriptId: String,
        metadata: LxScriptMetadata,
        rawScript: String
    ): ActiveLxSession {
        val buildState = SessionBuildState(metadata = metadata)
        val quickJs = QuickJs.create(jobDispatcher = runtimeDispatcher)
        bindHostFunctions(quickJs = quickJs, buildState = buildState)

        quickJs.evaluate<Unit>(
            code = buildPrelude(metadata = metadata),
            filename = "lx-prelude.js"
        )
        quickJs.evaluate<Any?>(
            code = rawScript,
            filename = "${metadata.name.ifBlank { scriptId }}.js"
        )

        val initedPayload = buildState.initedPayload
            ?: throw IllegalStateException("脚本未发送 EVENT_NAMES.inited")
        val declaredSources = parseDeclaredSources(initedPayload)
        if (declaredSources.isEmpty()) {
            throw IllegalStateException("脚本未声明任何可用来源")
        }

        return ActiveLxSession(
            script = LxCustomScript(
                id = scriptId,
                rawScript = rawScript,
                name = metadata.name,
                description = metadata.description,
                version = metadata.version,
                author = metadata.author,
                homepage = metadata.homepage,
                declaredSources = declaredSources.keys.toList(),
                updatedAt = System.currentTimeMillis()
            ),
            quickJs = quickJs,
            declaredSources = declaredSources,
            updateAlert = buildState.updateAlert
        )
    }

    private fun bindHostFunctions(
        quickJs: QuickJs,
        buildState: SessionBuildState
    ) {
        quickJs.function("__lxHostSend") { args ->
            val eventName = args.stringArg(0)
            val jsonPayload = args.stringArg(1)
            when (eventName) {
                "inited" -> {
                    buildState.initedPayload = jsonPayload.toJsonObjectOrNull(json)
                }
                "updateAlert" -> {
                    if (buildState.updateAlert == null) {
                        buildState.updateAlert = sanitizeUpdateAlert(
                            metadata = buildState.metadata,
                            payload = jsonPayload.toJsonObjectOrNull(json)
                        )
                    }
                }
                else -> Unit
            }
        }

        quickJs.function("__lxHostConsole") { args ->
            val level = args.stringArg(0).ifBlank { "log" }
            val message = args.stringArg(1)
            when (level.lowercase()) {
                "warn" -> Log.w(TAG, "[JS] $message")
                "error" -> Log.e(TAG, "[JS] $message")
                else -> Log.d(TAG, "[JS] $message")
            }
        }

        quickJs.function("__lxHostSetTimeout") { args ->
            val timerId = args.intArg(0)
            val delayMs = max(0, args.intArg(1))
            val job = runtimeScope.launch {
                delay(delayMs.toLong())
                fireTimer(timerId)
            }
            runtimeScope.launch {
                runtimeMutex.withLock {
                    activeSession?.timerJobs?.set(timerId, job)
                }
            }
        }

        quickJs.function("__lxHostClearTimeout") { args ->
            val timerId = args.intArg(0)
            runtimeScope.launch {
                runtimeMutex.withLock {
                    val current = activeSession ?: return@withLock
                    current.timerJobs.remove(timerId)?.cancel()
                }
            }
        }

        quickJs.function("__lxHostBufferFrom") { args ->
            val value = args.firstOrNull()
            val encoding = args.stringArg(1).ifBlank { "utf8" }
            when (value) {
                is ByteArray -> value
                else -> encodeToBytes(value?.toString().orEmpty(), encoding)
            }
        }

        quickJs.function("__lxHostBufferToString") { args ->
            val bytes = args.byteArrayArg(0)
            val encoding = args.stringArg(1).ifBlank { "utf8" }
            decodeFromBytes(bytes, encoding)
        }

        quickJs.function("__lxHostMd5") { args ->
            val digest = MessageDigest.getInstance("MD5").digest(args.byteArrayArg(0))
            digest.toHexString()
        }

        quickJs.function("__lxHostRandomBytes") { args ->
            val size = min(max(0, args.intArg(0)), 4096)
            ByteArray(size).also(secureRandom::nextBytes)
        }

        quickJs.function("__lxHostAesEncrypt") { args ->
            val input = args.byteArrayArg(0)
            val mode = args.stringArg(1).ifBlank { "cbc" }
            val key = args.byteArrayArg(2)
            val iv = args.byteArrayArgOrNull(3)
            encryptAes(
                input = input,
                key = key,
                iv = iv,
                mode = mode
            )
        }

        quickJs.function("__lxHostRsaEncrypt") { args ->
            val input = args.byteArrayArg(0)
            val publicKeyPem = args.stringArg(1)
            encryptRsa(input = input, publicKeyPem = publicKeyPem)
        }

        quickJs.asyncFunction("__lxHostRequest") { args ->
            val url = args.stringArg(0)
            val optionsJson = args.stringArg(1)
            val requestId = args.intArg(2)
            enqueueRequest(
                requestId = requestId,
                url = url,
                optionsJson = optionsJson
            )
        }

        quickJs.function("__lxHostCancelRequest") { args ->
            val requestId = args.intArg(0)
            runtimeScope.launch {
                runtimeMutex.withLock {
                    activeSession?.activeCalls?.get(requestId)?.cancel()
                }
            }
        }
        quickJs.function("__lxHostResolveRequestInvocation") { args ->
            completeRequestInvocation(
                invocationId = args.intArg(0),
                completion = LxInvocationCompletion(value = args.stringArg(1))
            )
        }
        quickJs.function("__lxHostRejectRequestInvocation") { args ->
            completeRequestInvocation(
                invocationId = args.intArg(0),
                completion = LxInvocationCompletion(error = args.stringArg(1))
            )
        }
    }

    private suspend fun invokeRequestHandler(
        session: ActiveLxSession,
        request: LxRuntimeRequest
    ): String? {
        runtimeMutex.withLock {
            val active = activeSession
            require(active === session && !session.quickJs.isClosed) { "落雪脚本会话已失效" }
        }
        val payloadJson = json.encodeToString(request)
        val invocationId = requestInvocationSeq.incrementAndGet()
        val deferred = CompletableDeferred<LxInvocationCompletion>()
        pendingRequestInvocations[invocationId] = deferred
        return try {
            session.quickJs.evaluate<Unit>(
                code = """
                    (() => {
                      const invocationId = $invocationId;
                      const handler = globalThis.__lxHandlers?.[globalThis.EVENT_NAMES.request];
                      if (typeof handler !== "function") {
                        __lxHostRejectRequestInvocation(invocationId, "脚本未注册 request 事件处理器");
                        return;
                      }
                      Promise.resolve()
                        .then(() => handler($payloadJson))
                        .then((result) => {
                          if (typeof result === "string") {
                            __lxHostResolveRequestInvocation(invocationId, result);
                            return;
                          }
                          if (result == null) {
                            __lxHostResolveRequestInvocation(invocationId, "");
                            return;
                          }
                          __lxHostResolveRequestInvocation(invocationId, JSON.stringify(result));
                        })
                        .catch((error) => {
                          const message = error?.stack || error?.message || String(error);
                          __lxHostRejectRequestInvocation(invocationId, message);
                        });
                    })();
                """.trimIndent(),
                filename = "lx-invoke-request.js"
            )
            val completion = deferred.await()
            completion.error?.let { throw IllegalStateException(it) }
            completion.value
        } finally {
            pendingRequestInvocations.remove(invocationId)
        }
    }

    private suspend fun enqueueRequest(
        requestId: Int,
        url: String,
        optionsJson: String
    ) {
        val requestUrl = url.trim()
        val httpUrl = requestUrl.toHttpUrlOrNull()
            ?: run {
                deliverRequestResult(
                    requestId = requestId,
                    err = requestError("请求 URL 无效"),
                    response = null,
                    body = null
                )
                return
            }

        val options = parseRequestOptions(optionsJson)
        val timeoutSeconds = options.timeoutSeconds.coerceIn(1, 30).toLong()
        val client = okHttpClient.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        val requestBuilder = Request.Builder()
            .url(httpUrl)

        options.headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val requestBody = buildRequestBody(options)
        val method = options.method.ifBlank {
            if (requestBody != null) "POST" else "GET"
        }.uppercase()
        requestBuilder.method(method, if (method == "GET" || method == "HEAD") null else requestBody ?: ByteArray(0).toRequestBody())

        val call = client.newCall(requestBuilder.build())
        runtimeMutex.withLock {
            activeSession?.activeCalls?.set(requestId, call)
        }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val errName = when {
                        call.isCanceled() -> "AbortError"
                        e.message?.contains("timeout", ignoreCase = true) == true -> "TimeoutError"
                        else -> "NetworkError"
                    }
                    runtimeScope.launch {
                        deliverRequestResult(
                            requestId = requestId,
                            err = requestError(e.message?.ifBlank { "请求失败" } ?: "请求失败", errName),
                            response = null,
                            body = null
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyString = it.body?.string().orEmpty()
                        val normalizedBody = parseLxResponseBody(bodyString, json)
                        runtimeScope.launch {
                            deliverRequestResult(
                                requestId = requestId,
                                err = null,
                                response = buildJsonObject {
                                    put("statusCode", it.code)
                                    put(
                                        "headers",
                                        buildJsonObject {
                                            it.headers.names().forEach { name ->
                                                put(name, it.headers.values(name).joinToString(", "))
                                            }
                                        }
                                    )
                                    put("body", normalizedBody)
                                },
                                body = bodyString
                            )
                        }
                    }
                }
            }
        )
    }

    private suspend fun deliverRequestResult(
        requestId: Int,
        err: JsonObject?,
        response: JsonObject?,
        body: String?
    ) {
        val session = runtimeMutex.withLock {
            val current = activeSession ?: return
            current.activeCalls.remove(requestId)
            current.takeUnless { it.quickJs.isClosed }
        } ?: return
        runCatching {
            session.quickJs.evaluate<Unit>(
                code = """
                    globalThis.__lxInvokeRequestCallback(
                      $requestId,
                      ${err.toJsonLiteral(json)},
                      ${response.toJsonLiteral(json)},
                      ${body.toJsonLiteral(json)}
                    );
                """.trimIndent(),
                filename = "lx-request-callback.js"
            )
        }.onFailure { error ->
            Log.w(TAG, "LX 请求回调投递失败: requestId=$requestId", error)
        }
    }

    private fun completeRequestInvocation(
        invocationId: Int,
        completion: LxInvocationCompletion
    ) {
        pendingRequestInvocations.remove(invocationId)?.complete(completion)
    }

    private fun cancelPendingRequestInvocations(message: String) {
        pendingRequestInvocations.values.forEach { deferred ->
            deferred.complete(LxInvocationCompletion(error = message))
        }
        pendingRequestInvocations.clear()
    }

    private suspend fun fireTimer(timerId: Int) {
        val session = runtimeMutex.withLock {
            val current = activeSession ?: return
            current.timerJobs.remove(timerId)
            current.takeUnless { it.quickJs.isClosed }
        } ?: return
        runCatching {
            session.quickJs.evaluate<Unit>(
                code = "globalThis.__lxFireTimer($timerId);",
                filename = "lx-fire-timer.js"
            )
        }.onFailure { error ->
            Log.w(TAG, "LX 定时器回调执行失败: timerId=$timerId", error)
        }
    }

    private fun parseDeclaredSources(payload: JsonObject): Map<String, LxDeclaredSource> {
        val sources = payload["sources"] as? JsonObject
            ?: throw IllegalStateException("inited.sources 格式不正确")

        return buildMap {
            sources.forEach { (sourceId, sourceValue) ->
                val sourceObject = sourceValue as? JsonObject
                    ?: throw IllegalStateException("来源 $sourceId 配置格式不正确")
                val knownSource = LxKnownSource.fromId(sourceId)
                    ?: throw IllegalStateException("存在未知来源标识：$sourceId")
                val type = sourceObject["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (type != "music") {
                    throw IllegalStateException("来源 $sourceId 的 type 必须为 music")
                }
                val actions = sourceObject["actions"].stringList()
                val qualities = (sourceObject["qualitys"] ?: sourceObject["qualities"]).stringList()

                if (knownSource == LxKnownSource.LOCAL) {
                    if (actions.any { it !in LOCAL_ALLOWED_ACTIONS }) {
                        throw IllegalStateException("local 来源仅允许 ${LOCAL_ALLOWED_ACTIONS.joinToString("/")}")
                    }
                } else {
                    if (actions != listOf("musicUrl")) {
                        throw IllegalStateException("$sourceId 仅允许 actions=[musicUrl]")
                    }
                    if (qualities.isEmpty() || qualities.any { it !in SUPPORTED_QUALITIES }) {
                        throw IllegalStateException(
                            "$sourceId 仅允许音质 ${SUPPORTED_QUALITIES.joinToString("/")}"
                        )
                    }
                }

                put(
                    sourceId,
                    LxDeclaredSource(
                        id = sourceId,
                        type = type,
                        actions = actions,
                        qualities = qualities
                    )
                )
            }
        }
    }

    private fun sanitizeUpdateAlert(
        metadata: LxScriptMetadata,
        payload: JsonObject?
    ): LxUpdateAlertInfo? {
        val log = payload?.get("log")?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.take(MAX_UPDATE_ALERT_TEXT_LENGTH)
            .orEmpty()
        if (log.isBlank()) return null

        val url = payload?.get("updateUrl")?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.take(MAX_UPDATE_ALERT_TEXT_LENGTH)
            ?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }

        return LxUpdateAlertInfo(
            scriptId = metadata.id,
            scriptName = metadata.name,
            scriptVersion = metadata.version,
            log = log,
            updateUrl = url
        )
    }

    private fun parseMetadata(scriptId: String, rawScript: String): LxScriptMetadata? {
        return parseLxScriptMetadata(scriptId = scriptId, rawScript = rawScript)
    }

    private fun buildPrelude(metadata: LxScriptMetadata): String {
        val currentScriptInfoJson = json.encodeToString(metadata)
        return """
            (() => {
              const __lxSafeClone = (value) => {
                if (value == null) return null;
                if (value instanceof Uint8Array || value instanceof Int8Array) {
                  return { __lxType: "bytes", base64: __lxHostBufferToString(new Int8Array(value), "base64") };
                }
                if (Array.isArray(value)) return value.map(__lxSafeClone);
                if (typeof value === "object") {
                  const out = {};
                  for (const [key, entry] of Object.entries(value)) {
                    out[key] = __lxSafeClone(entry);
                  }
                  return out;
                }
                return value;
              };
              const __lxHandlers = Object.create(null);
              const __lxPendingRequests = new Map();
              const __lxTimers = new Map();
              let __lxRequestSeq = 0;
              const EVENT_NAMES = Object.freeze({
                inited: "inited",
                request: "request",
                updateAlert: "updateAlert",
              });
              globalThis.EVENT_NAMES = EVENT_NAMES;
              globalThis.__lxHandlers = __lxHandlers;
              globalThis.console = {
                log: (...args) => __lxHostConsole("log", args.map(String).join(" ")),
                warn: (...args) => __lxHostConsole("warn", args.map(String).join(" ")),
                error: (...args) => __lxHostConsole("error", args.map(String).join(" ")),
              };
              globalThis.setTimeout = (handler, timeout = 0, ...args) => {
                const id = Date.now() + Math.floor(Math.random() * 100000);
                __lxTimers.set(id, { handler, args });
                __lxHostSetTimeout(id, Number(timeout) || 0);
                return id;
              };
              globalThis.clearTimeout = (id) => {
                __lxTimers.delete(id);
                __lxHostClearTimeout(Number(id) || 0);
              };
              globalThis.__lxFireTimer = (id) => {
                const timer = __lxTimers.get(id);
                if (!timer) return;
                __lxTimers.delete(id);
                if (typeof timer.handler === "function") {
                  return timer.handler(...timer.args);
                }
                return eval(String(timer.handler));
              };
              globalThis.__lxInvokeRequestCallback = (requestId, err, resp, body) => {
                const callback = __lxPendingRequests.get(requestId);
                if (!callback) return;
                __lxPendingRequests.delete(requestId);
                callback(err ?? null, resp ?? null, body ?? null);
              };
              const lx = {
                EVENT_NAMES,
                env: "mobile",
                version: "1.2.0",
                currentScriptInfo: $currentScriptInfoJson,
                on(eventName, handler) {
                  __lxHandlers[String(eventName)] = handler;
                },
                send(eventName, data) {
                  return __lxHostSend(String(eventName), JSON.stringify(__lxSafeClone(data)));
                },
                request(url, options, callback) {
                  const requestId = ++__lxRequestSeq;
                  __lxPendingRequests.set(requestId, typeof callback === "function" ? callback : () => {});
                  __lxHostRequest(String(url ?? ""), JSON.stringify(__lxSafeClone(options ?? {})), requestId);
                  return () => __lxHostCancelRequest(requestId);
                },
                utils: {
                  buffer: {
                    from(value, encoding = "utf8") {
                      return __lxHostBufferFrom(value, String(encoding));
                    },
                    bufToString(buf, encoding = "utf8") {
                      return __lxHostBufferToString(buf, String(encoding));
                    },
                  },
                  crypto: {
                    md5(value) {
                      return __lxHostMd5(value);
                    },
                    randomBytes(size) {
                      return __lxHostRandomBytes(Number(size) || 0);
                    },
                    aesEncrypt(buffer, mode, key, iv) {
                      return __lxHostAesEncrypt(buffer, String(mode ?? ""), key, iv ?? null);
                    },
                    rsaEncrypt(buffer, key) {
                      return __lxHostRsaEncrypt(buffer, String(key ?? ""));
                    },
                  },
                  zlib: Object.freeze({}),
                },
              };
              globalThis.lx = lx;
              globalThis.on = lx.on;
              globalThis.send = lx.send;
              globalThis.request = lx.request;
              globalThis.utils = lx.utils;
              const freezeTargets = [
                Object, Object.prototype, Function, Function.prototype,
                Array, Array.prototype, String, String.prototype,
                Number, Number.prototype, Boolean, Boolean.prototype,
                RegExp, RegExp.prototype, Date, Date.prototype,
                Error, Error.prototype, Promise, Promise.prototype,
                Map, Map.prototype, Set, Set.prototype,
                JSON, Math, Uint8Array, Uint8Array.prototype,
                Int8Array, Int8Array.prototype, ArrayBuffer, ArrayBuffer.prototype,
              ];
              for (const target of freezeTargets) {
                if (!target) continue;
                try {
                  Object.getOwnPropertyNames(target).forEach((key) => {
                    if (
                      (target === Function.prototype && (key === "toString" || key === "toLocaleString")) ||
                      (target === Object.prototype && key === "toString")
                    ) {
                      return;
                    }
                    const desc = Object.getOwnPropertyDescriptor(target, key);
                    if (!desc) return;
                    try {
                      Object.defineProperty(target, key, {
                        ...desc,
                        configurable: false,
                        writable: "writable" in desc ? false : desc.writable,
                      });
                    } catch (_) {}
                  });
                } catch (_) {}
                try { Object.freeze(target); } catch (_) {}
              }
            })();
        """.trimIndent()
    }

    private fun parseRequestOptions(optionsJson: String): ParsedRequestOptions {
        val root = runCatching { json.parseToJsonElement(optionsJson) }.getOrNull() as? JsonObject
            ?: return ParsedRequestOptions()
        val method = root["method"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val headers = root["headers"].jsonObjectOrNull()
            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
            .orEmpty()
        val timeoutSeconds = root["timeout"]?.jsonPrimitive?.intOrNull ?: DEFAULT_TIMEOUT_SECONDS
        val body = root["body"]
        val form = root["form"].jsonObjectOrNull()?.toMap().orEmpty()
        val formData = root["formData"].jsonObjectOrNull()?.toMap().orEmpty()
        return ParsedRequestOptions(
            method = method,
            headers = headers,
            body = body,
            form = form,
            formData = formData,
            timeoutSeconds = timeoutSeconds
        )
    }

    private fun buildRequestBody(options: ParsedRequestOptions) = when {
        options.formData.isNotEmpty() -> {
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .apply {
                    options.formData.forEach { (name, value) ->
                        val bytes = value.toByteArrayFromDescriptorOrNull()
                        if (bytes != null) {
                            addFormDataPart(
                                name,
                                "$name.bin",
                                bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                            )
                        } else {
                            addFormDataPart(name, value.jsonPrimitive.contentOrNull.orEmpty())
                        }
                    }
                }
                .build()
        }
        options.form.isNotEmpty() -> {
            FormBody.Builder()
                .apply {
                    options.form.forEach { (name, value) ->
                        add(name, value.jsonPrimitive.contentOrNull.orEmpty())
                    }
                }
                .build()
        }
        options.body != null -> {
            val bytes = options.body.toByteArrayFromDescriptorOrNull()
            when {
                bytes != null -> bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                options.body is JsonPrimitive && options.body.isString -> {
                    options.body.content.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
                }
                else -> json.encodeToString(options.body).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
        }
        else -> null
    }

    private fun encodeToBytes(value: String, encoding: String): ByteArray = when (encoding.lowercase()) {
        "utf8", "utf-8" -> value.toByteArray(UTF_8)
        "base64" -> Base64.getDecoder().decode(value)
        "hex" -> value.hexToBytes()
        else -> throw IllegalArgumentException("仅支持 base64/hex/utf8 编码")
    }

    private fun decodeFromBytes(bytes: ByteArray, encoding: String): String = when (encoding.lowercase()) {
        "utf8", "utf-8" -> bytes.toString(UTF_8)
        "base64" -> Base64.getEncoder().encodeToString(bytes)
        "hex" -> bytes.toHexString()
        else -> throw IllegalArgumentException("仅支持 base64/hex/utf8 编码")
    }

    private fun encryptAes(
        input: ByteArray,
        key: ByteArray,
        iv: ByteArray?,
        mode: String
    ): ByteArray {
        val normalizedMode = mode.lowercase()
        val transformation = when (normalizedMode) {
            "cbc", "aes-cbc", "aes/cbc/pkcs5padding" -> "AES/CBC/PKCS5Padding"
            "ecb", "aes-ecb", "aes/ecb/pkcs5padding" -> "AES/ECB/PKCS5Padding"
            else -> throw IllegalArgumentException("仅支持 AES/CBC/PKCS5Padding 与 AES/ECB/PKCS5Padding")
        }
        val cipher = Cipher.getInstance(transformation)
        val secretKey = SecretKeySpec(key, "AES")
        if (transformation.contains("/CBC/")) {
            val ivBytes = iv?.takeIf { it.isNotEmpty() } ?: ByteArray(16)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(ivBytes.copyOf(16)))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        }
        return cipher.doFinal(input)
    }

    private fun encryptRsa(input: ByteArray, publicKeyPem: String): ByteArray {
        val normalized = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(normalized)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(input)
    }

    private fun extractHttpUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }

        val parsed = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        return findFirstHttpUrl(parsed)
    }

    private fun findFirstHttpUrl(element: JsonElement?): String? = when (element) {
        is JsonObject -> {
            PLAYABLE_URL_KEYS.firstNotNullOfOrNull { key ->
                element[key]?.jsonPrimitive?.contentOrNull
                    ?.takeIf {
                        it.startsWith("http://", ignoreCase = true) ||
                            it.startsWith("https://", ignoreCase = true)
                    }
            } ?: element.values.firstNotNullOfOrNull(::findFirstHttpUrl)
        }
        is JsonArray -> element.firstNotNullOfOrNull(::findFirstHttpUrl)
        is JsonPrimitive -> element.contentOrNull
            ?.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
        else -> null
    }

    private fun requestError(message: String, name: String = "RequestError"): JsonObject = buildJsonObject {
        put("message", message)
        put("name", name)
    }

    companion object {
        private const val TAG = "LxCustomRuntime"
        private val PLAYABLE_URL_KEYS = listOf("url", "musicUrl", "playUrl", "play_url")
        private val SUPPORTED_QUALITIES = setOf("128k", "320k", "flac", "flac24bit")
        private val LOCAL_ALLOWED_ACTIONS = setOf("musicUrl", "lyric", "pic")
        private const val DEFAULT_TIMEOUT_SECONDS = 15
        private const val MAX_UPDATE_ALERT_TEXT_LENGTH = 1024
        private val secureRandom = SecureRandom()
    }
}

private val HEADER_TAG_REGEX = Regex("""^[*!]?\s*@([A-Za-z][\w-]*)\s*(.*)$""")

internal fun parseLxScriptMetadata(
    scriptId: String,
    rawScript: String
): LxScriptMetadata? {
    val tags = parseLxScriptHeaderTags(rawScript) ?: return null
    val name = tags["name"].orEmpty().trim()
    if (name.isBlank()) return null

    return LxScriptMetadata(
        id = scriptId,
        name = name,
        description = tags["description"].orEmpty().trim(),
        version = tags["version"].orEmpty().trim(),
        author = tags["author"].orEmpty().trim(),
        homepage = tags["homepage"].orEmpty().trim(),
        rawScript = rawScript
    )
}

private fun parseLxScriptHeaderTags(rawScript: String): Map<String, String>? {
    val normalized = rawScript.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    var cursor = 0
    while (cursor + 1 < normalized.length && normalized[cursor] == '/' && normalized[cursor + 1] == '*') {
        val commentEnd = normalized.indexOf("*/", startIndex = cursor + 2)
        if (commentEnd < 0) return null

        val tags = linkedMapOf<String, String>()
        normalized.substring(cursor + 2, commentEnd)
            .lineSequence()
            .forEach { line ->
                val match = HEADER_TAG_REGEX.matchEntire(line.trim()) ?: return@forEach
                val key = match.groupValues[1].trim()
                val value = match.groupValues[2].trim()
                if (key.isNotBlank()) {
                    tags[key] = value
                }
            }
        if (tags["name"].orEmpty().isNotBlank()) {
            return tags
        }

        cursor = commentEnd + 2
        while (cursor < normalized.length && normalized[cursor].isWhitespace()) {
            cursor++
        }
    }
    return null
}

@Singleton
class LxCustomUpdateAlertCoordinator @Inject constructor() {
    private val shownKeys = linkedSetOf<String>()
    private val _alertState = kotlinx.coroutines.flow.MutableStateFlow<LxUpdateAlertInfo?>(null)
    val alertState: kotlinx.coroutines.flow.StateFlow<LxUpdateAlertInfo?> = _alertState

    fun showIfNeeded(alert: LxUpdateAlertInfo) {
        if (!shownKeys.add(alert.dedupeKey)) return
        _alertState.value = alert
    }

    fun dismiss() {
        _alertState.value = null
    }
}

private data class ActiveLxSession(
    val script: LxCustomScript,
    val quickJs: QuickJs,
    val declaredSources: Map<String, LxDeclaredSource>,
    val updateAlert: LxUpdateAlertInfo?,
    val activeCalls: MutableMap<Int, Call> = mutableMapOf(),
    val timerJobs: MutableMap<Int, Job> = mutableMapOf(),
    var didShowUpdateAlert: Boolean = false
) {
    fun close() {
        activeCalls.values.forEach(Call::cancel)
        timerJobs.values.forEach(Job::cancel)
        activeCalls.clear()
        timerJobs.clear()
        quickJs.close()
    }

    fun showUpdateAlertIfNeeded(coordinator: LxCustomUpdateAlertCoordinator) {
        if (didShowUpdateAlert) return
        val alert = updateAlert ?: return
        didShowUpdateAlert = true
        coordinator.showIfNeeded(alert)
    }
}

private data class SessionBuildState(
    val metadata: LxScriptMetadata,
    var initedPayload: JsonObject? = null,
    var updateAlert: LxUpdateAlertInfo? = null
)

@Serializable
private data class LxRuntimeRequest(
    val source: String,
    val action: String,
    val info: LxMusicRequestPayload
)

private data class ParsedRequestOptions(
    val method: String = "",
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
    val form: Map<String, JsonElement> = emptyMap(),
    val formData: Map<String, JsonElement> = emptyMap(),
    val timeoutSeconds: Int = 15
)

private data class LxInvocationCompletion(
    val value: String? = null,
    val error: String? = null
)

private val LxCustomScript.runtimeKey: String
    get() = "$id#$updatedAt"

private fun Array<Any?>.stringArg(index: Int): String = getOrNull(index)?.toString().orEmpty()

private fun Array<Any?>.intArg(index: Int): Int = when (val value = getOrNull(index)) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> value.toInt()
    is Float -> value.toInt()
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: 0
    else -> 0
}

private fun Array<Any?>.byteArrayArg(index: Int): ByteArray = when (val value = getOrNull(index)) {
    is ByteArray -> value
    is String -> value.toByteArray(UTF_8)
    null -> ByteArray(0)
    else -> value.toString().toByteArray(UTF_8)
}

private fun Array<Any?>.byteArrayArgOrNull(index: Int): ByteArray? = when (val value = getOrNull(index)) {
    null -> null
    is ByteArray -> value
    is String -> value.toByteArray(UTF_8)
    else -> value.toString().toByteArray(UTF_8)
}

private fun JsonElement?.stringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it.isNotBlank() }
    else -> emptyList()
}

private fun JsonElement?.jsonObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonObject?.toJsonLiteral(json: Json): String = this
    ?.let(json::encodeToString)
    ?: "null"

private fun String?.toJsonLiteral(json: Json): String = this
    ?.let(json::encodeToString)
    ?: "null"

private fun String.toJsonObjectOrNull(json: Json): JsonObject? =
    runCatching { json.parseToJsonElement(this) }.getOrNull() as? JsonObject

internal fun parseLxResponseBody(body: String, json: Json): JsonElement =
    runCatching { json.parseToJsonElement(body) }.getOrElse { JsonPrimitive(body) }

private fun JsonElement.toByteArrayFromDescriptorOrNull(): ByteArray? {
    val obj = this as? JsonObject ?: return null
    if (obj["__lxType"]?.jsonPrimitive?.contentOrNull != "bytes") return null
    val base64 = obj["base64"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (base64.isBlank()) return ByteArray(0)
    return Base64.getDecoder().decode(base64)
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    val cleaned = trim().removePrefix("0x")
    require(cleaned.length % 2 == 0) { "十六进制字符串长度必须为偶数" }
    return ByteArray(cleaned.length / 2) { index ->
        cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}
