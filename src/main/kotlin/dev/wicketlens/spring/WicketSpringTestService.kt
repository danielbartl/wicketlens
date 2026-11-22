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
import dev.wicketlens.analysis.WicketSourceFiles
import dev.wicketlens.routes.WicketMountRow
import dev.wicketlens.testing.WicketMountedTestSelection
import dev.wicketlens.testing.WicketMountedTests
import dev.wicketlens.testing.WicketMountedResource
import dev.wicketlens.testing.WicketTestGenerator
import kotlinx.coroutines.*

@Service(Service.Level.PROJECT)
class WicketSpringTestService(private val project: Project, private val scope: CoroutineScope) {
    private var active: Job? = null

    fun generate(sourceUrl: String, offset: Int) = start("Generate Wicket Spring Test") {
        WicketSpringTestGenerator().prepare(project, sourceUrl, offset)
    }

    fun generateGeneral(sourceUrl: String, offset: Int) = start("Generate Wicket Test") {
        WicketTestGenerator().prepare(project, sourceUrl, offset)
    }

    fun generateMounted(row: WicketMountRow) {
        val path = row.mount.path ?: return
        if (row.mount.nonPageTarget == "Resource") {
            start("Generate Wicket Mounted Resource Test") {
                val app = row.application
                WicketMountedTests.prepareResource(project, app.className, path, app.moduleName, app.sourceSet, WicketMountedResource()).test
            }
            return
        }
        val page = row.mount.pageSource ?: return
        start("Generate Wicket Mounted Page Test") {
            val file = WicketSourceFiles.find(project, page.file) ?: throw AnalysisUnavailable("The mounted page source is unavailable.")
            WicketTestGenerator().prepare(project, file.url, page.offset, WicketMountedTestSelection(path, row.application.className))
        }
    }

    private fun start(title: String, prepare: () -> PreparedSpringTest) {
        active?.cancel()
        active = scope.launch {
            try {
                val prepared = withBackgroundProgress(project, title, cancellable = true) {
                    constrainedReadAction(ReadConstraint.inSmartMode(project), ReadConstraint.withDocumentsCommitted(project)) {
                        prepare()
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
