package dev.wicketlens

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode

class WicketIdQuickFixTest : WicketProjectPsiTestCase() {
    fun testCorrectJavaIdToAnUnpairedMarkupSibling() {
        val java = pageFile(body = "add(new Label(\"titel\", \"Display\"));")
        val html = companion()
        apply(java, "titel", "Change Wicket ID to 'title'")
        assertTrue(java.text.contains("Label(\"title\", \"Display\")"))
        assertEquals("<html><body><span wicket:id=\"title\"></span></body></html>", html.text)
        matched(java, 1)
    }

    fun testCorrectMarkupIdLeavesJavaUntouched() {
        val java = pageFile()
        val html = companion(html = "<span wicket:id=\"titel\"></span>")
        val before = java.text
        apply(html, "titel", "Change Wicket ID to 'title'")
        assertEquals(before, java.text)
        assertTrue(html.text.contains("wicket:id=\"title\""))
        matched(java, 1)
    }

    fun testIdCorrectionsUseTheSameParentPath() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("titel")); add(form);
        """.trimIndent())
        companion(html = "<div><form wicket:id=\"form\"><span wicket:id=\"title\"></span></form><span wicket:id=\"outside\"></span></div>")
        val names = fixes(java, "titel").second.map { it.name }
        assertTrue(names.contains("Change Wicket ID to 'title'"))
        assertFalse(names.contains("Change Wicket ID to 'outside'"))
        apply(java, "titel", "Change Wicket ID to 'title'")
        matched(java, 2)
    }

    fun testCreateMissingMarkupAtThePageRoot() {
        val java = pageFile()
        val html = companion(html = "<html><body><p>Keep me</p></body></html>")
        apply(java, "title", "Create HTML counterpart")
        assertTrue(html.text.contains("<p>Keep me</p>"))
        matched(java, 1)
    }

    fun testCreateMarkupBelowTheMatchedFormRatherThanTheBody() {
        val java = formPage("form.add(new org.apache.wicket.markup.html.form.TextField<String>(\"name\"));")
        val html = companion(html = "<html><body><form wicket:id=\"form\"></form></body></html>")
        apply(java, "name", "Create HTML counterpart")
        assertTrue(html.text.contains("type=\"text\""))
        matched(java, 2)
        val report = WicketEditorAnalysis.pages(java).single().report
        val name = report.markup!!.elements.single { it.wicketId == "name" }
        assertEquals("form", report.markup.elements.single { it.nodeId == name.parentWicketElementId }.wicketId)
    }

    fun testCreateRootJavaLabelPreservesMarkupTextAndEscapesIt() {
        val java = pageFile(body = "")
        val html = companion(html = "<span wicket:id=\"title\">Say &quot;Hello&quot; &amp; goodbye</span>")
        apply(html, "title", "Create Java Label")
        assertTrue(java.text, java.text.contains("\"Say \\\"Hello\\\" & goodbye\""))
        matched(java, 1)
    }

    fun testCreateJavaConstructorWhenThePageUsesAnImplicitOne() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent()) as PsiJavaFile
        val html = companion()
        apply(html, "title", "Create Java Label")
        assertEquals(1, java.classes.single().constructors.size)
        assertTrue(java.classes.single().constructors.single().isConstructor)
        matched(java, 1)
    }

    fun testGenerateAFormThenAnInputUsingItsNamedReceiver() {
        val java = pageFile(body = "")
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"name\" type=\"text\"/></form>")
        apply(html, "form", "Create Java Form")
        assertTrue(java.text, java.text.contains("wicketForm"))
        apply(html, "name", "Create Java TextField")
        assertTrue(java.text, java.text.contains("wicketForm.add("))
        matched(java, 2)
    }

    fun testCreateJavaChildUsingAnExistingPrivateFinalField() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private final org.apache.wicket.markup.html.form.Form<Void> form = new org.apache.wicket.markup.html.form.Form<>("form");
                public ExamplePage() { add(form); }
            }
        """.trimIndent()) as PsiJavaFile
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"name\" type=\"text\"/></form>")
        apply(html, "name", "Create Java TextField")
        assertTrue(java.text, java.text.contains("this.form.add("))
        matched(java, 2)
    }

    fun testCreateChildInTheInitializeLocalScope() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                @Override protected void onInitialize() {
                    super.onInitialize();
                    org.apache.wicket.markup.html.form.Form<Void> form = new org.apache.wicket.markup.html.form.Form<>("form");
                    add(form);
                }
            }
        """.trimIndent()) as PsiJavaFile
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"name\" type=\"text\"/></form>")
        apply(html, "name", "Create Java TextField")
        assertTrue(java.text, java.classes.single().findMethodsByName("onInitialize", false).single().text.contains("form.add("))
        matched(java, 2)
    }

    fun testCrossFileMarkupPreviewDoesNotModifyPhysicalFiles() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        val before = java.text to html.text
        val (descriptor, list) = fixes(java, "title")
        val preview = (list.single { it.name.startsWith("Create HTML counterpart") } as ModCommandQuickFix).generatePreview(project, descriptor)
        assertTrue(preview.toString(), preview is IntentionPreviewInfo.CustomDiff)
        val diff = preview as IntentionPreviewInfo.CustomDiff
        assertEquals("ExamplePage.html", diff.fileName())
        assertTrue(diff.modifiedText(), diff.modifiedText().contains("wicket:id=\"title\""))
        assertEquals(before, java.text to html.text)
    }

    fun testCrossFileJavaPreviewShowsTheGeneratedSetupWithoutWritingIt() {
        val java = pageFile(body = "")
        val html = companion()
        val before = java.text to html.text
        val (descriptor, list) = fixes(html, "title")
        val preview = (list.single { it.name.startsWith("Create Java Label") } as ModCommandQuickFix).generatePreview(project, descriptor)
        assertTrue(preview.toString(), preview is IntentionPreviewInfo.CustomDiff)
        val diff = preview as IntentionPreviewInfo.CustomDiff
        assertEquals("ExamplePage.java", diff.fileName())
        assertTrue(diff.modifiedText(), diff.modifiedText().contains("Label(\"title\""))
        assertEquals(before, java.text to html.text)
    }

    fun testNativeIntentionDiscoveryPreviewAndUndo() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        val before = java.text to html.text
        myFixture.enableInspections(WicketUnpairedIdInspection())
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"title\"") + 2)
        val action = myFixture.filterAvailableIntentions("Create HTML counterpart for 'title'").single()
        val preview = myFixture.getIntentionPreviewText(action)
        assertTrue(preview.orEmpty(), preview?.contains("wicket:id=\"title\"") == true)
        myFixture.launchAction(action)
        PlatformTestUtil.waitWithEventsDispatching("Quick fix applied", { html.text.contains("wicket:id=\"title\"") }, 10)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        matched(java, 1)
        val oldDialog = TestDialogManager.setTestDialog(TestDialog.OK)
        try { myFixture.performEditorAction(IdeActions.ACTION_UNDO) } finally { TestDialogManager.setTestDialog(oldDialog) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, java.text to html.text)
    }

    fun testSavedFixRevalidatesWhenItsCounterpartAppears() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        val (descriptor, list) = fixes(java, "title")
        val fix = list.single { it.name.startsWith("Create HTML counterpart") } as ModCommandQuickFix
        edit(html, "<span wicket:id=\"title\"></span>")
        assertTrue(fix.perform(project, descriptor).isEmpty)
        matched(java, 1)
    }

    fun testSavedCorrectionCannotCreateACollisionAfterAnEdit() {
        val java = pageFile(body = "add(new Label(\"titel\"));")
        val html = companion()
        val (descriptor, list) = fixes(java, "titel")
        val fix = list.single { it.name == "Change Wicket ID to 'title'" } as ModCommandQuickFix
        edit(java, java.text.replace("add(new Label(\"titel\"));", "add(new Label(\"titel\"), new Label(\"title\"));"))
        assertTrue(fix.perform(project, descriptor).isEmpty)
        assertTrue(html.text.contains("wicket:id=\"title\""))
    }

    fun testPendingOppositeDocumentEditPreventsASavedFix() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        val (descriptor, list) = fixes(java, "title")
        val fix = list.single { it.name.startsWith("Create HTML counterpart") } as ModCommandQuickFix
        edit(html, "<span wicket:id=\"title\"></span>", commit = false)
        assertTrue(fix.perform(project, descriptor).isEmpty)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun testSharedFactoryLiteralCannotBeRetargeted() {
        val java = pageFile(body = "add(label());").let { file ->
            edit(file, file.text.replace(" } }", " } private Label label() { return new Label(\"titel\"); } }"))
            file
        }
        companion()
        val names = fixes(java, "titel").second.map { it.name }
        assertFalse(names.any { it.startsWith("Change Wicket ID") })
        assertTrue(names.any { it.startsWith("Create HTML counterpart") })
    }

    fun testBaseClassWithInheritorsOffersNoLocalEdit() {
        val java = pageFile()
        companion(html = "<html><body></body></html>")
        myFixture.addFileToProject("src/main/java/example/ChildPage.java", "package example; public class ChildPage extends ExamplePage {}")
        assertEmpty(fixes(java, "title").second)
    }

    fun testInheritedMarkupDoesNotReceiveASingleClassQuickFix() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", "package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}")
        companion("BasePage", "<html><body></body></html>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends BasePage {
                public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        assertEmpty(fixes(java, "title").second)
    }

    fun testAParentWithoutAStableJavaReceiverHasNoCreateChildFix() {
        pageFile(body = "add(new org.apache.wicket.markup.html.form.Form<Void>(\"form\"));")
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"name\" type=\"text\"/></form>")
        assertFalse(fixes(html, "name").second.any { it.name.startsWith("Create Java") })
    }

    fun testUnsupportedMarkupDoesNotInventAComponentClass() {
        pageFile(body = "")
        val html = companion(html = "<select wicket:id=\"choice\"></select>")
        assertEmpty(fixes(html, "choice").second)
    }

    fun testUnknownJavaComponentDoesNotInventATag() {
        myFixture.addFileToProject("src/main/java/example/Heading.java", """
            package example; public class Heading extends org.apache.wicket.markup.html.basic.Label {
                public Heading(String id) { super(id); }
            }
        """.trimIndent())
        val java = pageFile(body = "add(new Heading(\"title\"));")
        companion(html = "<html><body></body></html>")
        assertEmpty(fixes(java, "title").second)
    }

    fun testUncertainAndMalformedScopesOfferNoFixes() {
        val java = pageFile(body = "add(new Label(System.getProperty(\"id\"))); removeAll();")
        val html = companion()
        assertEmpty(problems(java))
        assertEmpty(problems(html))
        edit(java, java.text.replace("System.getProperty(\"id\")", "\"title\"").replace(" removeAll();", ""))
        edit(html, "<div wicket:id=\"missing\"")
        assertEmpty(problems(java))
        assertEmpty(problems(html))
    }

    fun testCompositeContainerAndChildFixesPreserveTheOuterPair() {
        val composite = myFixture.addFileToProject("src/main/java/example/Input.java", """
            package example; public class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                public Input(String id) { super(id); }
            }
        """.trimIndent())
        val inner = companion("Input", "<wicket:panel><div wicket:id=\"details\"><span wicket:id=\"caption\">Caption</span></div></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"title\"));")
        companion()
        apply(inner, "details", "Create Java WebMarkupContainer")
        apply(inner, "caption", "Create Java Label")
        matched(composite, 2)
        matched(page, 1)
        assertTrue(composite.text, composite.text.contains("wicketContainer.add("))
    }

    fun testOrdinaryPanelUsesOnlyItsPanelSectionForMarkupCreation() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePanel.java", """
            package example; public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePanel(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        companion("ExamplePanel", "<html><body><div wicket:id=\"preview\"></div><wicket:panel></wicket:panel></body></html>")
        apply(java, "title", "Create HTML counterpart")
        matched(java, 1)
        assertEquals(listOf("title"), WicketEditorAnalysis.pages(java).single().report.markup!!.elements
            .filter { it.participatesInComponentTree && it.hasWicketId }.map { it.wicketId })
    }

    fun testMissingMarkupParentIsCreatedBeforeItsChild() {
        val java = formPage("form.add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        companion(html = "<html><body></body></html>")
        assertFalse(fixes(java, "title").second.any { it.name.startsWith("Create HTML counterpart") })
        apply(java, "form", "Create HTML counterpart")
        apply(java, "title", "Create HTML counterpart")
        matched(java, 2)
    }

    fun testRepeatedIdsAreCreatedUnderTheirOwnJavaParents() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<Void> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<Void> right = new org.apache.wicket.markup.html.form.Form<>("right");
            add(left, right);
        """.trimIndent())
        val html = companion(html = "<div><form wicket:id=\"left\"><input wicket:id=\"name\"/></form><form wicket:id=\"right\"><input wicket:id=\"name\"/></form></div>")
        apply(html, "name", "Create Java TextField", 0)
        apply(html, "name", "Create Java TextField", 1)
        assertTrue(java.text, java.text.contains("left.add("))
        assertTrue(java.text, java.text.contains("right.add("))
        matched(java, 4)
    }

    fun testCheckboxAndButtonMarkupAndJavaCounterparts() {
        val java = formPage("")
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"enabled\" type=\"checkbox\"/><button wicket:id=\"save\" type=\"submit\">Save</button></form>")
        apply(html, "enabled", "Create Java CheckBox")
        apply(html, "save", "Create Java Button")
        matched(java, 3)
        edit(html, "<div><form wicket:id=\"form\"></form></div>")
        apply(java, "enabled", "Create HTML counterpart")
        apply(java, "save", "Create HTML counterpart")
        matched(java, 3)
    }

    fun testContainerNamesAndImportsDoNotCollideWithExistingCode() {
        myFixture.addFileToProject("src/main/java/example/Label.java", "package example; public class Label {}")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; import example.Label;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private Label display;
                public ExamplePage() { final int wicketForm = 1; }
            }
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\">Hello</span></form>")
        apply(html, "form", "Create Java Form")
        apply(html, "title", "Create Java Label")
        assertTrue(java.text, java.text.contains("import example.Label;"))
        assertTrue(java.text, java.text.contains("new org.apache.wicket.markup.html.basic.Label("))
        matched(java, 2)
    }

    fun testMarkupCreationEscapesTheJavaId() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        val id = "field\"&<>\\'"
        WriteCommandAction.runWriteCommandAction(project) { ElementManipulators.handleContentChange(java.idValue("title"), id) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        apply(java, id, "Create HTML counterpart")
        matched(java, 1)
        assertEquals(id, WicketEditorAnalysis.pages(java).single().report.markup!!.elements.single { it.hasWicketId }.wicketId)
        assertTrue(html.text, html.text.contains("&quot;"))
    }

    fun testJavaCreationEscapesTheMarkupIdAndIgnoresCommentsInTheLabelModel() {
        val java = pageFile(body = "")
        val html = companion(html = "<span wicket:id=\"field&amp;&quot;\"><!-- explanatory comment -->Say &quot;hi&quot;</span>")
        apply(html, "field&\"", "Create Java Label")
        matched(java, 1)
        assertFalse(java.text, java.text.contains("explanatory comment"))
        assertTrue(java.text, java.text.contains("Say \\\"hi\\\""))
    }

    fun testConstructorWithAnEarlyReturnOffersNoInsertionAfterIt() {
        pageFile(body = "return;")
        val html = companion()
        assertFalse(fixes(html, "title").second.any { it.name.startsWith("Create Java") })
    }

    fun testReadOnlyTargetsOfferNoCounterpartCreation() {
        val java = pageFile()
        val html = companion(html = "<html><body></body></html>")
        WriteCommandAction.runWriteCommandAction(project) { html.virtualFile.isWritable = false }
        try { assertEmpty(fixes(java, "title").second) }
        finally { WriteCommandAction.runWriteCommandAction(project) { html.virtualFile.isWritable = true } }
        edit(java, java.text.replace("add(new Label(\"title\"));", ""))
        edit(html, "<span wicket:id=\"title\"></span>")
        WriteCommandAction.runWriteCommandAction(project) { java.virtualFile.isWritable = false }
        try { assertEmpty(fixes(html, "title").second) }
        finally { WriteCommandAction.runWriteCommandAction(project) { java.virtualFile.isWritable = true } }
    }

    fun testSavedJavaCreationRechecksTheCurrentTagType() {
        val java = pageFile(body = "")
        val html = companion()
        val (descriptor, list) = fixes(html, "title")
        val fix = list.single { it.name.startsWith("Create Java Label") } as ModCommandQuickFix
        edit(html, "<select wicket:id=\"title\"></select>")
        assertTrue(fix.perform(project, descriptor).isEmpty)
        assertTrue(java.text, java.text.contains("public ExamplePage() {  }"))
    }

    fun testUnmatchedPreviewIdsHaveNoQuickFixes() {
        myFixture.addFileToProject("src/main/java/example/Input.java", """
            package example; public class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                public Input(String id) { super(id); }
            }
        """.trimIndent())
        pageFile(body = "add(new Input(\"title\"));")
        val html = companion(html = "<div wicket:id=\"title\"><span wicket:id=\"preview\"></span></div>")
        assertEmpty(problems(html))
    }

    fun testCannotCreateMarkupInsideAVoidElement() {
        val java = formPage("form.add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        companion(html = "<input wicket:id=\"form\"/>")
        assertFalse(fixes(java, "title").second.any { it.name.startsWith("Create HTML counterpart") })
    }

    fun testCorrectionDoesNotPairAnInputWithAnIncompatibleSpan() {
        val java = formPage("form.add(new org.apache.wicket.markup.html.form.TextField<String>(\"name\"));")
        val html = companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\">Title</span></form>")
        assertFalse(fixes(java, "name").second.any { it.name.startsWith("Change Wicket ID") })
        assertFalse(fixes(html, "title").second.any { it.name.startsWith("Change Wicket ID") })
    }

    fun testCorrectionDoesNotTurnAMarkupContainerWithChildrenIntoALabel() {
        val java = pageFile(body = "add(new Label(\"title\"));")
        companion(html = "<div wicket:id=\"details\"><span wicket:id=\"caption\">Caption</span></div>")
        assertFalse(fixes(java, "title").second.any { it.name.startsWith("Change Wicket ID") })
    }

    fun testGeneratedLocalsCannotEscapeTheCompositeContract() {
        val java = myFixture.addFileToProject("src/main/java/example/Input.java", """
            package example; public class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                public Input(String id) { super(id); }
            }
        """.trimIndent())
        val inner = companion("Input", "<wicket:panel><div wicket:id=\"details\"></div></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"title\"));")
        companion()
        apply(inner, "details", "Create Java WebMarkupContainer")
        myFixture.addFileToProject("src/main/java/example/Registry.java", """
            package example; public class Registry { public static void consume(org.apache.wicket.Component component) {} }
        """.trimIndent())
        edit(java, java.text.replace("this.add(wicketContainer);", "Registry.consume(wicketContainer); this.add(wicketContainer);"))
        assertTrue(WicketEditorAnalysis.pages(page).single().report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testDemoCanBeCorrectedUsingAllThreeActions() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        val source = myFixture.addFileToProject("src/main/java/example/QuickFixPage.java", java.nio.file.Files.readString(root.resolve("main/java/example/QuickFixPage.java")))
        val html = companion("QuickFixPage", java.nio.file.Files.readString(root.resolve("main/resources/example/QuickFixPage.html")))
        // Interactive demo repairs must not change the starting state of this fixture copy.
        WriteCommandAction.runWriteCommandAction(project) {
            PsiTreeUtil.findChildrenOfType(source, PsiLiteralExpression::class.java)
                .firstOrNull { it.value == "title" && WicketIdPsi.javaId(it) != null }
                ?.let { ElementManipulators.handleContentChange(it, "titel") }
            PsiTreeUtil.findChildrenOfType(source, PsiLiteralExpression::class.java)
                .filter { it.value == "note" && WicketIdPsi.javaId(it) != null }
                .forEach { PsiTreeUtil.getParentOfType(it, PsiExpressionStatement::class.java)?.delete() }
            PsiTreeUtil.findChildrenOfType(html, com.intellij.psi.xml.XmlTag::class.java)
                .filter { it.getAttributeValue("wicket:id") == "name" }.forEach { it.delete() }
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val initial = WicketEditorAnalysis.pages(source).single().report.matching!!.findings.groupingBy { it.code }.eachCount()
        assertEquals(mapOf(DiagnosticCode.MATCHED to 1, DiagnosticCode.JAVA_ONLY to 2, DiagnosticCode.MARKUP_ONLY to 2), initial)
        apply(source, "titel", "Change Wicket ID to 'title'")
        apply(source, "name", "Create HTML counterpart")
        apply(html, "note", "Create Java Label")
        matched(source, 4)
        assertEmpty(problems(source))
        assertEmpty(problems(html))
    }

    fun testGeneratedGenericArgumentsUseTheJdkTypesWhenProjectNamesCollide() {
        myFixture.addFileToProject("src/main/java/example/String.java", "package example; public class String {}")
        myFixture.addFileToProject("src/main/java/example/Void.java", "package example; public class Void {}")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private String existingString;
                private Void existingVoid;
                public ExamplePage() {}
            }
        """.trimIndent()) as PsiJavaFile
        val html = companion(html = "<form wicket:id=\"form\"><input wicket:id=\"name\"/></form>")
        apply(html, "form", "Create Java Form")
        apply(html, "name", "Create Java TextField")
        val arguments = PsiTreeUtil.findChildrenOfType(java, PsiNewExpression::class.java)
            .mapNotNull { (it.type as? PsiClassType)?.parameters?.singleOrNull()?.canonicalText }.toSet()
        assertEquals(setOf("java.lang.Void", "java.lang.String"), arguments)
        assertEquals("example.String", java.classes.single().findFieldByName("existingString", false)!!.type.canonicalText)
        assertEquals("example.Void", java.classes.single().findFieldByName("existingVoid", false)!!.type.canonicalText)
        matched(java, 2)
    }

    private fun formPage(body: String) = pageFile(body = """
        org.apache.wicket.markup.html.form.Form<Void> form = new org.apache.wicket.markup.html.form.Form<>("form");
        $body add(form);
    """.trimIndent())

    private fun problems(file: PsiFile): List<ProblemDescriptor> {
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
        return holder.results
    }

    private fun fixes(file: PsiFile, id: String, occurrence: Int = 0): Pair<ProblemDescriptor, List<LocalQuickFix>> {
        val value = if (file is PsiJavaFile) PsiTreeUtil.findChildrenOfType(file, PsiLiteralExpression::class.java)
            .filter { it.value == id && WicketIdPsi.javaId(it) != null }[occurrence]
        else PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java)
            .filter { it.name == "wicket:id" && it.displayValue == id }[occurrence].valueElement!!
        val descriptor = problems(file).single { it.psiElement!!.textRange == value.textRange }
        return descriptor to descriptor.fixes.orEmpty().filterIsInstance<LocalQuickFix>()
    }

    private fun apply(file: PsiFile, id: String, prefix: String, occurrence: Int = 0) {
        val (descriptor, list) = fixes(file, id, occurrence)
        val fix = list.single { it.name.startsWith(prefix) } as ModCommandQuickFix
        val command = fix.perform(project, descriptor)
        assertFalse(fix.name, command.isEmpty)
        WriteCommandAction.runWriteCommandAction(project) {
            ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(descriptor), command)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun matched(java: PsiFile, count: Int) {
        val report = WicketEditorAnalysis.pages(java).single().report
        assertEquals(report.toString(), count, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
    }
}
