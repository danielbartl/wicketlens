package dev.wicketlens.routes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.util.PsiModificationTracker
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

data class WicketRoutesState(val message: String = "Refresh to discover mounted URLs.", val running: Boolean = false,
    val snapshot: WicketRoutesSnapshot? = null, val stale: Boolean = false)

@Service(Service.Level.PROJECT)
class WicketRoutesService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    @Volatile var state = WicketRoutesState()
        private set
    private val listeners = CopyOnWriteArrayList<(WicketRoutesState) -> Unit>()
    private var job: Job? = null
    private var navigation: Job? = null
    private var generation = 0L
    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) { changed() }
        }, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { changed() })
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) { changed() }
        })
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) { if (events.any { !it.isFromSave }) changed() }
        })
    }

    fun refresh(moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main") {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val request = ++generation
        job?.cancel(); navigation?.cancel()
        publish(WicketRoutesState("Discovering mounted URLs; waiting for indexing and committed documents…", running = true))
        job = scope.launch {
            try {
                val snapshot = WicketRoutesRequest().prepare(project, moduleName, applicationClass, sourceSet)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && generation == request) {
                        job = null
                        val mounts = snapshot.routes.applications.sumOf { it.mounts.size }
                        publish(WicketRoutesState("$mounts mount declarations in ${snapshot.routes.applications.size} applications — static snapshot; refresh after edits.", snapshot = snapshot))
                        current()
                    }
                }
            } catch (cancelled: CancellationException) { cancelled(request); throw cancelled
            } catch (_: ProcessCanceledException) { cancelled(request)
            } catch (failure: Exception) {
                if (failure !is PageAnalysisFailure) logger<WicketRoutesService>().warn("Mounted URL discovery failed", failure)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && generation == request) {
                        job = null
                        publish(WicketRoutesState((failure as? PageAnalysisFailure)?.message ?: "Route discovery failed. See the IDE log."))
                    }
                }
            }
        }
    }

    fun cancel() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        ++generation; job?.cancel(); navigation?.cancel(); job = null
        publish(state.copy(message = "Mounted URL discovery cancelled. Refresh to try again.", running = false))
    }

    fun current(): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val snapshot = state.snapshot ?: return false
        if (state.running || state.stale || project.isDisposed) return false
        try { WicketRoutesRequest().requireCurrent(project, snapshot) }
        catch (_: PageAnalysisFailure) {
            navigation?.cancel()
            publish(state.copy(message = "Mounted URLs are out of date. Refresh before navigating.", stale = true))
            return false
        }
        return true
    }

    fun navigate(location: SourceLocation): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!current()) return false
        val snapshot = state.snapshot!!
        val locations = snapshot.routes.applications.flatMap { app -> listOf(app.source) + app.mounts.flatMap {
            listOfNotNull(it.registration, it.pageSource, it.markupSource)
        } }
        if (location !in locations) return false
        val source = snapshot.version.sources.singleOrNull { it.path == location.file } ?: return false
        navigation?.cancel()
        navigation = scope.launch {
            val length = constrainedReadAction {
                if (state.snapshot !== snapshot) return@constrainedReadAction null
                try { WicketRoutesRequest().requireCurrent(project, snapshot) }
                catch (_: PageAnalysisFailure) { return@constrainedReadAction null }
                val file = VirtualFileManager.getInstance().findFileByUrl(source.url) ?: return@constrainedReadAction null
                FileDocumentManager.getInstance().getDocument(file)?.textLength
            } ?: return@launch
            withContext(Dispatchers.EDT) {
                if (state.snapshot !== snapshot || !current() || location.offset !in 0..length) return@withContext
                val file = VirtualFileManager.getInstance().findFileByUrl(source.url) ?: return@withContext
                object : OpenFileDescriptor(project, file, location.offset) {
                    private fun valid() = state.snapshot === snapshot && !state.stale &&
                        snapshot.version.isCurrent(project, 0) && VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount == snapshot.structureStamp
                    override fun getOffset(): Int = if (valid()) super.getOffset() else -1
                    override fun canNavigate(): Boolean = valid() && super.canNavigate()
                }.navigate(true)
                current()
            }
        }
        return true
    }

    fun analyze(mount: WicketMount): Boolean {
        if (!current() || !mount.analyzablePage || state.snapshot!!.routes.applications.none { mount in it.mounts }) return false
        val location = mount.pageSource ?: return false
        val source = state.snapshot!!.version.sources.singleOrNull { it.path == location.file } ?: return false
        project.service<WicketLensService>().analyze(source.url, location.offset)
        return true
    }

    fun listen(parent: Disposable, listener: (WicketRoutesState) -> Unit) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        listeners += listener; Disposer.register(parent) { listeners -= listener }; listener(state)
    }
    private fun publish(value: WicketRoutesState) { state = value; listeners.forEach { it(value) } }
    private fun changed() {
        if (project.isDisposed || state.snapshot == null || state.stale) return
        if (ApplicationManager.getApplication().isDispatchThread) current()
        else scope.launch(Dispatchers.EDT) { if (!project.isDisposed) current() }
    }
    private fun cancelled(request: Long) {
        if (!project.isDisposed) scope.launch(Dispatchers.EDT) {
            if (!project.isDisposed && request == generation && state.running) { job = null; publish(WicketRoutesState("Discovery cancelled. Refresh to try again.")) }
        }
    }
    override fun dispose() { listeners.clear() }
}
