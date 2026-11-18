package dev.wicketlens.routes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
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
import com.intellij.util.concurrency.AppExecutorUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class WicketRoutesState(val message: String = "Refresh to discover mounted URLs.", val running: Boolean = false,
    val snapshot: WicketRoutesSnapshot? = null, val stale: Boolean = false)

@Service(Service.Level.PROJECT)
class WicketRoutesService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    @Volatile var state = WicketRoutesState()
        private set
    private val listeners = CopyOnWriteArrayList<(WicketRoutesState) -> Unit>()
    private var job: Job? = null
    private var navigation: CancellablePromise<*>? = null
    private var generation = 0L
    private val changes = AtomicLong()
    private val invalidations = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var verifiedChanges = -1L

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
        scope.launch {
            for (signal in invalidations) {
                val snapshot = state.takeUnless { it.stale || it.running }?.snapshot ?: continue
                val check = checkFreshness(snapshot)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && state.snapshot === snapshot && !state.stale && !state.running) {
                        when {
                            changes.get() != check.change -> invalidations.trySend(Unit)
                            check.current -> {
                                verifiedChanges = check.change
                                publish(state)
                            }
                            else -> publish(state.copy(message = STALE_MESSAGE, stale = true))
                        }
                    }
                }
            }
        }
    }

    fun refresh(moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main") =
        refresh(moduleName, applicationClass, sourceSet, null)

    private fun refresh(moduleName: String?, applicationClass: String?, sourceSet: String, open: WicketMountRow?, retry: Boolean = true) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val request = ++generation
        job?.cancel(); navigation?.cancel()
        val previous = state.snapshot.takeIf { open != null }
        publish(WicketRoutesState("Discovering mounted URLs; waiting for indexing and committed documents…", running = true, snapshot = previous))
        fun retryOpen(): Boolean {
            if (open == null || !retry) return false
            refresh(moduleName, applicationClass, sourceSet, open, retry = false)
            return true
        }
        job = scope.launch {
            try {
                val snapshot = WicketRoutesRequest().prepare(project, moduleName, applicationClass, sourceSet)
                val check = checkFreshness(snapshot)
                val destination = open?.let { refreshedPage(snapshot, it) }
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && generation == request) {
                        job = null
                        if (!check.current && retryOpen()) return@withContext
                        val mounts = snapshot.routes.applications.sumOf { it.mounts.size }
                        verifiedChanges = check.change
                        val message = when {
                            !check.current -> STALE_MESSAGE
                            open != null && destination == null -> "That mount changed. Choose a path from the updated list."
                            else -> "$mounts mount declarations in ${snapshot.routes.applications.size} applications"
                        }
                        publish(WicketRoutesState(message, snapshot = snapshot, stale = !check.current))
                        if (destination != null && check.current) navigateSnapshot(snapshot, destination) {
                            if (!retryOpen()) publish(state.copy(message = STALE_MESSAGE, stale = true))
                        }
                        if (check.current && !current()) invalidations.trySend(Unit)
                    }
                }
            } catch (cancelled: CancellationException) { cancelled(request); throw cancelled
            } catch (_: ProcessCanceledException) { cancelled(request)
            } catch (failure: Exception) {
                if (failure !is PageAnalysisFailure) logger<WicketRoutesService>().warn("Mounted URL discovery failed", failure)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && generation == request) {
                        job = null
                        if (failure is PageAnalysisFailure && failure.code == "STALE_SNAPSHOT" && retryOpen()) return@withContext
                        publish(WicketRoutesState((failure as? PageAnalysisFailure)?.message ?: "Route discovery failed. See the IDE log.",
                            snapshot = previous, stale = previous != null))
                    }
                }
            }
        }
    }

    fun cancel() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        ++generation; job?.cancel(); navigation?.cancel(); job = null
        val snapshot = state.snapshot
        publish(state.copy(message = "Mounted URL discovery cancelled. Refresh to try again.", running = false,
            stale = snapshot != null && (state.stale || state.running)))
    }

    fun openPage(row: WicketMountRow) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val snapshot = state.snapshot ?: return
        val location = row.mount.pageSource ?: return
        if (state.running || row.mount.path == null || row.application !in snapshot.routes.applications ||
            row.mount !in row.application.mounts) return
        val refreshAndOpen = { refresh(snapshot.moduleName, snapshot.applicationClass, snapshot.sourceSet, row) }
        if (state.stale) refreshAndOpen() else navigateSnapshot(snapshot, location, refreshAndOpen)
    }

    private fun refreshedPage(snapshot: WicketRoutesSnapshot, old: WicketMountRow): SourceLocation? =
        snapshot.routes.applications.asSequence().filter {
            it.className == old.application.className && it.moduleName == old.application.moduleName &&
                it.sourceSet == old.application.sourceSet && it.source.file == old.application.source.file
        }.flatMap { it.mounts.asSequence() }.filter {
            it.path == old.mount.path && it.pageClass == old.mount.pageClass &&
                it.registration.file == old.mount.registration.file && it.pageSource?.file == old.mount.pageSource?.file
        }.mapNotNull { it.pageSource }.distinct().singleOrNull()

    fun current(): Boolean = state.let {
        it.snapshot != null && !it.running && !it.stale && !project.isDisposed && verifiedChanges == changes.get()
    }

    private data class Freshness(val change: Long, val current: Boolean)

    private suspend fun checkFreshness(snapshot: WicketRoutesSnapshot) = constrainedReadAction {
        val change = changes.get()
        val current = try {
            WicketRoutesRequest().requireCurrent(project, snapshot)
            true
        } catch (_: PageAnalysisFailure) {
            false
        }
        Freshness(change, current)
    }

    fun navigate(location: SourceLocation): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!current()) return false
        val snapshot = state.snapshot!!
        val locations = snapshot.routes.applications.flatMap { app -> listOf(app.source) + app.mounts.flatMap {
            listOfNotNull(it.registration, it.pageSource, it.markupSource)
        } }
        if (location !in locations) return false
        if (snapshot.version.sources.none { it.path == location.file }) return false
        navigateSnapshot(snapshot, location) { publish(state.copy(message = STALE_MESSAGE, stale = true)) }
        return true
    }

    private fun navigateSnapshot(snapshot: WicketRoutesSnapshot, location: SourceLocation, onUnavailable: () -> Unit) {
        navigation?.cancel()
        val source = snapshot.version.sources.singleOrNull { it.path == location.file }
        navigation = ReadAction.nonBlocking<OpenFileDescriptor?> {
            try { WicketRoutesRequest().requireCurrent(project, snapshot) }
            catch (_: PageAnalysisFailure) { return@nonBlocking null }
            val file = source?.let { VirtualFileManager.getInstance().findFileByUrl(it.url) } ?: return@nonBlocking null
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@nonBlocking null
            if (location.offset !in 0..document.textLength) return@nonBlocking null
            OpenFileDescriptor(project, file, location.offset)
        }.inSmartMode(project).withDocumentsCommitted(project).expireWith(this)
            // IntelliJ reruns the read if a write intervenes before this callback.
            .finishOnUiThread(ModalityState.defaultModalityState()) { descriptor ->
                if (state.snapshot !== snapshot || state.running) return@finishOnUiThread
                navigation = null
                if (descriptor?.canNavigate() == true) descriptor.navigate(true) else onUnavailable()
            }.submit(AppExecutorUtil.getAppExecutorService())
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
        if (project.isDisposed) return
        // Document notifications may arrive on the EDT while model locks are forbidden.
        changes.incrementAndGet()
        if (state.snapshot != null && !state.stale) invalidations.trySend(Unit)
    }
    private fun cancelled(request: Long) {
        if (!project.isDisposed) scope.launch(Dispatchers.EDT) {
            if (!project.isDisposed && request == generation && state.running) { job = null; publish(WicketRoutesState("Discovery cancelled. Refresh to try again.")) }
        }
    }
    override fun dispose() { invalidations.close(); listeners.clear() }

    private companion object {
        const val STALE_MESSAGE = "Out of date. Click a path or refresh."
    }
}
