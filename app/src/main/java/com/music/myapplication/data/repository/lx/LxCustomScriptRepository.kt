package com.music.myapplication.data.repository.lx

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.LxScriptCatalogStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LxCustomScriptRepository @Inject constructor(
    private val store: LxScriptCatalogStore,
    private val runtime: LxCustomScriptRuntime
) {
    val catalog: Flow<LxScriptCatalog> = store.catalog

    val summary: Flow<LxScriptCatalogSummary> = catalog.map { it.toSummary() }

    suspend fun getCatalog(): LxScriptCatalog = store.read()

    suspend fun getActiveValidatedScript(): LxCustomScript? = store.read().activeValidatedScript

    suspend fun saveScript(
        rawScript: String,
        existingScriptId: String? = null
    ): Result<LxCustomScript> {
        val now = System.currentTimeMillis()
        val catalog = store.read()
        val current = existingScriptId?.let { targetId ->
            catalog.scripts.firstOrNull { it.id == targetId }
        }
        val scriptId = current?.id ?: buildScriptId(now)
        val validation = runtime.validate(scriptId = scriptId, rawScript = rawScript)
        val updated = LxCustomScript(
            id = scriptId,
            rawScript = rawScript,
            name = validation.metadata.name.ifBlank { current?.name.orEmpty() },
            description = validation.metadata.description.ifBlank { current?.description.orEmpty() },
            version = validation.metadata.version.ifBlank { current?.version.orEmpty() },
            author = validation.metadata.author.ifBlank { current?.author.orEmpty() },
            homepage = validation.metadata.homepage.ifBlank { current?.homepage.orEmpty() },
            declaredSources = validation.declaredSources.map { it.id },
            lastValidatedAt = now,
            lastValidationError = validation.validationError,
            updatedAt = now,
            updateAlertLog = validation.updateAlert?.log,
            updateAlertUrl = validation.updateAlert?.updateUrl
        )

        store.update { currentCatalog ->
            val nextScripts = currentCatalog.scripts
                .filterNot { it.id == scriptId } +
                updated
            val nextActiveId = when {
                currentCatalog.activeScriptId != scriptId -> currentCatalog.activeScriptId
                updated.isValidationPassed -> scriptId
                else -> null
            }
            currentCatalog.copy(
                scripts = nextScripts.sortedByDescending { it.updatedAt },
                activeScriptId = nextActiveId
            )
        }
        runtime.invalidate(scriptId)

        return Result.Success(updated)
    }

    suspend fun activateScript(scriptId: String): Result<LxCustomScript> {
        val catalog = store.read()
        val script = catalog.scripts.firstOrNull { it.id == scriptId }
            ?: return Result.Error(AppError.Parse(message = "脚本不存在"))
        if (!script.isValidationPassed) {
            return Result.Error(AppError.Parse(message = "该脚本最近一次校验未通过，不能设为当前"))
        }
        if (script.declaredSources.none { sourceId ->
                LxKnownSource.fromId(sourceId)?.mappedPlatform in setOf(
                    com.music.myapplication.domain.model.Platform.NETEASE,
                    com.music.myapplication.domain.model.Platform.QQ,
                    com.music.myapplication.domain.model.Platform.KUWO
                )
            }
        ) {
            return Result.Error(AppError.Parse(message = "该脚本未声明网易云 / QQ / 酷我的 musicUrl 来源"))
        }

        return runCatching {
            runtime.invalidate()
            runtime.warmUp(script)
            store.update { it.copy(activeScriptId = script.id) }
            script
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = {
                Result.Error(AppError.Parse(message = it.message?.ifBlank { "脚本初始化失败" } ?: "脚本初始化失败", cause = it))
            }
        )
    }

    suspend fun deleteScript(scriptId: String) {
        val catalog = store.read()
        val shouldClearActive = catalog.activeScriptId == scriptId
        store.update {
            it.copy(
                scripts = it.scripts.filterNot { script -> script.id == scriptId },
                activeScriptId = if (shouldClearActive) null else it.activeScriptId
            )
        }
        runtime.invalidate(scriptId)
    }

    fun dismissUpdateAlert() {
        runtime.dismissUpdateAlert()
    }

    companion object {
        private fun buildScriptId(timestampMs: Long): String = "lx_${timestampMs}"
    }
}
