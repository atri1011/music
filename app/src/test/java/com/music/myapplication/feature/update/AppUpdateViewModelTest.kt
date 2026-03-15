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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
            every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(0)
            coEvery { repository.fetchLatest() } returns Result.Success(null)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.availableUpdate)
            coVerify(exactly = 0) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lastCheckWithin24Hours_skipsRepositoryFetch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(System.currentTimeMillis())
            every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(0)
            coEvery { repository.fetchLatest() } returns Result.Error(AppError.Network(message = "should not call"))

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            coVerify(exactly = 0) { repository.fetchLatest() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun updateAvailable_showsDialogAndRecordsCheckTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            val update = AppUpdateInfo(
                latestVersionCode = BuildConfig.VERSION_CODE + 1,
                latestVersionName = "1.2.0",
                downloadUrl = "https://example.com/app.apk",
                changelog = "- 修复：xxx\n- 新增：yyy"
            )

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
            every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(0)
            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDialog)
            assertEquals(update, viewModel.state.value.availableUpdate)
            coVerify(exactly = 1) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dismiss_recordsLastNotifiedAndHidesDialog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            val update = AppUpdateInfo(
                latestVersionCode = BuildConfig.VERSION_CODE + 1,
                latestVersionName = "1.2.0",
                downloadUrl = "https://example.com/app.apk"
            )

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
            every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(0)
            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit
            coEvery { preferences.setAppUpdateLastNotifiedVersionCode(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showDialog)

            viewModel.dismiss(update.latestVersionCode)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.availableUpdate)
            coVerify(exactly = 1) { preferences.setAppUpdateLastNotifiedVersionCode(update.latestVersionCode) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repositoryError_doesNotRecordCheckTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
            coEvery { repository.fetchLatest() } returns Result.Error(AppError.Network(message = "boom"))
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            coVerify(exactly = 0) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun alreadyNotifiedSameVersion_doesNotShowDialog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = mockk<PlayerPreferences>()
            val repository = mockk<AppUpdateRepository>()

            val latest = BuildConfig.VERSION_CODE + 1
            val update = AppUpdateInfo(
                latestVersionCode = latest,
                latestVersionName = "1.2.0",
                downloadUrl = "https://example.com/app.apk"
            )

            every { preferences.appUpdateLastCheckAtMs } returns flowOf(0L)
            every { preferences.appUpdateLastNotifiedVersionCode } returns flowOf(latest)
            coEvery { repository.fetchLatest() } returns Result.Success(update)
            coEvery { preferences.setAppUpdateLastCheckAtMs(any()) } returns Unit

            val viewModel = AppUpdateViewModel(preferences, repository)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.showDialog)
            assertNull(viewModel.state.value.availableUpdate)
            coVerify(exactly = 1) { preferences.setAppUpdateLastCheckAtMs(any()) }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

