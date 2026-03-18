package com.music.myapplication.data.repository

import com.music.myapplication.BuildConfig
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.remote.dto.AppUpdateManifestDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AppUpdateRepositoryImplTest {

    private val api = mockk<TuneHubApi>()
    private val repository = AppUpdateRepositoryImpl(api)

    @Test
    fun parseNewManifest_success() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } returns Json.parseToJsonElement("""{"version":"main"}""")
        coEvery { api.fetchAppUpdateManifest(any()) } returns validManifest()

        val result = repository.fetchLatest()

        assertTrue(result is Result.Success)
        val update = (result as Result.Success).data
        assertNotNull(update)
        assertEquals(14, update!!.latestVersionCode)
        assertEquals("1.4.0", update.latestVersionName)
        assertEquals("https://example.com/app-release.apk", update.apkUrl)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", update.sha256)
        assertEquals(10_240L, update.fileSizeBytes)
        assertEquals(false, update.isForceUpdate)
        assertEquals(0, update.minSupportedVersionCode)
    }

    @Test
    fun fallbackToLegacyDownloadUrl_success() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } returns Json.parseToJsonElement("""{"version":"main"}""")
        coEvery { api.fetchAppUpdateManifest(any()) } returns validManifest(
            apkUrl = null,
            downloadUrl = "https://legacy.example.com/app-release.apk"
        )

        val result = repository.fetchLatest()

        assertTrue(result is Result.Success)
        val update = (result as Result.Success).data
        assertNotNull(update)
        assertEquals("https://legacy.example.com/app-release.apk", update!!.apkUrl)
    }

    @Test
    fun invalidSha256_returnsParseError() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } returns Json.parseToJsonElement("""{"version":"main"}""")
        coEvery { api.fetchAppUpdateManifest(any()) } returns validManifest(
            sha256 = "sha256-invalid"
        )

        val result = repository.fetchLatest()

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Parse)
        assertTrue(error.message.contains("sha256"))
    }

    @Test
    fun invalidSha256FromVersionedSource_fallbackToOtherSource_success() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } returns Json.parseToJsonElement("""{"version":"v1.5.0"}""")
        coEvery { api.fetchAppUpdateManifest(match { it.contains("@") }) } returns validManifest(
            sha256 = "sha256-invalid"
        )
        coEvery { api.fetchAppUpdateManifest(match { !it.contains("@") }) } returns validManifest()

        val result = repository.fetchLatest()

        assertTrue(result is Result.Success)
        val update = (result as Result.Success).data
        assertNotNull(update)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", update!!.sha256)
    }

    @Test
    fun resolveLatestFails_usesUnpinnedJsdelivrFallbackFirst() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } throws IllegalStateException("resolve failed")
        val expectedUrl = "https://cdn.jsdelivr.net/gh/${BuildConfig.APP_UPDATE_REPO}/update.json"
        coEvery { api.fetchAppUpdateManifest(expectedUrl) } returns validManifest()
        coEvery { api.fetchAppUpdateManifest(match { it != expectedUrl }) } throws IllegalStateException("unexpected source")

        val result = repository.fetchLatest()

        assertTrue(result is Result.Success)
    }

    @Test
    fun invalidVersionCode_returnsParseError() = runTest {
        assumeUpdateRepoConfigured()
        coEvery { api.fetchJsonElement(any()) } returns Json.parseToJsonElement("""{"version":"main"}""")
        coEvery { api.fetchAppUpdateManifest(any()) } returns validManifest(
            latestVersionCode = 0
        )

        val result = repository.fetchLatest()

        assertTrue(result is Result.Error)
        val error = (result as Result.Error).error
        assertTrue(error is AppError.Parse)
        assertTrue(error.message.contains("versionCode"))
    }

    private fun assumeUpdateRepoConfigured() {
        assumeTrue(BuildConfig.APP_UPDATE_REPO.isNotBlank())
    }

    private fun validManifest(
        latestVersionCode: Int = 14,
        latestVersionName: String = "1.4.0",
        apkUrl: String? = "https://example.com/app-release.apk",
        downloadUrl: String? = null,
        sha256: String = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        fileSizeBytes: Long = 10_240L,
        changelog: String = "- 自动更新升级",
        isForceUpdate: Boolean = false,
        minSupportedVersionCode: Int = 0,
        publishedAt: String = "2026-03-18T00:00:00Z"
    ): AppUpdateManifestDto {
        return AppUpdateManifestDto(
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName,
            apkUrl = apkUrl,
            downloadUrl = downloadUrl,
            sha256 = sha256,
            fileSizeBytes = fileSizeBytes,
            changelog = changelog,
            isForceUpdate = isForceUpdate,
            minSupportedVersionCode = minSupportedVersionCode,
            publishedAt = publishedAt
        )
    }
}
