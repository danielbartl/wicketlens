package dev.wicketlens.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.*
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

/** Navigation consumes the same source template graphs; it never grants editing authority. */
internal object WicketTreeNavigation {
    private val LIBRARY_KEY=Key.create<CachedValue<List<PageReport>>>("wicketlens.tree.library.navigation")
    fun targets(element:PsiElement):List<PsiElement> {
        if(element !is PsiMethod && element !is XmlAttributeValue) return emptyList()
        val file=element.containingFile
        val library=element is XmlAttributeValue && !ProjectFileIndex.getInstance(file.project).isInContent(file.virtualFile)
        val reports=if(library) libraryReports(file) else WicketEditorAnalysis.navigationPages(file).map { it.report }
        return reports.flatMap { report ->
            val tree=report.treeTemplate ?: return@flatMap emptyList()
            val origin=SourceLocation(file.virtualFile.path,element.textOffset)
            if(element is PsiMethod) {
                when(origin) {
                    tree.nodeFactoryLocation -> listOfNotNull(report.markupPath?.let { WicketIdPsi.source(file.project,it) })
                    tree.contentFactoryLocation,tree.junctionFactoryLocation -> {
                        val id=if(origin==tree.contentFactoryLocation) "content" else "junction"
                        report.markup!!.elements.filter { it.wicketId==id && it.parentWicketElementId==null }.mapNotNull { WicketIdPsi.markupValue(file.project,it) }
                    }
                    else -> emptyList()
                }
            } else if(element is XmlAttributeValue) {
                val selected=report.markup?.elements?.singleOrNull { it.parentWicketElementId==null && WicketIdPsi.markupValue(file.project,it)==element }
                val target=when(selected?.wicketId) { "content"->tree.contentFactoryLocation;"junction"->tree.junctionFactoryLocation;else->null }
                listOfNotNull(target?.let { location -> WicketIdPsi.source(file.project,location.file)?.findElementAt(location.offset)?.let { leaf ->
                    generateSequence(leaf) { it.parent }.filterIsInstance<PsiMethod>().firstOrNull()?.nameIdentifier
                } })
            } else emptyList()
        }.distinct()
    }
    private fun libraryReports(file:PsiFile):List<PageReport> {
        val index=ProjectFileIndex.getInstance(file.project)
        if(file.virtualFile==null || file.virtualFile.name!="Node.html" || !index.isInLibrary(file.virtualFile) ||
            com.intellij.openapi.project.DumbService.isDumb(file.project) || PsiDocumentManager.getInstance(file.project).uncommittedDocuments.isNotEmpty()) return emptyList()
        return CachedValuesManager.getCachedValue(file,LIBRARY_KEY) {
            val archive=file.virtualFile.path.substringBefore("!/")
            val node=JavaPsiFacade.getInstance(file.project).findClasses(WicketTreeContexts.NODE,GlobalSearchScope.allScope(file.project))
                .singleOrNull { it is PsiCompiledElement && it.containingFile.virtualFile.path.substringBefore("!/")==archive }
            val seeds=mutableListOf<Pair<PsiClass,PageReport>>();var visits=0;var complete=true
            if(node!=null) ClassInheritorsSearch.search(node,GlobalSearchScope.projectScope(file.project),true).forEach(com.intellij.util.Processor { type ->
                ProgressManager.checkCanceled()
                if(++visits>256 || seeds.size>=16) { complete=false;false }
                else {
                    if(type !is PsiCompiledElement && type.qualifiedName!=null && index.isInContent(type.containingFile.virtualFile) && WicketComponentTypes.ownsMarkup(type)) {
                        val report=WicketPageAnalyzer().analyze(type)
                        if(report?.markup?.sourceFiles?.contains(file.virtualFile.path)==true) seeds+=type to report
                    }
                    true
                }
            })
            val contexts=if(complete && seeds.isNotEmpty()) WicketNavigationContextsAnalyzer().analyze(seeds) else null
            val result=if(contexts?.complete==true) contexts.contexts.filter { it.relationship=="TREE_TEMPLATE" }.map { it.report } else emptyList()
            CachedValueProvider.Result.create(result,PsiModificationTracker.MODIFICATION_COUNT,ProjectRootManager.getInstance(file.project),VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }
}
class WicketTreeDeclarationHandler:GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement:PsiElement?,offset:Int,editor:Editor):Array<PsiElement>? {
        val method=PsiTreeUtil.getParentOfType(sourceElement,PsiMethod::class.java,false) ?: return null
        if(method.nameIdentifier?.textRange?.containsOffset(offset)!=true || !WicketEditorAnalysis.supports(method.containingFile)) return null
        return WicketTreeNavigation.targets(method).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
