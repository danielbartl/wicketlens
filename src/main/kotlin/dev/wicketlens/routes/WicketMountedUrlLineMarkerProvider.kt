package dev.wicketlens.routes

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.xml.util.XmlStringUtil
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketEditorTargetResolver
import dev.wicketlens.model.SourceLocation

class WicketMountedUrlLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = "Mounted Wicket URLs"
    override fun getIcon() = MARKER
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
        val project = file.project
        val virtualFile = file.virtualFile ?: return
        if (!file.isPhysical || !ProjectFileIndex.getInstance(project).isInContent(virtualFile) ||
            DumbService.isDumb(project) || PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty()) return

        try {
            val anchors = when (file) {
                is PsiJavaFile -> elements.filterIsInstance<PsiIdentifier>().filter { leaf ->
                    val type = leaf.parent as? PsiClass
                    type?.nameIdentifier == leaf && InheritanceUtil.isInheritor(type, "org.apache.wicket.Page")
                }.map { it to SourceLocation(virtualFile.path, it.textOffset) }
                is XmlFile -> {
                    val opening = file.rootTag?.firstChild
                    if (virtualFile.extension != "html" || opening == null || opening !in elements) return
                    WicketEditorTargetResolver().resolve(project, virtualFile.url, -1)
                    listOf(opening to SourceLocation(virtualFile.path, 0))
                }
                else -> return
            }
            if (anchors.isEmpty()) return
            val paths = mountedPaths(project)
            for ((leaf, location) in anchors) {
                val routes = paths[location] ?: continue
                val tooltip = tooltip(routes)
                val pageClass = (leaf.parent as? PsiClass)?.qualifiedName
                result += LineMarkerInfo(leaf, leaf.textRange, icon, { tooltip }, { _, anchor ->
                    val window = ToolWindowManager.getInstance(anchor.project).getToolWindow("WicketLens")
                    window?.activate {
                        val contents = window.contentManager
                        val content = contents.contents.firstOrNull { it.component is WicketRoutesPanel } ?: return@activate
                        (content.component as WicketRoutesPanel).showMountedPage(location.file, pageClass)
                        contents.setSelectedContent(content, true)
                    }
                }, GutterIconRenderer.Alignment.LEFT, { name })
            }
        } catch (_: IndexNotReadyException) {
            // The next highlighting pass can retry in smart mode.
        } catch (_: AnalysisUnavailable) {
            // The markup file has no unique conventional Wicket owner.
        }
    }

    private fun mountedPaths(project: Project): Map<SourceLocation, Map<String, MountStatus>> =
        CachedValuesManager.getManager(project).getCachedValue(project, MOUNTS, {
            val paths = mutableMapOf<SourceLocation, MutableMap<String, MountStatus>>()
            // The editor needs all mount declarations even when the browser is filtered.
            for (application in WicketRouteAnalyzer().analyze(project, sourceSet = "any").applications) {
                for (mount in application.mounts) {
                    val path = mount.path ?: continue
                    for (location in listOfNotNull(mount.pageSource, mount.markupSource)) {
                        val pagePaths = paths.getOrPut(location) { linkedMapOf() }
                        pagePaths[path] = minOf(pagePaths[path] ?: mount.status, mount.status)
                    }
                }
            }
            CachedValueProvider.Result.create(paths,
                PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(project), VirtualFileManager.getInstance())
        }, false)

    private fun tooltip(routes: Map<String, MountStatus>): String {
        val entries = routes.toSortedMap().entries.take(TOOLTIP_LIMIT).map { (path, status) ->
            val note = if (status == MountStatus.DECLARED) "" else " (${status.name.lowercase()})"
            XmlStringUtil.escapeString(path.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")) + note
        }
        val more = routes.size - entries.size
        val footer = if (more == 0) "" else "<br>$more more in Mounted URLs"
        return XmlStringUtil.wrapInHtml("<b>Mounted URLs</b><br>" + entries.joinToString("<br>") + footer +
            "<br>Click to show this page’s mounts in WicketLens.")
    }

    private companion object {
        const val TOOLTIP_LIMIT = 24
        val MOUNTS = Key.create<CachedValue<Map<SourceLocation, Map<String, MountStatus>>>>("wicketlens.gutter.mounts")
        val MARKER = IconLoader.getIcon("/icons/mountedWicketUrl.svg", WicketMountedUrlLineMarkerProvider::class.java)
    }
}
