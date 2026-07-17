package info.plateaukao.einkbro.task

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.WebViewEngine
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.viewmodel.TtsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates execution of a [BrowserTask]. Owns the lifecycle of the off-screen
 * [BrowserTools], exposes a [progress] StateFlow for the UI to render, and supports
 * cancellation.
 *
 * Android scoped this per-Activity via lifecycleScope; here the host passes a
 * [CoroutineScope] (or accepts the default Main-dispatcher scope) plus the shared
 * [TtsViewModel] and a resolver for the active tab's engine. Hold one instance
 * next to the browser screen, like `aiChatDelegate` on Android.
 */
class TaskRunner(
    private val ttsViewModel: TtsViewModel,
    private val activeEngineProvider: () -> WebViewEngine? = { null },
    private val config: ConfigManager = AppServices.config,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _progress = MutableStateFlow<TaskProgress?>(null)
    val progress: StateFlow<TaskProgress?> = _progress.asStateFlow()

    private var currentJob: Job? = null
    private var currentTools: BrowserTools? = null

    /**
     * Starts [task], cancelling any task already running. [initialSnapshot] carries
     * the originating page for agent-style tasks (null for templates that only use
     * the active tab).
     */
    fun run(task: BrowserTask, initialSnapshot: InitialPageSnapshot? = null): Job {
        cancel()
        val openAi = OpenAiRepository()

        val tools = BrowserToolsImpl(
            config = config,
            openAiRepository = openAi,
            ttsViewModel = ttsViewModel,
            progressSink = { line -> appendStep(line) },
            finishSink = { markdown -> markFinished(markdown) },
            initialSnapshot = initialSnapshot,
            activeEngineProvider = activeEngineProvider,
        )
        currentTools = tools

        _progress.value = TaskProgress(
            taskName = task.displayName,
            status = TaskProgress.Status.Running,
            steps = emptyList(),
            finalMarkdown = null,
        )

        val job = scope.launch {
            try {
                task.run(tools)
                val current = _progress.value
                if (current?.status == TaskProgress.Status.Running) {
                    // Task exited without calling finish()
                    _progress.value = current.copy(
                        status = TaskProgress.Status.Done,
                        finalMarkdown = current.finalMarkdown
                            ?: "(task ended without producing a result)",
                    )
                }
            } catch (e: CancellationException) {
                _progress.value = _progress.value?.copy(status = TaskProgress.Status.Cancelled)
                throw e
            } catch (e: Exception) {
                println("TaskRunner: task failed: ${e.message}")
                _progress.value = _progress.value?.copy(
                    status = TaskProgress.Status.Failed,
                    finalMarkdown = "Task failed: ${e.message}",
                )
            } finally {
                tools.dispose()
                if (currentTools === tools) currentTools = null
            }
        }
        currentJob = job
        return job
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        currentTools?.dispose()
        currentTools = null
    }

    /** Clears the last task's progress from the UI (after the result was consumed). */
    fun clearProgress() {
        if (_progress.value?.status != TaskProgress.Status.Running) {
            _progress.value = null
        }
    }

    private fun appendStep(line: TaskProgress.StepLine) {
        val current = _progress.value ?: return
        _progress.value = current.copy(steps = current.steps + line)
    }

    private fun markFinished(markdown: String) {
        val current = _progress.value ?: return
        _progress.value = current.copy(
            status = TaskProgress.Status.Done,
            finalMarkdown = markdown,
        )
    }
}
