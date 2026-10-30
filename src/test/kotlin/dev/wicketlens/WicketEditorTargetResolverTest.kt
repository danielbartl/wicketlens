package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketEditorTargetResolver
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketEditorTargetResolverTest : WicketProjectPsiTestCase() {
    private val resolver = WicketEditorTargetResolver()

    fun testJavaEditorPreservesItsFileAndCaretIdentity() {
        val java = pageFile()
        val target = resolver.resolve(project, java.virtualFile.url, 42)
        assertEquals(java.virtualFile.url, target.sourceUrl)
        assertEquals(42, target.caretOffset)
    }

    fun testResourceCompanionFindsItsWicketJavaSource() {
        val java = pageFile()
        val html = companion()
        val target = resolver.resolve(project, html.virtualFile.url, 10)
        assertEquals(java.virtualFile.url, target.sourceUrl)
        assertEquals("example.ExamplePage", target.qualifiedClassName)
        assertEquals(-1, target.caretOffset)
    }

    fun testAdjacentCompanionUsesTheSamePairingContract() {
        val java = pageFile()
        val html = myFixture.addFileToProject("src/main/java/example/ExamplePage.html", "<span wicket:id=\"title\"></span>")
        assertEquals(java.virtualFile.url, resolver.resolve(project, html.virtualFile.url, 0).sourceUrl)
    }

    fun testPanelHtmlFindsItsPanelSource() {
        val java = myFixture.addFileToProject("src/main/java/example/DetailsPanel.java", """
            package example; public class DetailsPanel extends org.apache.wicket.markup.html.panel.Panel {
                public DetailsPanel(String id) { super(id); }
            }
        """.trimIndent())
        val html = companion("DetailsPanel", "<wicket:panel><div></div></wicket:panel>")
        val target = resolver.resolve(project, html.virtualFile.url, 0)
        assertEquals(java.virtualFile.url, target.sourceUrl)
        assertEquals("example.DetailsPanel", target.qualifiedClassName)
    }

    fun testAmbiguousForwardPairingDoesNotChooseOneHtmlFile() {
        pageFile()
        val html = companion()
        myFixture.addFileToProject("src/main/java/example/ExamplePage.html", "<span wicket:id=\"title\"></span>")
        assertThrows(AnalysisUnavailable::class.java) { resolver.resolve(project, html.virtualFile.url, 0) }
    }

    fun testPackageIdentityWinsOverSameSimpleClassName() {
        pageFile()
        val java = myFixture.addFileToProject("src/main/java/other/ExamplePage.java", """
            package other; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        val html = myFixture.addFileToProject("src/main/resources/other/ExamplePage.html", "<div></div>")
        assertEquals(java.virtualFile.url, resolver.resolve(project, html.virtualFile.url, 0).sourceUrl)
    }

    fun testProductionAndTestCompanionsRemainSeparate() {
        val java = pageFile()
        val html = companion()
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/resources"), JavaResourceRootType.TEST_RESOURCE)
        val testJava = myFixture.addFileToProject("src/test/java/example/ExamplePage.java", java.text) as PsiJavaFile
        val testHtml = myFixture.addFileToProject("src/test/resources/example/ExamplePage.html", html.text)
        assertEquals(java.virtualFile.url, resolver.resolve(project, html.virtualFile.url, 0).sourceUrl)
        assertEquals(testJava.virtualFile.url, resolver.resolve(project, testHtml.virtualFile.url, 0).sourceUrl)
    }

    fun testUnrelatedHtmlDoesNotReuseAnotherPagesSnapshot() {
        pageFile()
        val html = companion("Unrelated")
        assertThrows(AnalysisUnavailable::class.java) { resolver.resolve(project, html.virtualFile.url, 0) }
    }

    fun testNonWicketAndUnconfiguredHtmlAreUnsupported() {
        myFixture.addFileToProject("src/main/java/example/Plain.java", "package example; public class Plain {}")
        val plain = companion("Plain")
        assertThrows(AnalysisUnavailable::class.java) { resolver.resolve(project, plain.virtualFile.url, 0) }
        val outside = myFixture.addFileToProject("unconfigured/ExamplePage.html", "<div></div>")
        assertThrows(AnalysisUnavailable::class.java) { resolver.resolve(project, outside.virtualFile.url, 0) }
    }
}
