package com.music.myapplication.feature.more

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.data.repository.lx.LxCustomScript
import com.music.myapplication.data.repository.lx.LxImportedScriptContent
import com.music.myapplication.data.repository.lx.LxCustomScriptRepository
import com.music.myapplication.data.repository.lx.LxScriptCatalog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LxSourcesViewModelTest {

    @Test
    fun loadImportedScript_opensEditorWithImportedText() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.loadImportedScript(
                rawScript = "console.log('lx');",
                sourceLabel = "demo.js"
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isEditing)
            assertEquals("console.log('lx');", viewModel.state.value.editingScriptText)
            assertEquals("已载入 demo.js，确认后保存并校验", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadImportedScript_whileEditingExisting_preservesScriptId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }
            val existingScript = LxCustomScript(
                id = "script-1",
                rawScript = "old-script"
            )

            viewModel.startEdit(existingScript)
            advanceUntilIdle()
            viewModel.loadImportedScript(
                rawScript = "new-script",
                sourceLabel = "updated.js"
            )
            advanceUntilIdle()

            assertEquals("script-1", viewModel.state.value.editingScriptId)
            assertEquals("new-script", viewModel.state.value.editingScriptText)
            assertEquals("已载入 updated.js，保存后会覆盖当前脚本", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadImportedScript_rejectsBlankFile() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.loadImportedScript(
                rawScript = "\uFEFF",
                sourceLabel = "empty.js"
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isEditing)
            assertEquals("导入失败：所选文件内容为空", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importScriptFromUrl_success_opensEditorAndClosesDialog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())
            coEvery { repository.importScriptFromUrl("https://example.com/demo.js") } returns Result.Success(
                LxImportedScriptContent(
                    rawScript = "console.log('remote');",
                    sourceLabel = "demo.js"
                )
            )

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.showImportUrlDialog()
            viewModel.updateImportUrl("https://example.com/demo.js")
            viewModel.importScriptFromUrl()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isImportUrlDialogVisible)
            assertTrue(viewModel.state.value.isEditing)
            assertEquals("console.log('remote');", viewModel.state.value.editingScriptText)
            assertEquals("已载入 demo.js，确认后保存并校验", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importScriptFromUrl_whileEditingExisting_preservesScriptId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())
            coEvery { repository.importScriptFromUrl("https://example.com/updated.js") } returns Result.Success(
                LxImportedScriptContent(
                    rawScript = "new-script",
                    sourceLabel = "updated.js"
                )
            )

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }
            val existingScript = LxCustomScript(
                id = "script-1",
                rawScript = "old-script"
            )

            viewModel.startEdit(existingScript)
            advanceUntilIdle()
            viewModel.showImportUrlDialog()
            viewModel.updateImportUrl("https://example.com/updated.js")
            viewModel.importScriptFromUrl()
            advanceUntilIdle()

            assertEquals("script-1", viewModel.state.value.editingScriptId)
            assertEquals("new-script", viewModel.state.value.editingScriptText)
            assertEquals("已载入 updated.js，保存后会覆盖当前脚本", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun importScriptFromUrl_error_keepsDialogOpenAndShowsError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())
            coEvery { repository.importScriptFromUrl("http://example.com/demo.js") } returns Result.Error(
                AppError.Parse(message = "请输入有效的 https 脚本链接")
            )

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.showImportUrlDialog()
            viewModel.updateImportUrl("http://example.com/demo.js")
            viewModel.importScriptFromUrl()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isImportUrlDialogVisible)
            assertEquals("http://example.com/demo.js", viewModel.state.value.importUrlText)
            assertEquals("请输入有效的 https 脚本链接", viewModel.state.value.importUrlError)
            assertFalse(viewModel.state.value.isImportingUrl)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun activateScript_repositoryThrows_clearsBusyState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = mockk<LxCustomScriptRepository>()
            every { repository.catalog } returns MutableStateFlow(LxScriptCatalog())
            coEvery { repository.activateScript("script-1") } throws IllegalStateException("初始化卡住了")

            val viewModel = LxSourcesViewModel(repository)
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.activateScript("script-1")
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isProcessing)
            assertEquals("初始化卡住了", viewModel.state.value.statusMessage)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
