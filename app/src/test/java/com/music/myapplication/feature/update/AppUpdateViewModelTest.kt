package com.music.myapplication.feature.update

import com.music.myapplication.BuildConfig
import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.datastore.PlayerPreferences
import com.music.myapplication.domain.model.AppUpdateInfo
import com.music.myapplication.domain.repository.AppUpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    @Test
    fun repositoryReturnsNull_skipsPromptAndDoesNotRecordCheckTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            coEvery { repository.fetchLatest() } returns Result.Success(null)
            every { downloadCoordinator.observeDownloadState() } returns flowOf(AppUpdateDownloadState())

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.availableUpdate)
            coVerify(exactly = 0) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun optionalUpdate_showsDialogAndCanSkip() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val update = testUpdate(isForceUpdate = false, minSupportedVersionCode = 0)

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns flowOf(AppUpdateDownloadState())

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDialog)
            assertTrue(viewModel.state.value.canSkipUpdate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun forceUpdate_disablesSkip() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val update = testUpdate(isForceUpdate = true)

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns flowOf(AppUpdateDownloadState())

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDialog)
            assertFalse(viewModel.state.value.canSkipUpdate)
            viewModel.dismissCurrentUpdate()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.showDialog)
            coVerify(exactly = 0) { preferences.setAppUpdateLastNotifiedVersionCode(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun minSupportedVersionHigherThanCurrent_disablesSkip() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val update = testUpdate(
                isForceUpdate = false,
                minSupportedVersionCode = BuildConfig.VERSION_CODE + 1
            )

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns flowOf(AppUpdateDownloadState())

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDialog)
            assertFalse(viewModel.state.value.canSkipUpdate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun primaryAction_enqueuesDownloadAndUpdatesState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val downloadStateFlow = MutableStateFlow(AppUpdateDownloadState())
            val update = testUpdate()

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns downloadStateFlow
            every { downloadCoordinator.enqueue(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            viewModel.onPrimaryAction()
            advanceUntilIdle()

            assertEquals(AppUpdateActionState.DOWNLOADING, viewModel.state.value.actionState)
            verify(exactly = 1) { downloadCoordinator.enqueue(update) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun downloadSuccess_butVerifyFails_setsVerifyFailed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val downloadStateFlow = MutableStateFlow(AppUpdateDownloadState())
            val update = testUpdate()

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns downloadStateFlow
            every { downloadCoordinator.enqueue(any()) } returns Unit
            coEvery { installer.verifySha256(any(), any()) } returns false

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            viewModel.onPrimaryAction()
            advanceUntilIdle()

            downloadStateFlow.value = AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.SUCCEEDED,
                localFilePath = "D:/tmp/update.apk"
            )
            advanceUntilIdle()

            assertEquals(AppUpdateActionState.VERIFY_FAILED, viewModel.state.value.actionState)
            assertTrue(viewModel.state.value.stageMessage?.contains("校验失败") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun downloadSuccess_andInstallerLaunched_setsInstalling() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            val downloadStateFlow = MutableStateFlow(AppUpdateDownloadState())
            val update = testUpdate()

            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            every { downloadCoordinator.observeDownloadState() } returns downloadStateFlow
            every { downloadCoordinator.enqueue(any()) } returns Unit
            coEvery { installer.verifySha256(any(), any()) } returns true
            every { installer.launchInstall(any()) } returns AppUpdateInstallResult.LaunchedInstaller

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            viewModel.onPrimaryAction()
            advanceUntilIdle()

            downloadStateFlow.value = AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.SUCCEEDED,
                localFilePath = "D:/tmp/update.apk"
            )
            advanceUntilIdle()

            assertEquals(AppUpdateActionState.INSTALLING, viewModel.state.value.actionState)
            assertTrue(viewModel.state.value.stageMessage?.contains("安装器") == true)
            verify(exactly = 1) { installer.launchInstall("D:/tmp/update.apk") }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repositoryError_doesNotRecordCheckTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockPreferences()
            val repository = mockk<AppUpdateRepository>()
            val downloadCoordinator = mockk<AppUpdateDownloadCoordinator>()
            val installer = mockk<AppUpdateInstaller>()

            coEvery { repository.fetchLatest() } returns Result.Error(AppError.Network(message = "boom"))
            every { downloadCoordinator.observeDownloadState() } returns flowOf(AppUpdateDownloadState())

            val viewModel = AppUpdateViewModel(preferences, repository, downloadCoordinator, installer)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            coVerify(exactly = 0) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun mockPreferences(): PlayerPreferences {
        val preferences = mockk<PlayerPreferences>()
        every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
        every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(0)
        coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
        coEvery { preferences.setAppUpdateLastNotifiedVersionCode(any()) } returns Unit
        return preferences
    }

    private fun testUpdate(
        isForceUpdate: Boolean = false,
        minSupportedVersionCode: Int = 0
    ): AppUpdateInfo {
        return AppUpdateInfo(
            latestVersionCode = BuildConfig.VERSION_CODE + 1,
            latestVersionName = "1.4.0",
            apkUrl = "https://example.com/app.apk",
            sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            fileSizeBytes = 10_240L,
            changelog = "- 修复：xxx\n- 新增：yyy",
            isForceUpdate = isForceUpdate,
            minSupportedVersionCode = minSupportedVersionCode,
            publishedAt = "2026-03-18T00:00:00Z"
        )
    }
}