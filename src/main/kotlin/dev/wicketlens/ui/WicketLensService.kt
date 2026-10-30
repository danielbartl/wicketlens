package dev.wicketlens.ui

import com.intellij.openapi.Disposable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.platform.ide.progress.withBackgroundProgress
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.analysis.EditorPageTarget
import dev.wicketlens.analysis.WicketEditorTargetResolver
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class PreparedReport(val report: PageReport, val presentation: FormattedReport, val json: String, val version: ReportVersion,
    val hierarchy: List<ReportTreeEntry> = ReportHierarchyBuilder().build(report)) {
    val text get() = presentation.text
}
data class ReportState(val message: String, val running: Boolean = false, val prepared: PreparedReport? = null,
    val stale: Boolean = false, val canReanalyze: Boolean = false)
private data class AnalysisRequest(val sourceUrl: String, val caretOffset: Int, val qualifiedClassName: String? = null,
    val fromEditor: Boolean = false, val markupSuffix: String = "")

/** One bounded snapshot per project; injected scope cancels work on project close. */
@Service(Service.Level.PROJECT)
class WicketLensService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    @Volatile var state = ReportState("Open a Wicket Java Page or Panel and run Analyze Wicket Page.")
        private set
    private var active: Job? = null
    private var navigation: Job? = null
    private var generation = 0L
    private val listeners = CopyOnWriteArrayList<(ReportState) -> Unit>()
    private val changes = AtomicLong()
    @Volatile private var watchedPaths = emptyList<String>()
    private var lastRequest: AnalysisRequest? = null
    @Volatile var followEditor = PropertiesComponent.getInstance(project).getBoolean("wicketlens.follow.editor", true)
        private set
    private var editorObservers = 0
    private var activeEditorUrl: String? = null
    private var activeFromEditor = false
    // Retain the source-navigation context through delayed/duplicate editor-selection events.
    private var navigatingTo: String? = null

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                FileDocumentManager.getInstance().getFile(event.document)?.path?.let {
                    if (watches(it)) changed()
                }
            }
        }, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val newUrl = event.newFile?.url
                val ownNavigation = newUrl != null && newUrl == navigatingTo
                if (!ownNavigation) navigatingTo = null
                if (followEditor && editorObservers > 0 && !ownNavigation) {
                    followSelectedEditor(event.newFile)
                }
            }
        })
        connection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { changed() })
        connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) = changed()
        })
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { !it.isFromSave && (watches(it.path) || it.file?.path?.let(::watches) == true) }) changed()
            }
        })
    }

    fun analyze(sourceUrl: String, caretOffset: Int) {
        analyze(sourceUrl, caretOffset, "")
    }

    fun analyze(sourceUrl: String, caretOffset: Int, markupSuffix: String) {
        navigatingTo = null
        start(AnalysisRequest(sourceUrl, caretOffset, markupSuffix = markupSuffix))
    }

    fun reanalyze() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!state.running) lastRequest?.let(::start)
    }

    /** Only follow editors after the report UI has been opened; never force tool-window activation. */
    fun trackEditor(parent: Disposable) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        editorObservers++
        Disposer.register(parent) {
            editorObservers--
            if (editorObservers == 0 && activeFromEditor && state.running && !project.isDisposed) cancel()
        }
        if (editorObservers == 1 && followEditor && !state.running && selectedEditorFile() != null) followSelectedEditor()
    }

    fun setEditorFollowing(enabled: Boolean) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (followEditor == enabled) return
        navigatingTo = null
        followEditor = enabled
        PropertiesComponent.getInstance(project).setValue("wicketlens.follow.editor", enabled, true)
        when {
            enabled && editorObservers > 0 -> followSelectedEditor()
            !enabled && activeFromEditor && state.running -> cancel()
            else -> publish(state)
        }
    }

    private fun selectedEditorFile(): VirtualFile? {
        val manager = FileEditorManager.getInstance(project)
        return manager.selectedTextEditor?.document?.let { FileDocumentManager.getInstance().getFile(it) }
            ?: manager.selectedFiles.firstOrNull()
    }

    private fun followSelectedEditor(file: VirtualFile? = selectedEditorFile()) {
        val manager = FileEditorManager.getInstance(project)
        if (file != null) {
            if (state.running && activeEditorUrl == file.url) return
            val prepared = state.prepared
            val currentJava = lastRequest?.sourceUrl == file.url && prepared?.report?.markupSuffix?.isEmpty() == true
            val ownMarkup = prepared?.report?.markupPath == file.path &&
                prepared.report.qualifiedClassName.substringAfterLast('.') + prepared.report.markupSuffix == file.nameWithoutExtension
            val ownNavigation = navigatingTo == file.url
            if (prepared != null && !state.stale && (currentJava || ownMarkup || ownNavigation)) return
            if (file.extension in setOf("java", "html")) {
                val editor = manager.selectedTextEditor?.takeIf { FileDocumentManager.getInstance().getFile(it.document) == file }
                start(AnalysisRequest(file.url, editor?.caretModel?.offset ?: -1, fromEditor = true))
                return
            }
        }
        ++generation
        active?.cancel()
        navigation?.cancel()
        active = null
        lastRequest = null
        publish(ReportState("Select a Wicket Java Page/Panel or its companion HTML file."))
    }

    private fun start(input: AnalysisRequest) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        lastRequest = input.takeUnless { it.fromEditor }
        activeEditorUrl = input.sourceUrl
        activeFromEditor = input.fromEditor
        val request = ++generation
        active?.cancel()
        navigation?.cancel()
        publish(ReportState("Analyzing Wicket page; waiting for indexing and document commits if necessary…", running = true))
        active = scope.launch {
            try {
                if (input.fromEditor) delay(100)
                val (prepared, target) = withBackgroundProgress(project, "Analyze Wicket Page", cancellable = true) {
                    val (report, version, target) = constrainedReadAction(
                        ReadConstraint.inSmartMode(project),
                        ReadConstraint.withDocumentsCommitted(project),
                    ) {
                        val target = if (input.fromEditor) WicketEditorTargetResolver().resolve(project, input.sourceUrl, input.caretOffset)
                            else EditorPageTarget(input.sourceUrl, input.caretOffset, input.qualifiedClassName)
                        val source = VirtualFileManager.getInstance().findFileByUrl(target.sourceUrl)
                        watchedPaths = ProjectRootManager.getInstance(project).contentSourceRoots.map { it.path } + listOfNotNull(source?.path)
                        val suffix = if (input.fromEditor) target.markupSuffix else input.markupSuffix
                        val report = WicketPageSelection().analyze(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName, suffix)
                        val version = ReportVersion.capture(project, report, target.sourceUrl, changes.get())
                        watchedPaths = (watchedPaths + version.sources.flatMap { listOf(it.path, it.path.substringBefore("!/")) }).distinct()
                        Triple(report, version, target.copy(markupSuffix = suffix))
                    }
                    PreparedReport(report, WicketReportFormatter().formatWithLinks(report), WicketContextExporter().export(report), version) to target
                }
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && request == generation) {
                        active = null
                        lastRequest = AnalysisRequest(target.sourceUrl, target.caretOffset, prepared.report.qualifiedClassName, markupSuffix = target.markupSuffix)
                        val variant = if (target.markupSuffix.isEmpty()) "" else " [${prepared.report.markupPath?.substringAfterLast('/')}]"
                        publish(ReportState("${prepared.report.qualifiedClassName}$variant — static snapshot; re-run after editing.", prepared = prepared))
                        refreshFreshness()
                    }
                }
            } catch (cancelled: CancellationException) {
                cancelled(request)
                throw cancelled
            } catch (_: ProcessCanceledException) {
                cancelled(request)
            } catch (unavailable: AnalysisUnavailable) {
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && request == generation) {
                        active = null
                        publish(ReportState(unavailable.message ?: "The selected class cannot be analyzed."))
                    }
                }
            } catch (failure: Exception) {
                logger<WicketLensService>().warn("WicketLens analysis failed", failure)
                withContext(Dispatchers.EDT) {
                    if (!project.isDisposed && request == generation) {
                        active = null
                        publish(ReportState("Analysis failed. See the IDE log for details, then try again."))
                    }
                }
            }
        }
    }

    fun cancel() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        ++generation
        active?.cancel()
        navigation?.cancel()
        active = null
        publish(ReportState("Analysis cancelled. Run Analyze Wicket Page to try again."))
    }

    fun copyContext(): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!refreshFreshness()) return false
        val prepared = state.prepared ?: return false
        if (state.running) return false
        CopyPasteManager.getInstance().setContents(StringSelection(prepared.json))
        publish(state.copy(message = "Copied Wicket context for ${prepared.report.qualifiedClassName}. Re-run after editing."))
        return true
    }

    /** Return whether navigation was accepted; open on EDT with the coroutine's write-intent context. */
    fun navigate(location: SourceLocation): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (!refreshFreshness()) return false
        val prepared = state.prepared ?: return false
        if (prepared.presentation.links.none { it.javaLocation == location || it.markupLocation == location } &&
            prepared.hierarchy.none { it.javaLocation == location || it.markupLocation == location }) return false
        val source = prepared.version.sources.singleOrNull { it.path == location.file } ?: return false
        navigation?.cancel()
        navigation = scope.launch {
            // Unopened clean documents may have been collected since capture. Recreate them
            // off the EDT, then revalidate the snapshot before navigating to its offset.
            val length = constrainedReadAction {
                if (state.prepared !== prepared || !prepared.version.isCurrent(project, changes.get())) return@constrainedReadAction null
                val file = VirtualFileManager.getInstance().findFileByUrl(source.url) ?: return@constrainedReadAction null
                FileDocumentManager.getInstance().getDocument(file)?.textLength
            } ?: return@launch
            withContext(Dispatchers.EDT) {
                if (state.prepared !== prepared || !refreshFreshness() || location.offset !in 0..length) return@withContext
                val file = VirtualFileManager.getInstance().findFileByUrl(source.url) ?: return@withContext
                // Opening an editor may pump events. Recheck when the IDE actually requests the offset.
                navigatingTo = file.url
                try {
                    object : OpenFileDescriptor(project, file, location.offset) {
                        override fun getOffset(): Int = if (prepared.version.isCurrent(project, changes.get())) super.getOffset() else -1
                        override fun canNavigate(): Boolean = prepared.version.isCurrent(project, changes.get()) && super.canNavigate()
                    }.navigate(true)
                } finally { if (!refreshFreshness()) navigatingTo = null }
            }
        }
        return true
    }

    fun refreshFreshness(): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val prepared = state.prepared ?: return false
        if (state.running || state.stale) return false
        if (!prepared.version.isCurrent(project, changes.get())) {
            publish(state.copy(message = "Report is out of date. Re-analyze before navigating or copying.", stale = true))
            return false
        }
        return true
    }

    fun listen(parent: Disposable, listener: (ReportState) -> Unit) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        refreshFreshness()
        listeners += listener
        Disposer.register(parent) { listeners -= listener }
        listener(state)
    }

    private fun cancelled(request: Long) {
        if (project.isDisposed) return
        // Schedule cleanup without waiting on the EDT from a cancelled project scope.
        ToolWindowManager.getInstance(project).invokeLater {
            if (!project.isDisposed && request == generation && state.running) {
                active = null
                publish(ReportState("Analysis cancelled. Run Analyze Wicket Page to try again."))
            }
        }
    }

    private fun publish(next: ReportState) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        state = next.copy(canReanalyze = lastRequest != null && !next.running)
        listeners.forEach { it(state) }
    }

    private fun watches(path: String): Boolean = watchedPaths.any {
        path == it || path.startsWith("$it/") || it.startsWith("$path/")
    }

    private fun changed() {
        changes.incrementAndGet()
        if (project.isDisposed || state.prepared == null || state.stale) return
        if (ApplicationManager.getApplication().isDispatchThread) refreshFreshness()
        else scope.launch(Dispatchers.EDT) { if (!project.isDisposed) refreshFreshness() }
    }

    override fun dispose() { listeners.clear() }
}
