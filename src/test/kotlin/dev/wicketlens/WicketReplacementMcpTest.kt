package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketReplacementMcpTest : WicketProjectPsiTestCase() {
    private fun fixture(): PsiJavaFile {
        myFixture.addFileToProject("src/main/java/example/Host.java", """
            package example; public class Host extends org.apache.wicket.markup.html.panel.Panel {
                private final Dialog editor;
                private java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory;
                public Host(String id) { super(id); editor = new Dialog("editor"); add(editor); }
                public Host setFields(java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory) { this.factory=factory; return this; }
                public void edit(org.apache.wicket.model.IModel<String> model) { org.apache.wicket.Component fields = factory.apply(Dialog.CONTENT, model); editor.open(fields); }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Host.html", "<wicket:panel><div wicket:id='editor'></div></wicket:panel>")
        myFixture.addFileToProject("src/main/java/example/Dialog.java", """
            package example; public class Dialog extends org.apache.wicket.markup.html.panel.Panel {
                public static final String CONTENT = "body";
                private final org.apache.wicket.markup.html.form.Form<Void> form;
                public Dialog(String id) { super(id); form = new org.apache.wicket.markup.html.form.Form<>("form"); add(form); form.add(new org.apache.wicket.markup.html.WebMarkupContainer(CONTENT)); }
                public void open(org.apache.wicket.Component content) { form.addOrReplace(content); }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Dialog.html", "<wicket:panel><form wicket:id='form'><div wicket:id='body'></div></form></wicket:panel>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { Host host = new Host("host").setFields(Fields::new); add(host); }
                public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                    public Fields(String id, org.apache.wicket.model.IModel<String> model) { super(id, model); add(new org.apache.wicket.markup.html.basic.Label("name")); }
                }
            }
        """) as PsiJavaFile
        companion(html = "<div wicket:id='host'></div>")
        companion("ExamplePage\$Fields", "<wicket:panel><b wicket:id='name'></b></wicket:panel>")
        return java
    }
    private fun file(path: String) = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testDefaultGraphAndReadOnlyEventProjectionShareNativeFacts() {
        val java = fixture(); val all = listOf(java, file("src/main/java/example/Host.java"), file("src/main/java/example/Dialog.java"), file("src/main/resources/example/Dialog.html"))
        val before = all.map { it.text to it.modificationStamp }
        val query = WicketPageAnalysisRequest()
        val plain = JsonParser.parseString(query.render(query.capture(project, "example.ExamplePage"))).asJsonObject
        assertFalse(plain.has("navigationContexts")); assertFalse(plain["analysis"].asJsonObject.has("lifecycle"))
        val snapshot = query.capture(project, "example.ExamplePage", includeNavigationContexts = true)
        val event = snapshot.navigationContexts!!.contexts.single { it.relationship == "EVENT_TEMPLATE" }
        assertEquals("host:editor:form:body", event.report.lifecycle!!.componentPath)
        val json = JsonParser.parseString(query.render(snapshot)).asJsonObject
        val exported = json["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString == "EVENT_TEMPLATE" }.asJsonObject
        assertEquals("EVENT_TEMPLATE", exported["lifecycle"].asJsonObject["phase"].asString)
        assertEquals("unknown", exported["lifecycle"].asJsonObject["runtimePresence"].asString)
        assertTrue(exported["bindings"].asJsonArray.all { binding ->
            binding.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString == "INFERRED"
        })
        val native = WicketEditorAnalysis.navigationPages(java).single { it.report.lifecycle != null }.report
        assertEquals(WicketContextExporter().export(event.report), WicketContextExporter().export(native))
        assertEquals(before, all.map { it.text to it.modificationStamp }); query.requireCurrent(project, snapshot)
        try { query.render(snapshot, WicketMcpReport.CONTEXT, "host:editor:form:body:name"); fail("Event paths must not become initial-tree paths") }
        catch (e: PageAnalysisFailure) { assertTrue(e.code, e.code in setOf("COMPONENT_NOT_FOUND", "UNRESOLVED_COMPONENT_PATH")) }
    }
    fun testConstructorReferenceAndTheCoreReplacementSlotNavigateBothWaysWithoutRename() {
        val java = fixture()
        val reference = PsiTreeUtil.findChildrenOfType(java, PsiMethodReferenceExpression::class.java).single()
        val attribute = PsiTreeUtil.findChildrenOfType(file("src/main/resources/example/Dialog.html"), XmlAttributeValue::class.java).single { it.value == "body" }
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val offset = reference.textRange.endOffset - 2
        val handler = WicketReplacementDeclarationHandler()
        assertTrue(handler.getGotoDeclarationTargets(java.findElementAt(offset), offset, myFixture.editor)!!.any { it == attribute })
        assertNull(handler.getGotoDeclarationTargets(java.findElementAt(reference.textOffset), reference.textOffset, myFixture.editor))
        assertTrue(attribute.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element == reference })
        assertNull(WicketIdRenameTarget.at(java, reference.textOffset)); assertNull(WicketIdRenameTarget.at(attribute.containingFile, attribute.textOffset + 1))
        val host = PsiTreeUtil.findChildrenOfType(java, PsiLiteralExpression::class.java).single { it.value == "host" }
        assertTrue(host.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).isNotEmpty())
    }
    fun testUnsavedReplacementSourceChangesInvalidateTheEventSnapshot() {
        fixture(); val query = WicketPageAnalysisRequest(); val before = query.capture(project, "example.ExamplePage", includeNavigationContexts = true)
        val dialog = file("src/main/java/example/Dialog.java")
        assertTrue(before.version.sources.any { it.path == dialog.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(dialog)!!.setText(dialog.text.replace("form.addOrReplace(content)", "form.addOrReplace(content); addOrReplace(content)")) }
        try { query.requireCurrent(project, before); fail("Expected stale event snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val after = query.capture(project, "example.ExamplePage", includeNavigationContexts = true)
        assertTrue(after.navigationContexts!!.contexts.none { it.relationship == "EVENT_TEMPLATE" })
        assertTrue(after.navigationContexts.lifecycleDiagnostics.isNotEmpty())
    }
    fun testEventProjectionRemainsSubjectToTheWholeResponseLimit() {
        fixture(); val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.ExamplePage", includeNavigationContexts = true)
        val navigation = snapshot.navigationContexts!!
        val event = navigation.contexts.single { it.relationship == "EVENT_TEMPLATE" }
        val oversized = event.copy(report = event.report.copy(warnings = listOf(dev.wicketlens.model.Diagnostic(
            dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS, "x".repeat(600_000)))))
        try { query.render(snapshot.copy(navigationContexts = navigation.copy(contexts = listOf(oversized)))); fail("Expected response bound") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }

}
