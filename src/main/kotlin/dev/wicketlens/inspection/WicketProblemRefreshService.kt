package dev.wicketlens.inspection

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.*

/** A paired-file change must refresh the other editor too, without opening the sidebar. */
@Service(Service.Level.PROJECT)
class WicketProblemRefreshService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private var pending: Job? = null

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { refresh() })
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) = refresh()
        })
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { !it.isFromSave && (it.file?.isDirectory == true || it.path.endsWith(".java") || it.path.endsWith(".html")) }) refresh()
            }
        })
    }

    @Synchronized private fun refresh() {
        pending?.cancel()
        pending = scope.launch {
            delay(150)
            constrainedReadAction(ReadConstraint.inSmartMode(project), ReadConstraint.withDocumentsCommitted(project)) { Unit }
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) ApplicationManager.getApplication().runReadAction {
                    FileEditorManager.getInstance(project).openFiles.forEach { virtualFile ->
                        val file = PsiManager.getInstance(project).findFile(virtualFile)
                        if (file != null && WicketIdProblems.supports(file)) DaemonCodeAnalyzer.getInstance(project).restart(file, this@WicketProblemRefreshService)
                    }
                }
            }
        }
    }

    override fun dispose() { pending?.cancel() }
}
