package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketSourceSetupEditorTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    private var autoComplete = true

    override fun setUp() {
        super.setUp()
        service.setEditorFollowing(false)
        service.cancel()
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    }

    override fun tearDown() {
        try {
            service.cancel()
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testFactoryIdReferencesResolveToTheActualLiteralOriginInBothDirections() {
        val java = page("add(label(\"title\"));", "private Label label(String id) { return new Label(id); }")
        val html = companion()
        assertEquals(html.idValue("title"), java.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(java.idValue("title"), html.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
    }

    fun testNativeRenameChangesOneFactoryInvocationAndItsMarkup() {
        val java = page("add(label(\"title\"), label(\"second\"));", "private Label label(String id) { return new Label(id); }")
        val html = companion(html = "<span wicket:id=\"title\"></span><span wicket:id=\"second\"></span>")
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("label(\"heading\"), label(\"second\")"))
        assertTrue(java.text.contains("return new Label(id)"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertTrue(html.text.contains("wicket:id=\"second\""))
    }

    fun testSharedFactoryAndSuperclassLiteralsCannotBeRenamedForOnePage() {
        val java = page("add(label());", "private Label label() { return new Label(\"title\"); }")
        val html = companion()
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testFactoryCompletionSupportsAnEmptyIdAtTheCallSite() {
        val java = page("add(label(\"\"));", "private Label label(String id) { return new Label(id); }")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val choices = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), choices.map { it.lookupString })
        myFixture.lookup.currentItem = choices.single()
        myFixture.finishLookup('\n')
        assertTrue(java.text.contains("label(\"title\")"))
    }

    fun testUnpairedFactoryIdInspectionUsesTheCallerLiteral() {
        val java = page("add(label(\"title\"));", "private Label label(String id) { return new Label(id); }")
        companion(html = "<div>No IDs</div>")
        val holder = ProblemsHolder(InspectionManager.getInstance(project), java, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(java)
        assertEquals(1, holder.results.size)
        assertEquals(java.idValue("title").textRange, holder.results.single().psiElement!!.textRange)
        assertEquals("\"title\"", holder.results.single().psiElement!!.text)
    }

    fun testReorderedSourceConstructorIdIsTheOnlyNavigableArgument() {
        myFixture.addFileToProject("src/main/java/example/Heading.java", """
            package example; public class Heading extends org.apache.wicket.markup.html.basic.Label {
                public Heading(String text, String id) { super(id, text); }
            }
        """.trimIndent())
        val java = page("add(new Heading(\"display\", \"title\"));")
        val html = companion()
        assertNull(WicketIdPsi.javaId(java.idValue("display")))
        assertNotNull(WicketIdPsi.javaId(java.idValue("title")))
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
    }

    fun testExternalFactoryIsCapturedAndUnsavedEditsInvalidateTheReport() {
        val factory = myFixture.addFileToProject("src/main/java/example/Parts.java", """
            package example; public class Parts {
                public static org.apache.wicket.markup.html.basic.Label label(String id) {
                    return new org.apache.wicket.markup.html.basic.Label(id);
                }
            }
        """.trimIndent())
        val java = page("add(Parts.label(\"title\"));")
        companion()
        analyze(java)
        val prepared = service.state.prepared!!
        assertTrue(prepared.version.sources.any { it.path == factory.virtualFile.path })
        assertEquals(3, prepared.version.sources.size)
        edit(factory, factory.text.replace("Label(id)", "Label(id + \"changed\")"), commit = false)
        assertFalse(service.refreshFreshness())
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun testSourceSuperclassEditIsObservedOnReanalysis() {
        val base = myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage() { add(new org.apache.wicket.markup.html.basic.Label("base")); }
            }
        """.trimIndent())
        val java = page("add(new Label(\"title\"));", base = "BasePage")
        companion(html = "<span wicket:id=\"base\"></span><span wicket:id=\"title\"></span>")
        analyze(java)
        assertTrue(service.state.prepared!!.version.sources.any { it.path == base.virtualFile.path })
        assertEquals(2, service.state.prepared!!.report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        edit(base, base.text.replace("\"base\"", "\"changed\""))
        assertTrue(service.state.stale)
        service.reanalyze()
        awaitReport()
        assertEquals(setOf("changed", "title"), service.state.prepared!!.report.children().map { it.wicketId }.toSet())
    }

    fun testOneLiteralForwardedToTwoComponentPathsCannotBeRenamedAsOnePair() {
        val java = page("""
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            populate(left, right, "name"); add(left, right);
        """.trimIndent(), """
            private void populate(org.apache.wicket.markup.html.form.Form<?> left, org.apache.wicket.markup.html.form.Form<?> right, String id) {
                left.add(new Label(id)); right.add(new Label(id));
            }
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"left\"><span wicket:id=\"name\"></span></form><form wicket:id=\"right\"><span wicket:id=\"name\"></span></form>")
        assertEquals(4, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"name\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"name\"") + 1))
    }

    fun testBaseImplementationRenameCannotSilentlyBreakADescendantsOwnMarkup() {
        val base = myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        val baseHtml = companion("BasePage")
        page("", base = "BasePage")
        companion()
        assertNotNull(WicketIdPsi.pair(base.idValue("title")))
        assertNull(WicketIdRenameTarget.at(base, base.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(baseHtml, baseHtml.text.indexOf("\"title\"") + 1))
    }

    fun testAnOwnPrivateFinalFieldKeepsPairedRenameWhenAttachedByAHelper() {
        val java = page("attach(title);", """
            private final Label title = new Label("title");
            private void attach(Label child) { add(child); }
        """.trimIndent())
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertTrue(java.text.contains("attach(title)"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testDerivedCallerLiteralCannotRenameASharedInheritedTemplate() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage(String id) { add(new org.apache.wicket.markup.html.basic.Label(id)); }
            }
        """.trimIndent())
        val html = companion("BasePage")
        val java = page("super(\"title\");", base = "BasePage")
        val sibling = myFixture.addFileToProject("src/main/java/example/SiblingPage.java", """
            package example; public final class SiblingPage extends BasePage {
                public SiblingPage() { super("title"); }
            }
        """.trimIndent()) as PsiJavaFile
        val before = listOf(java.text, sibling.text, html.text)
        for (file in listOf(java, sibling)) {
            val pair = WicketIdPsi.pair(file.idValue("title"))!!
            assertEquals(html.idValue("title"), pair.attribute)
            assertTrue(pair.page.report.nodes.single { it.wicketId == "title" }.idRenameSafe)
            assertEquals(DiagnosticCode.MATCHED, pair.page.report.matching!!.findings.single().code)
            assertNull(WicketIdRenameTarget.at(file, file.text.indexOf("\"title\"") + 1))
            assertFalse(WicketIdRenameTarget.safe(pair))
        }
        assertEquals(before, listOf(java.text, sibling.text, html.text))
    }

    fun testDerivedCallerLiteralCanRenameItsOwnCompanionWithoutChangingASiblingTemplate() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage(String id) { add(new org.apache.wicket.markup.html.basic.Label(id)); }
            }
        """.trimIndent())
        val shared = companion("BasePage")
        val java = page("super(\"title\");", base = "BasePage")
        val own = companion()
        val sibling = myFixture.addFileToProject("src/main/java/example/SiblingPage.java", """
            package example; public final class SiblingPage extends BasePage {
                public SiblingPage() { super("title"); }
            }
        """.trimIndent()) as PsiJavaFile
        val before = sibling.text to shared.text
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("super(\"heading\")"))
        assertTrue(own.text.contains("wicket:id=\"heading\""))
        assertEquals(before, sibling.text to shared.text)
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(sibling).single().report.matching!!.findings.single().code)
    }

    fun testOwnExtensionDoesNotMakeAnInheritedAttributeSafeToRename() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage(String id) { add(new org.apache.wicket.markup.html.basic.Label(id)); }
            }
        """.trimIndent())
        val shared = companion("BasePage", "<html><body><span wicket:id=\"title\"></span><wicket:child/></body></html>")
        val java = page("super(\"title\"); add(new Label(\"local\"));", base = "BasePage")
        val own = companion(html = "<wicket:extend><span wicket:id=\"local\"></span></wicket:extend>")
        val pair = WicketIdPsi.pair(java.idValue("title"))!!
        assertEquals(shared.idValue("title"), pair.attribute)
        assertEquals(own.virtualFile.path, pair.page.report.markupPath)
        assertEquals(2, pair.page.report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertFalse(WicketIdRenameTarget.safe(pair))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNotNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"local\"") + 1))
    }

    fun testInterfaceFactoryReferencesAndNativeRenameUseTheCallersLiteral() {
        val impl = instanceFactory()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        val html = companion()
        val pair = WicketIdPsi.pair(java.idValue("title"))!!
        assertEquals(html.idValue("title"), pair.attribute)
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("parts.label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertTrue(impl.text.contains("new org.apache.wicket.markup.html.basic.Label(id)"))
    }

    fun testNativeCompletionWorksForAnInterfaceFactoryCall() {
        instanceFactory()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"\"));")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single()
        myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }

    fun testInstanceImplementationAndInterfaceParticipateInSnapshotFreshness() {
        val impl = instanceFactory()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        companion()
        analyze(java)
        val prepared = service.state.prepared!!
        assertEquals(4, prepared.version.sources.size)
        assertTrue(prepared.version.sources.any { it.path.endsWith("/Parts.java") })
        assertTrue(prepared.version.sources.any { it.path == impl.virtualFile.path })
        edit(impl, impl.text.replace("Label(id)", "Label(\"changed\")"), commit = false)
        assertFalse(service.refreshFreshness())
        assertFalse(service.copyContext())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); awaitReport()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testInterfaceDeclarationEditsInvalidateDispatchSnapshots() {
        instanceFactory()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        companion()
        analyze(java)
        val api = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Parts.java"))!!
        edit(api, api.text.replace("String id", "CharSequence id"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.navigate(service.state.prepared!!.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun testKnownFactoryMissingPairsAreReportedAtTheCallerAndMarkup() {
        instanceFactory()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"javaOnly\"));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        for (file in listOf(java, html)) {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
            assertEquals(1, holder.results.size)
            assertTrue(file.text.substring(holder.results.single().psiElement.textRange.startOffset).contains(if (file == java) "javaOnly" else "htmlOnly"))
        }
    }

    fun testAHelperImplementationLiteralIsNotRenamedAsASingleCallSitePair() {
        val impl = instanceFactory("return new org.apache.wicket.markup.html.basic.Label(\"title\");")
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"ignored\"));")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(impl, impl.text.indexOf("\"title\"") + 1))
    }

    fun testFinalPageInheritedVirtualFactoryRetainsCallerRenameAndBaseNavigation() {
        val base = myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                protected org.apache.wicket.markup.html.basic.Label label(String id) { return new org.apache.wicket.markup.html.basic.Label(id); }
            }
        """.trimIndent())
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public final class ExamplePage extends BasePage {
                public ExamplePage() { add(label("title")); }
            }
        """.trimIndent()) as PsiJavaFile
        val html = companion()
        analyze(java)
        val node = service.state.prepared!!.report.children().single()
        assertEquals(base.virtualFile.path, node.javaLocation.file)
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testSwitchingAKnownHelperImplementationUpdatesTheDependencySet() {
        instanceFactory()
        val other = myFixture.addFileToProject("src/main/java/example/OtherParts.java", """
            package example; public class OtherParts implements Parts {
                public org.apache.wicket.markup.html.basic.Label label(String ignored) { return new org.apache.wicket.markup.html.basic.Label("changed"); }
            }
        """.trimIndent())
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        companion()
        analyze(java)
        edit(java, java.text.replace("new DefaultParts()", "new OtherParts()"))
        service.reanalyze(); awaitReport()
        val prepared = service.state.prepared!!
        assertEquals("changed", prepared.report.children().single().wicketId)
        assertTrue(prepared.version.sources.any { it.path == other.virtualFile.path })
        assertTrue(prepared.version.sources.none { it.path.endsWith("/DefaultParts.java") })
    }

    fun testConfiguredProviderConstructorIdSupportsReferencesAndNativeRename() {
        configuredFactory()
        val java = page("add(parts.build());", "private final ConfiguredParts parts = new ConfiguredParts(\"title\");")
        val html = companion()
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("ConfiguredParts(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testEmptyProviderConstructorIdReceivesNativeCompletion() {
        configuredFactory()
        val java = page("add(new ConfiguredParts(\"\").build());")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }

    fun testConfiguredConstructorIdIsRecognizedForColoringButDisplayTextIsNot() {
        myFixture.addFileToProject("src/main/java/example/ConfiguredParts.java", """
            package example; public class ConfiguredParts {
                private final String id; private final String caption;
                public ConfiguredParts(String id, String caption) { this.id = id; this.caption = caption; }
                public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id, caption); }
            }
        """.trimIndent())
        val java = page("add(new ConfiguredParts(\"title\", \"Heading\").build());")
        companion()
        assertNotNull(WicketIdPsi.javaId(java.idValue("title")))
        assertNull(WicketIdPsi.javaId(java.idValue("Heading")))
    }

    fun testConfiguredMissingPairProblemUsesTheConstructorArgument() {
        configuredFactory()
        val java = page("add(new ConfiguredParts(\"javaOnly\").build());")
        companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        val holder = ProblemsHolder(InspectionManager.getInstance(project), java, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(java)
        assertEquals(1, holder.results.size)
        assertEquals(java.idValue("javaOnly").textRange, holder.results.single().psiElement.textRange)
    }

    fun testReusingOneStoredIdForTwoPathsPreventsPairedRename() {
        configuredFactory()
        val java = page("""
            ConfiguredParts parts = new ConfiguredParts("name");
            org.apache.wicket.markup.html.WebMarkupContainer left = new org.apache.wicket.markup.html.WebMarkupContainer("left");
            org.apache.wicket.markup.html.WebMarkupContainer right = new org.apache.wicket.markup.html.WebMarkupContainer("right");
            left.add(parts.build()); right.add(parts.build()); add(left, right);
        """)
        val html = companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        assertEquals(4, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"name\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"name\"") + 1))
    }

    fun testConfiguredProviderSourceEditsInvalidateSnapshots() {
        val impl = configuredFactory()
        val java = page("add(new ConfiguredParts(\"title\").build());")
        companion()
        analyze(java)
        assertTrue(service.state.prepared!!.version.sources.any { it.path == impl.virtualFile.path })
        edit(impl, impl.text.replace("this.id = id;", "this.id = \"changed\";"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); awaitReport()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testChangingOneProviderConfigurationLeavesTheOtherAllocationIntact() {
        configuredFactory()
        val java = page("add(first.build(), second.build());", """
            private final ConfiguredParts first = new ConfiguredParts("title");
            private final ConfiguredParts second = new ConfiguredParts("subtitle");
        """)
        companion(html = "<div><span wicket:id=\"title\"></span><span wicket:id=\"subtitle\"></span></div>")
        analyze(java)
        edit(java, java.text.replace("\"title\"", "\"changed\""))
        service.reanalyze(); awaitReport()
        assertEquals(setOf("changed", "subtitle"), service.state.prepared!!.report.children().map { it.wicketId }.toSet())
    }

    fun testConstructorDefaultImplementationLiteralIsNotRenamedPerCaller() {
        myFixture.addFileToProject("src/main/java/example/ConfiguredParts.java", """
            package example; public class ConfiguredParts {
                private final String id;
                public ConfiguredParts() { this("title"); }
                public ConfiguredParts(String id) { this.id = id; }
                public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id); }
            }
        """.trimIndent())
        val java = page("add(new ConfiguredParts().build());")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    private fun configuredFactory() = myFixture.addFileToProject("src/main/java/example/ConfiguredParts.java", """
        package example; public class ConfiguredParts {
            private final String id;
            public ConfiguredParts(String id) { this.id = id; }
            public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id); }
        }
    """.trimIndent())

    private fun instanceFactory(body: String = "return new org.apache.wicket.markup.html.basic.Label(id);"): PsiFile {
        myFixture.addFileToProject("src/main/java/example/Parts.java", """
            package example; public interface Parts { org.apache.wicket.markup.html.basic.Label label(String id); }
        """.trimIndent())
        return myFixture.addFileToProject("src/main/java/example/DefaultParts.java", """
            package example; public class DefaultParts implements Parts {
                public org.apache.wicket.markup.html.basic.Label label(String id) { $body }
            }
        """.trimIndent())
    }

    private fun page(body: String, members: String = "", base: String = "org.apache.wicket.markup.html.WebPage") =
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            public class ExamplePage extends $base { public ExamplePage() { $body } $members }
        """.trimIndent()) as PsiJavaFile

    private fun analyze(java: PsiJavaFile) { service.analyze(java.virtualFile.url, -1); awaitReport() }
    private fun awaitReport() {
        PlatformTestUtil.waitWithEventsDispatching("Source setup report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
