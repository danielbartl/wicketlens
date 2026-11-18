package dev.wicketlens.spring

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.xml.util.XmlStringUtil
import dev.wicketlens.model.SourceLocation

class WicketSpringBeanLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = "Wicket Spring bean sources"
    override fun getIcon() = BADGE
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        val file = elements.firstOrNull()?.containingFile ?: return
        if (!WicketSpringSupport.supports(file)) return
        val setup by lazy { WicketSpringSetup.forFile(file) }
        val catalog by lazy { WicketSpringBeanNames.catalog(file) }
        val analyzer = WicketDependencyAnalyzer()
        try {
            for (anchor in elements.filterIsInstance<PsiIdentifier>()) {
                ProgressManager.checkCanceled()
                val field = anchor.parent as? PsiField ?: continue
                if (field.nameIdentifier != anchor || !field.hasAnnotations() ||
                    !WicketProjectDependencyAnalyzer.isComponentField(field)) continue
                val candidates = linkedMapOf<SourceLocation, PsiElement>()
                val injection = analyzer.analyzeField(field, field.containingClass ?: continue, setup, "editor", { catalog }, file) { target ->
                    candidates[SourceLocation(target.containingFile.virtualFile.path, target.textRange.startOffset)] = target
                } ?: continue
                val targets = injection.candidates.mapNotNull { candidates[it.location] }.distinct()
                if (targets.isEmpty()) continue
                val names = injection.candidates.flatMap { it.names }.distinct().take(8)
                    .joinToString(", ") { name ->
                        XmlStringUtil.escapeString(name.take(80)) + if (name.length > 80) "…" else ""
                    }
                val tooltip = XmlStringUtil.wrapInHtml("<b>WicketLens Spring bean sources</b><br>$names<br>" +
                    "Click to open a source candidate. Runtime binding is unverified.")
                result += NavigationGutterIconBuilder.create(icon)
                    .setTargets(targets)
                    .setTooltipText(tooltip)
                    .setPopupTitle("Spring Bean Source Candidates")
                    .createLineMarkerInfo(anchor)
            }
        } catch (_: IndexNotReadyException) {
            // The next highlighting pass can retry in smart mode.
        }
    }

    private companion object {
        val BADGE = IconLoader.getIcon("/icons/mountedWicketUrl.svg", WicketSpringBeanLineMarkerProvider::class.java)
    }
}
