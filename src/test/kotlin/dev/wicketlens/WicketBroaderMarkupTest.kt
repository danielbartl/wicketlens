package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketLensService

class WicketBroaderMarkupTest : WicketProjectPsiTestCase() {
    override fun tearDown() {
        try {
            project.service<WicketLensService>().cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testPageHeadAndBodyIdsMatchAtTheSameLogicalRoot() {
        val java = pageFile(body = "add(new Label(\"title\"), new Label(\"body\"));")
        companion(html = "<html><head><title wicket:id=\"title\"></title></head><body><span wicket:id=\"body\"></span></body></html>")
        matched(analyze(java), 2)
    }

    fun testDerivedWicketHeadIsMergedBeforeTheBodyExtension() {
        page("BasePage", body = "add(new Label(\"baseTitle\"));")
        val base = companion("BasePage", "<html><head><title wicket:id=\"baseTitle\"></title></head><body><wicket:child/></body></html>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"script\"), new Label(\"body\"));")
        val child = companion("DetailPage", "<wicket:head><script wicket:id=\"script\"></script></wicket:head><wicket:extend><span wicket:id=\"body\"></span></wicket:extend>")
        val report = analyze(java)
        matched(report, 3)
        assertEquals(base.virtualFile.path, report.nodes.single { it.wicketId == "baseTitle" }.markupLocation!!.file)
        assertEquals(child.virtualFile.path, report.nodes.single { it.wicketId == "script" }.markupLocation!!.file)
        val header = report.markup!!.elements.single { it.tagName == "wicket:head" }
        assertEquals("head", report.markup.elements.single { it.nodeId == header.parentElementId }.tagName)
    }

    fun testHeaderContributionWithoutAnExplicitBaseHeadNeedsNoSyntheticLocations() {
        page("BasePage")
        companion("BasePage", "<html><body><wicket:child/></body></html>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:head><title wicket:id=\"title\"></title></wicket:head><wicket:extend/>")
        val report = analyze(java)
        matched(report, 1)
        assertTrue(report.markup!!.elements.none { it.tagName == "head" })
    }

    fun testMultipleInheritanceLevelsKeepAllHeaderContributions() {
        page("BasePage", body = "add(new Label(\"base\"));")
        companion("BasePage", "<html><head><title wicket:id=\"base\"></title></head><body><wicket:child/></body></html>")
        page("MiddlePage", "BasePage", "add(new Label(\"middle\"));")
        companion("MiddlePage", "<wicket:head><script wicket:id=\"middle\"></script></wicket:head><wicket:extend><wicket:child/></wicket:extend>")
        val java = page("DetailPage", "MiddlePage", "add(new Label(\"leaf\"));")
        companion("DetailPage", "<wicket:head><script wicket:id=\"leaf\"></script></wicket:head><wicket:extend/>")
        val report = analyze(java)
        matched(report, 3)
        assertEquals(3, report.markup!!.sourceFiles.size)
        assertEquals(WicketContextExporter().export(report), WicketContextExporter().export(analyze(java)))
    }

    fun testPlainDerivedHeadIsPreviewRatherThanAContribution() {
        page("BasePage")
        companion("BasePage", "<html><head></head><body><wicket:child/></body></html>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"body\"));")
        companion("DetailPage", "<html><head><title wicket:id=\"preview\"></title></head><body><wicket:extend><span wicket:id=\"body\"></span></wicket:extend></body></html>")
        val report = analyze(java)
        matched(report, 1)
        assertFalse(report.markup!!.elements.single { it.wicketId == "preview" }.participatesInComponentTree)
    }

    fun testPanelAndFormPanelHeadersBelongToTheirComponentRatherThanThePage() {
        for ((index, parent) in listOf("org.apache.wicket.markup.html.panel.Panel", "org.apache.wicket.markup.html.form.FormComponentPanel<String>").withIndex()) {
            val java = page("Input$index", parent, "super(id); add(new Label(\"title\"), new Label(\"body\"));", "String id")
            companion("Input$index", "<html><head><title wicket:id=\"preview\"></title></head><body><wicket:head><script wicket:id=\"title\"></script></wicket:head><wicket:panel><span wicket:id=\"body\"></span></wicket:panel></body></html>")
            matched(analyze(java), 2)
        }
    }

    fun testStaticHeadersDoNotInventComponents() {
        val java = pageFile()
        companion(html = "<html><head><meta charset=\"utf-8\"/><link href=\"style.css\"/><script src=\"app.js\"></script></head><body><span wicket:id=\"title\"></span></body></html>")
        matched(analyze(java), 1)
    }

    fun testDuplicateHeaderAndBodyIdsStayUnresolved() {
        val java = pageFile()
        companion(html = "<html><head><title wicket:id=\"title\"></title></head><body><span wicket:id=\"title\"></span></body></html>")
        unresolved(analyze(java))
    }

    fun testMisplacedAttributedAndMultipleHeadersStayUnresolved() {
        for ((index, markup) in listOf(
            "<wicket:extend><wicket:head><title wicket:id=\"title\"></title></wicket:head></wicket:extend>",
            "<wicket:head wicket:scope=\"custom\"><title wicket:id=\"title\"></title></wicket:head><wicket:extend/>",
            "<wicket:head/><wicket:head/><wicket:extend/>",
            "<wicket:extend/><wicket:head><title wicket:id=\"title\"></title></wicket:head>"
        ).withIndex()) {
            page("Base$index")
            companion("Base$index", "<html><body><wicket:child/></body></html>")
            val java = page("Child$index", "Base$index", "add(new Label(\"title\"));")
            companion("Child$index", markup)
            unresolved(analyze(java))
        }
    }

    fun testHeaderIdReferencesUseTheirActualSourceFile() {
        val java = pageFile()
        val html = companion(html = "<html><head><title wicket:id=\"title\"></title></head><body></body></html>")
        assertEquals(html.idValue("title"), java.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(java.idValue("title"), html.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
    }

    fun testOpeningAVariantSelectsItWhileJavaStillUsesDefaultMarkup() {
        val java = pageFile()
        val default = companion()
        val variant = companion("ExamplePage_fr", "<div><span wicket:id=\"title\"></span></div>")
        val target = WicketEditorTargetResolver().resolve(project, variant.virtualFile.url, 0)
        assertEquals("_fr", target.markupSuffix)
        assertEquals(variant.virtualFile.path, WicketEditorAnalysis.pages(variant).single().report.markupPath)
        assertEquals(default.virtualFile.path, WicketEditorAnalysis.pages(java).single().report.markupPath)
        assertEquals(listOf(variant.virtualFile.path), WicketPageResolver().resolve(java.classes.single()).relatedVariantPaths)
    }

    fun testExactStyledVariantAndAncestorVariantAreComposed() {
        page("BasePage")
        val base = companion("BasePage_mobile_dark_fr_CA", "<html><head></head><body><wicket:child/></body></html>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        val variant = companion("DetailPage_mobile_dark_fr_CA", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = WicketEditorAnalysis.pages(variant).single().report
        matched(report, 1)
        assertEquals(listOf(base.virtualFile.path, variant.virtualFile.path), report.markup!!.sourceFiles)
        assertEquals("_mobile_dark_fr_CA", report.markupSuffix)
    }

    fun testVariantFallsBackToDefaultAncestorOnlyWithoutCompetingVariants() {
        page("BasePage")
        val base = companion("BasePage", "<html><body><wicket:child/></body></html>")
        page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        val variant = companion("DetailPage_fr_CA", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        matched(WicketEditorAnalysis.pages(variant).single().report, 1)
        companion("BasePage_fr", "<html><body><wicket:child/></body></html>")
        unresolved(WicketEditorAnalysis.pages(variant).single().report)
        assertTrue(base.isValid)
    }

    fun testVariantWithoutAnyDefaultCompanionCanBeAnalyzedExplicitly() {
        val java = pageFile()
        val variant = companion("ExamplePage_dark", "<span wicket:id=\"title\"></span>")
        matched(WicketEditorAnalysis.pages(variant).single().report, 1)
        assertNull(WicketEditorAnalysis.pages(java).single().report.markup)
    }

    fun testClassNamesWithUnderscoresAreNotMistakenForVariants() {
        page("Example_Page", body = "add(new Label(\"title\"));")
        val html = companion("Example_Page")
        assertEquals("", WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0).markupSuffix)
    }

    fun testAClassNameAndVariantCollisionRequiresAnExplicitChoice() {
        pageFile()
        page("ExamplePage_fr", body = "add(new Label(\"title\"));")
        val html = companion("ExamplePage_fr")
        try { WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0); fail("Ambiguous filename") }
        catch (_: AnalysisUnavailable) { }
    }

    fun testVariantReferencesResolveToJavaWithoutChangingTheDefaultContext() {
        val java = pageFile()
        val default = companion()
        val variant = companion("ExamplePage_fr")
        assertEquals(java.idValue("title"), variant.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(default.idValue("title"), java.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
    }

    fun testPairedRenameDoesNotBreakOtherVariantsSharingJava() {
        val java = pageFile()
        val default = companion()
        val variant = companion("ExamplePage_fr")
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(default, default.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(variant, variant.text.indexOf("\"title\"") + 1))
    }

    fun testReanalysisKeepsTheExplicitVariantAndTracksBothMarkupFiles() {
        page("BasePage")
        val base = companion("BasePage_fr", "<html><head></head><body><wicket:child/></body></html>")
        page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        val variant = companion("DetailPage_fr", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val service = project.service<WicketLensService>()
        service.setEditorFollowing(true)
        val disposable = Disposer.newDisposable(testRootDisposable)
        FileEditorManager.getInstance(project).openTextEditor(com.intellij.openapi.fileEditor.OpenFileDescriptor(project, variant.virtualFile), true)
        service.trackEditor(disposable)
        await(service)
        assertEquals("_fr", service.state.prepared!!.report.markupSuffix)
        assertTrue(service.state.prepared!!.version.sources.any { it.path == base.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(base)!!.insertString(0, "<!-- edited header -->") }
        assertTrue(service.state.stale)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await(service)
        assertEquals(variant.virtualFile.path, service.state.prepared!!.report.markupPath)
        assertEquals("_fr", service.state.prepared!!.report.markupSuffix)
        Disposer.dispose(disposable)
    }

    fun testCustomMarkupProviderIsNotSilentlyTreatedAsConventionalHtml() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
                @Override public org.apache.wicket.markup.MarkupType getMarkupType() { return org.apache.wicket.markup.MarkupType.HTML; }
            }
        """.trimIndent()) as PsiJavaFile
        companion()
        assertEquals(MarkupResolutionStatus.UNSUPPORTED, WicketPageResolver().resolve(java.classes.single()).status)
        unresolved(analyze(java))
    }

    fun testVariantSuffixCannotEscapeTheResourcePackage() {
        val java = pageFile()
        companion()
        assertEquals(MarkupResolutionStatus.UNSUPPORTED, WicketPageResolver().resolve(java.classes.single(), "_../../other").status)
    }

    fun testHeaderMarkupCanCreateItsJavaLabelWithTheNativeFix() {
        val java = pageFile(body = "")
        val html = companion(html = "<html><head><title wicket:id=\"pageTitle\">Page heading</title></head><body></body></html>")
        val descriptor = problems(html).single()
        val fix = descriptor.fixes!!.filterIsInstance<com.intellij.modcommand.ModCommandQuickFix>().single { it.name.startsWith("Create Java Label") }
        val command = fix.perform(project, descriptor)
        WriteCommandAction.runWriteCommandAction(project) {
            com.intellij.modcommand.ModCommandExecutor.getInstance().executeInBatch(com.intellij.modcommand.ActionContext.from(descriptor), command)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        matched(analyze(java), 1)
    }

    fun testVariantInspectionActionsDoNotChangeSharedJava() {
        val java = pageFile(body = "add(new Label(\"titel\"));")
        companion()
        val variant = companion("ExamplePage_fr", "<span wicket:id=\"title\"></span>")
        assertTrue(problems(java).flatMap { it.fixes.orEmpty().toList() }.none { it.name.startsWith("Change Wicket ID") })
        val fixes = problems(variant).flatMap { it.fixes.orEmpty().toList() }
        assertTrue(fixes.none { it.name.startsWith("Create Java") })
        assertTrue(fixes.any { it.name == "Change Wicket ID to 'titel'" })
    }

    fun testVariantGutterProblemCarriesTheSelectedTemplateContext() {
        val java = pageFile(body = "")
        companion()
        val variant = companion("ExamplePage_fr", "<span wicket:id=\"title\"></span>")
        val problem = dev.wicketlens.inspection.WicketIdProblems.forFile(variant).single()
        assertEquals("_fr", problem.markupSuffix)
        val service = project.service<WicketLensService>()
        service.setEditorFollowing(false)
        service.analyze(problem.sourceUrl, problem.classOffset, problem.markupSuffix); await(service)
        assertEquals(variant.virtualFile.path, service.state.prepared!!.report.markupPath)
        assertEquals(java.virtualFile.url, problem.sourceUrl)
    }

    fun testManualJavaSwitchReturnsToDefaultAfterFollowingAVariant() {
        val java = pageFile()
        val default = companion()
        val variant = companion("ExamplePage_fr")
        val service = project.service<WicketLensService>()
        service.setEditorFollowing(true)
        val disposable = Disposer.newDisposable(testRootDisposable)
        val manager = FileEditorManager.getInstance(project)
        manager.openTextEditor(com.intellij.openapi.fileEditor.OpenFileDescriptor(project, variant.virtualFile), true)
        service.trackEditor(disposable); await(service)
        assertEquals("_fr", service.state.prepared!!.report.markupSuffix)
        manager.openTextEditor(com.intellij.openapi.fileEditor.OpenFileDescriptor(project, java.virtualFile), true)
        PlatformTestUtil.waitWithEventsDispatching("Java should restore default markup", {
            !service.state.running && service.state.prepared?.report?.markupPath == default.virtualFile.path
        }, 15)
        assertEquals("", service.state.prepared!!.report.markupSuffix)
        Disposer.dispose(disposable)
    }

    private fun problems(file: PsiFile): List<com.intellij.codeInspection.ProblemDescriptor> {
        val holder = com.intellij.codeInspection.ProblemsHolder(com.intellij.codeInspection.InspectionManager.getInstance(project), file, false)
        dev.wicketlens.inspection.WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
        return holder.results
    }

    fun testDemoDefaultAndFrenchLayoutsEachMatchThreeComponents() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("HeaderBasePage", "HeaderDetailPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
            for (suffix in listOf("", "_fr")) companion("$name$suffix", java.nio.file.Files.readString(root.resolve("main/resources/example/$name$suffix.html")))
        }
        val source = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/HeaderDetailPage.java")) as PsiJavaFile
        matched(analyze(source), 3)
        val html = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/HeaderDetailPage_fr.html"))!!
        val report = WicketEditorAnalysis.pages(html).single().report
        matched(report, 3)
        assertEquals("_fr", report.markupSuffix)
        assertTrue(report.markup!!.sourceFiles.all { it.endsWith("_fr.html") })
    }

    private fun page(name: String, parent: String = "org.apache.wicket.markup.html.WebPage", body: String = "", parameters: String = "") =
        myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            public class $name extends $parent { public $name($parameters) { $body } }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(java: PsiJavaFile) = WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun matched(report: PageReport, count: Int) {
        assertEquals(report.toString(), count, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.toString(), report.markup!!.scopeComplete)
    }
    private fun unresolved(report: PageReport) {
        assertTrue(report.toString(), report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }
    private fun await(service: WicketLensService) {
        PlatformTestUtil.waitWithEventsDispatching("Variant report", { !service.state.running && service.state.prepared != null }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
}
