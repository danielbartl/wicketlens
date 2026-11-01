package dev.wicketlens.spring

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import dev.wicketlens.analysis.AnalysisUnavailable
import kotlinx.coroutines.*

@Service(Service.Level.PROJECT)
class WicketSpringTestService(private val project: Project, private val scope: CoroutineScope) {
    private var active: Job? = null

    fun generate(sourceUrl: String, offset: Int) = start(sourceUrl, offset, true)
    fun generateGeneral(sourceUrl: String, offset: Int) = start(sourceUrl, offset, false)

    private fun start(sourceUrl: String, offset: Int, springOnly: Boolean) {
        val title = if (springOnly) "Generate Wicket Spring Test" else "Generate Wicket Test"
        active?.cancel()
        active = scope.launch {
            try {
                val prepared = withBackgroundProgress(project, title, cancellable = true) {
                    constrainedReadAction(ReadConstraint.inSmartMode(project), ReadConstraint.withDocumentsCommitted(project)) {
                        if (springOnly) WicketSpringTestGenerator().prepare(project, sourceUrl, offset)
                        else dev.wicketlens.testing.WicketTestGenerator().prepare(project, sourceUrl, offset)
                    }
                }
                withContext(Dispatchers.EDT) {
                    ensureActive()
                    if (!project.isDisposed) {
                        WicketSpringTestFiles.requireCurrent(project, prepared)
                        WicketSpringTestPreview(project, prepared).show()
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: ProcessCanceledException) {
                // Native progress cancellation leaves project sources untouched.
            } catch (unavailable: AnalysisUnavailable) {
                withContext(Dispatchers.EDT) { if (!project.isDisposed) Messages.showInfoMessage(project, unavailable.message, title) }
            } catch (failure: Exception) {
                logger<WicketSpringTestService>().warn("Wicket Spring test generation failed", failure)
                withContext(Dispatchers.EDT) { if (!project.isDisposed) Messages.showErrorDialog(project,
                    "Could not prepare the test. See the IDE log for details.", title) }
            }
        }
    }
}
