package dev.wicketlens

import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.model.*

class WicketReplacementContextsTest : WicketProjectPsiTestCase() {
    private fun setup(fieldsBody: String = "super(id, model); add(new org.apache.wicket.markup.html.basic.Label(\"name\"));",
        pageBody: String = "Host host = new Host(\"host\").setFields(Fields::new); add(host);",
        hostExtra: String = "", eventBody: String = "org.apache.wicket.Component fields = factory.apply(Overlay.CONTENT, model); editor.open(fields);",
        overlayBody: String = "form.addOrReplace(content);", overlayExtra: String = "") : PsiJavaFile {
        myFixture.addFileToProject("src/main/java/example/Host.java", """
            package example;
            public class Host extends org.apache.wicket.markup.html.panel.Panel {
                private final Overlay editor;
                private java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>, ? extends org.apache.wicket.Component> factory;
                public Host(String id) { super(id); editor = new Overlay("editor"); add(editor); }
                public Host setFields(java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> value) { factory = value; return this; }
                public void edit(org.apache.wicket.model.IModel<String> model) { $eventBody }
                $hostExtra
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Host.html", "<wicket:panel><div wicket:id='editor'></div></wicket:panel>")
        myFixture.addFileToProject("src/main/java/example/Overlay.java", """
            package example;
            public class Overlay extends org.apache.wicket.markup.html.panel.Panel {
                public static final String CONTENT = "content";
                private final org.apache.wicket.markup.html.WebMarkupContainer window;
                private final org.apache.wicket.markup.html.form.Form<Void> form;
                public Overlay(String id) { super(id); window = new org.apache.wicket.markup.html.WebMarkupContainer("window"); form = new org.apache.wicket.markup.html.form.Form<>("form"); window.add(form); add(window); form.add(new org.apache.wicket.markup.html.WebMarkupContainer(CONTENT)); }
                public Overlay open(org.apache.wicket.Component content) { setContent(content); return this; }
                public Overlay setContent(org.apache.wicket.Component content) { if (!CONTENT.equals(content.getId())) { throw new IllegalArgumentException(); } $overlayBody return this; }
                public void close() { form.replace(new org.apache.wicket.markup.html.WebMarkupContainer(CONTENT)); }
                $overlayExtra
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Overlay.html", "<wicket:panel><div wicket:id='window'><form wicket:id='form'><div wicket:id='content'></div></form></div></wicket:panel>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { $pageBody }
                public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                    public Fields(String id, org.apache.wicket.model.IModel<String> model) { $fieldsBody }
                }
            }
        """) as PsiJavaFile
        companion(html = "<div wicket:id='host'></div>")
        companion("ExamplePage\$Fields", "<wicket:panel><b wicket:id='name'></b></wicket:panel>")
        return java
    }
    private fun contexts(java: PsiJavaFile, limits: WicketReplacementContexts.Limits = WicketReplacementContexts.Limits()): WicketReplacementContexts.Result {
        val type = java.classes.single()
        return WicketReplacementContexts(limits).analyze(type, WicketPageAnalyzer().analyze(type)!!)
    }
    private fun withheld(java: PsiJavaFile) {
        val result = contexts(java)
        assertTrue(result.toString(), result.reports.isEmpty())
        assertTrue(result.toString(), result.diagnostics.isNotEmpty())
    }
    fun testSourceConstructorFactoryProjectsAReplacementSiteWithoutChangingTheInitialTree() {
        val java = setup(); val primary = WicketPageAnalyzer().analyze(java.classes.single())!!
        val result = contexts(java); assertTrue(result.diagnostics.toString(), result.complete)
        val event = result.reports.single()
        assertEquals("host:editor:window:form:content", event.lifecycle!!.componentPath)
        assertEquals(6, event.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(event.matching.findings.all { it.confidence == Confidence.INFERRED })
        assertTrue(event.nodes.filter { it.wicketId != null }.all { it.template && !it.idRenameSafe })
        assertFalse(event.scopeComplete)
        assertNull(primary.lifecycle)
        assertTrue(primary.nodes.none { it.wicketId == "name" })
        assertTrue(result.sources.any { it.endsWith("ExamplePage\$Fields.html") })
        val navigation = WicketNavigationContextsAnalyzer().analyze(java.classes.single(), primary)
        assertEquals(1, navigation.contexts.count { it.relationship == "EVENT_TEMPLATE" })
    }
    fun testWrongConstructorIdCannotBecomeAReplacementTemplate() = withheld(setup(fieldsBody = "super(\"wrong\", model); add(new org.apache.wicket.markup.html.basic.Label(\"name\"));"))
    fun testTwoRegistrationsForOneHostAreNotGuessed() = withheld(setup(pageBody = "Host host = new Host(\"host\").setFields(Fields::new).setFields(Fields::new); add(host);"))
    fun testFactoryOverwriteOutsideItsSetterIsRejected() = withheld(setup(hostExtra = "private void reset() { factory = null; }"))
    fun testReassignedHostDoesNotKeepItsFirstRoute() = withheld(setup(pageBody = "Host host = new Host(\"host\").setFields(Fields::new); host = new Host(\"other\"); add(host);"))
    fun testDuplicateHostAttachmentDoesNotProduceAUniquePath() = withheld(setup(pageBody = "Host host = new Host(\"host\").setFields(Fields::new); add(host); add(host);"))
    fun testKnownHostMutationWithholdsTheTemplate() = withheld(setup(pageBody = "Host host = new Host(\"host\").setFields(Fields::new); add(host); host.removeAll();"))
    fun testKnownPageMutationWithholdsTheTemplate() = withheld(setup(pageBody = "Host host = new Host(\"host\").setFields(Fields::new); add(host); removeAll();"))
    fun testFactoryResultWithTwoConsumersIsNotProjected() = withheld(setup(eventBody = "org.apache.wicket.Component fields = factory.apply(Overlay.CONTENT, model); editor.open(fields); editor.open(fields);"))
    fun testUnknownFactoryIdIsNotProjected() = withheld(setup(eventBody = "org.apache.wicket.Component fields = factory.apply(System.getProperty(\"id\"), model); editor.open(fields);"))
    fun testConditionalFactoryInvocationIsNotAnUnconditionalSite() = withheld(setup(eventBody = "if (model != null) { org.apache.wicket.Component fields = factory.apply(Overlay.CONTENT, model); editor.open(fields); }"))
    fun testContentEscapeIsNotAnOwnedReplacement() = withheld(setup(overlayBody = "remember(content); form.addOrReplace(content);", overlayExtra = "private void remember(org.apache.wicket.Component value) {}"))
    fun testTwoReplacementDestinationsAreAmbiguous() = withheld(setup(overlayBody = "form.addOrReplace(content); window.addOrReplace(content);"))

    private fun change(path: String, from: String, to: String) {
        val file = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(from, to))
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testKnownConstructorMutationInvalidatesTheOwnedPath() {
        val java = setup()
        change("src/main/java/example/Overlay.java", "add(window);", "add(window); removeAll();")
        withheld(java)
    }
    fun testOneChildWithTwoConstructorParentsIsAmbiguous() {
        val java = setup()
        change("src/main/java/example/Overlay.java", "add(window);", "add(window); add(form);")
        withheld(java)
    }
    fun testReceiverOverrideCannotRedirectTheReplacement() {
        val java = setup()
        change("src/main/java/example/Host.java", "new Overlay(\"editor\")", "new Overlay(\"editor\") { @Override public Overlay open(org.apache.wicket.Component content) { return this; } }")
        withheld(java)
    }
    fun testUnresolvedMarkupWrapperDoesNotBecomeAProvenSlot() {
        val java = setup()
        change("src/main/resources/example/Overlay.html", "<div wicket:id='content'></div>", "<wicket:unknown><div wicket:id='content'></div></wicket:unknown>")
        withheld(java)
    }
    fun testUnreviewedFluentReceiverReturnIsNotTheOriginalAllocation() {
        val java = setup(hostExtra = "public Host swap() { return new Host(\"other\"); }")
        change("src/main/java/example/ExamplePage.java", "new Host(\"host\").setFields", "new Host(\"host\").swap().setFields")
        withheld(java)
    }
    fun testForeignSetterFieldDoesNotRegisterOnThisHost() {
        val java = setup(hostExtra = "private Host other;")
        change("src/main/java/example/Host.java", "factory = value;", "other.factory = value;")
        assertTrue(contexts(java).reports.isEmpty())
    }
    fun testForeignReplacementFieldDoesNotUseThisDialogsForm() {
        val java = setup(overlayExtra = "private Overlay other;")
        change("src/main/java/example/Overlay.java", "form.addOrReplace(content);", "other.form.addOrReplace(content);")
        withheld(java)
    }
    fun testForeignFactoryInvocationIsNotTheRegisteredFactory() {
        val java = setup(hostExtra = "private Host other;")
        change("src/main/java/example/Host.java", "factory.apply(Overlay.CONTENT, model)", "other.factory.apply(Overlay.CONTENT, model)")
        withheld(java)
    }
    fun testStepBoundWithholdsAllEventTemplates() {
        val java = setup(); val result = contexts(java, WicketReplacementContexts.Limits(steps = 1))
        assertFalse(result.complete); assertTrue(result.reports.isEmpty())
        assertTrue(result.diagnostics.single().message.contains("bound"))
    }
    fun testCancellationPropagates() {
        val java = setup(); val type = java.classes.single(); val primary = WicketPageAnalyzer().analyze(type)!!
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {
            indicator.cancel(); WicketReplacementContexts().analyze(type, primary)
        }, indicator) }
    }
}
