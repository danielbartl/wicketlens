package dev.wicketlens

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile
import dev.wicketlens.spring.WicketSpringInjectionInspection
import dev.wicketlens.spring.WicketSpringSerializationInspection
import dev.wicketlens.spring.WicketSpringAnnotationInspection

class WicketSpringEditorTest : WicketSpringInspectionTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WicketSpringInjectionInspection(), WicketSpringSerializationInspection(), WicketSpringAnnotationInspection())
        val profile = com.intellij.profile.codeInspection.InspectionProjectProfileManager.getInstance(project).currentProfile
        for (name in listOf("WicketSpringInjection", "WicketSpringSerialization", "WicketSpringAnnotation")) {
            profile.setErrorLevel(com.intellij.codeInsight.daemon.HighlightDisplayKey.find(name)!!, HighlightDisplayLevel.WEAK_WARNING, project)
        }
    }

    fun testNativeEditorHighlightsBothInspectionsWithoutOpeningTheSidebar() {
        val java = page("@SpringBean transient Service orders = null;")
        val highlights = highlights(java)
        assertEquals(2, highlights.size)
        assertTrue(highlights.all { it.severity == HighlightSeverity.WEAK_WARNING })
        assertEquals(setOf("null", "orders"), highlights.map { java.text.substring(it.startOffset, it.endOffset) }.toSet())
    }

    fun testCommittedEditorEditClearsTheConstructionWarning() {
        val java = page("@SpringBean Service orders = null;")
        assertEquals(1, highlights(java).size)
        edit(java, java.text.replace(" = null", ""))
        assertTrue(highlights(java).isEmpty())
    }

    fun testNativeAnnotationSuppressionHidesBothRules() {
        assertTrue(highlights(page("@SuppressWarnings({\"WicketSpringInjection\", \"WicketSpringSerialization\"}) @SpringBean transient Service orders = null;")).isEmpty())
    }

    fun testNativeAutowiredFindingUsesWeakWarningAndTheAnnotationAnchor() {
        application()
        val java = page("@org.springframework.beans.factory.annotation.Autowired Service orders;")
        val finding = highlights(java).single()
        assertEquals(HighlightSeverity.WEAK_WARNING, finding.severity)
        assertEquals("org.springframework.beans.factory.annotation.Autowired", java.text.substring(finding.startOffset, finding.endOffset))
    }

    fun testNativeMissingManualInjectionFindingClearsAfterAnEdit() {
        val java = source("Model", "public class Model extends org.apache.wicket.model.Model<String> { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; public Model() {} }")
        assertEquals(1, highlights(java).size)
        edit(java, java.text.replace("public Model() {}", "public Model() { org.apache.wicket.injection.Injector.get().inject(this); }"))
        assertTrue(highlights(java).isEmpty())
    }

    fun testNativeAnnotationIssueSupportsSuppression() {
        application()
        assertTrue(highlights(page("@SuppressWarnings(\"WicketSpringAnnotation\") @org.springframework.beans.factory.annotation.Autowired Service orders;")).isEmpty())
    }

    fun testNativeAnnotationInspectionCanBeDisabled() {
        val profile = com.intellij.profile.codeInspection.InspectionProjectProfileManager.getInstance(project).currentProfile
        application()
        val java = page("@org.springframework.beans.factory.annotation.Autowired Service orders;")
        assertEquals(1, highlights(java).size)
        profile.setToolEnabled("WicketSpringAnnotation", false, project)
        try { assertTrue(highlights(java).isEmpty()) }
        finally { profile.setToolEnabled("WicketSpringAnnotation", true, project) }
    }

    private fun highlights(file: PsiFile): List<com.intellij.codeInsight.daemon.impl.HighlightInfo> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.doHighlighting().filter { it.description?.let { message ->
            message.startsWith("This initializer may overwrite") || message.startsWith("A transient Spring injection") ||
                message.startsWith("Wicket's Spring listener") || message.startsWith("Wicket's Spring injector")
        } == true }
    }
}
