package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketLayoutSetupTest : WicketProjectPsiTestCase() {
    private fun source(name: String, text: String): PsiJavaFile = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $text") as PsiJavaFile
    private fun decorated(body: String = "add(new org.apache.wicket.behavior.Behavior());", members: String = "") = source("Decorated", """
        public class Decorated extends org.apache.wicket.markup.html.WebMarkupContainer {
            public Decorated(String id) { super(id); $body }
            $members
        }
    """)
    private fun report(body: String, html: String = "<div wicket:id=\"card\"><span wicket:id=\"title\"></span></div>", members: String = ""): PageReport {
        val java = source("ExamplePage", """
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.basic.Label;
            public class ExamplePage extends WebPage { public ExamplePage() { $body } $members }
        """)
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matched(report: PageReport, vararg ids: String) {
        val found = report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }
        assertEquals(report.warnings.toString(), ids.toSet(), found.toSet())
        assertEquals(found.size, ids.size)
    }
    private fun unresolved(report: PageReport) = assertTrue(report.toString(), report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })

    fun testDecoratedContainerAndItsCallerChildrenKeepSeparateIdentityAndAttachments() {
        decorated()
        val report = report("Decorated card = new Decorated(\"card\"); card.add(new Label(\"title\")); add(card);")
        matched(report, "card", "title")
        assertEquals(report.nodes.single { it.wicketId == "card" }.nodeId, report.nodes.single { it.wicketId == "title" }.parentNodeId)
        assertTrue(report.javaSourceFiles.any { it.endsWith("/Decorated.java") })
        assertEquals(WicketContextExporter().export(report), WicketContextExporter().export(report.copy()))
    }

    fun testModelForwardingAndFluentSourceConfigurationPreserveTheCallerId() {
        source("Badge", """
            import org.apache.wicket.model.*;
            public class Badge extends org.apache.wicket.markup.html.basic.Label {
                private boolean outline;
                public Badge(String id, String text) { this(id, Model.of(text)); }
                public Badge(String id, IModel<?> model) { super(id, model); add(new org.apache.wicket.behavior.Behavior()); }
                public Badge outline(boolean outline) { this.outline = outline; return this; }
            }
        """)
        matched(report("add(new Badge(\"card\", \"caption\").outline(true));", "<span wicket:id=\"card\"></span>"), "card")
    }

    fun testInheritedConstructorDelegationAndMutableInitializationFieldsRunOnce() {
        decorated()
        source("Base", """
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.request.mapper.parameter.PageParameters;
            public class Base extends WebPage {
                private Decorated card;
                private final org.apache.wicket.markup.html.basic.Label title = new org.apache.wicket.markup.html.basic.Label("title");
                public Base() { this((PageParameters) null); }
                public Base(PageParameters parameters) { super(parameters); }
                @Override protected void onInitialize() { super.onInitialize(); setup(); }
                private void setup() { card = new Decorated("card"); card.add(title); add(card); }
            }
        """)
        val java = source("ExamplePage", "public class ExamplePage extends Base {}")
        companion(html = "<div wicket:id=\"card\"><span wicket:id=\"title\"></span></div>")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!, "card", "title")
    }

    fun testSelectedConcreteClassSuppliesAnInheritedFactoryHook() {
        decorated()
        source("Base", """
            public abstract class Base extends org.apache.wicket.markup.html.WebPage {
                protected abstract org.apache.wicket.Component make(String id);
                @Override protected void onInitialize() { super.onInitialize(); add(make("card")); }
            }
        """)
        val java = source("ExamplePage", "public class ExamplePage extends Base { @Override protected org.apache.wicket.Component make(String id) { return new Decorated(id); } }")
        companion(html = "<div wicket:id=\"card\"></div>")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, "card")
        assertTrue(report.warnings.any { "selected concrete class" in it.message })
    }

    fun testOpaquePanelContentsDoNotEraseAnIndependentSibling() {
        source("Box", "public class Box extends org.apache.wicket.markup.html.panel.Panel { public Box(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); } }")
        val report = report("add(new Box(\"card\"), new Label(\"title\"));", "<div wicket:id=\"card\"></div><span wicket:id=\"title\"></span>")
        matched(report, "card", "title")
        assertTrue(report.nodes.single { it.wicketId == "card" }.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertFalse(report.scopeComplete)
    }

    fun testRenderOnlyStaticResourceDoesNotExecuteOrPoisonConstruction() {
        decorated()
        matched(report("add(new Decorated(\"card\"));", "<div wicket:id=\"card\"></div>", """
            private static final org.apache.wicket.request.resource.CssResourceReference CSS =
                new org.apache.wicket.request.resource.CssResourceReference(ExamplePage.class, "styles.css");
            @Override public void renderHead(org.apache.wicket.markup.head.IHeaderResponse response) { super.renderHead(response); response.render(org.apache.wicket.markup.head.CssHeaderItem.forReference(CSS)); }
        """), "card")
    }

    fun testUnsafeSynchronousBehaviorBindingCannotAcquireMatches() {
        source("Unsafe", """
            public class Unsafe extends org.apache.wicket.behavior.Behavior {
                @Override public void bind(org.apache.wicket.Component component) { ((org.apache.wicket.MarkupContainer)component).removeAll(); }
            }
        """)
        decorated("add(new Unsafe());")
        val report = report("Decorated card = new Decorated(\"card\"); card.add(new Label(\"title\")); add(card);")
        unresolved(report)
        assertTrue(report.warnings.any { "binding" in it.message && it.location != null })
    }

    fun testAnUnknownRootEscapeStillInvalidatesAllAffectedAttachments() {
        decorated()
        source("External", "public class External { public static void mutate(org.apache.wicket.MarkupContainer root) { root.removeAll(); } }")
        unresolved(report("add(new Decorated(\"card\")); External.mutate(this);", "<div wicket:id=\"card\"></div>"))
    }

    fun testADeferredCapturedAliasMutationIsNotTreatedAsConfiguration() {
        decorated()
        unresolved(report("""
            Decorated card = new Decorated("card"); add(card);
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") { @Override public void onClick() { card.removeAll(); } });
        """, "<div wicket:id=\"card\"></div><a wicket:id=\"title\"></a>"))
    }

    fun testAComponentOwnedConstructorCannotMutateItsParentSilently() {
        source("Box", "public class Box extends org.apache.wicket.markup.html.panel.Panel { public Box(String id) { super(id); getParent().removeAll(); } }")
        unresolved(report("add(new Box(\"card\"), new Label(\"title\"));", "<div wicket:id=\"card\"></div><span wicket:id=\"title\"></span>"))
    }

    fun testAnOverriddenIdGetterIsNotAssumedToUseTheFrameworkId() {
        decorated(members = "@Override public String getId() { return \"different\"; }")
        unresolved(report("add(new Decorated(\"card\"));", "<div wicket:id=\"card\"></div>"))
    }

    fun testMutatingAnEarlierDelegationArgumentCannotKeepTheOriginalId() {
        source("Decorated", """
            public class Decorated extends org.apache.wicket.markup.html.basic.Label {
                public Decorated(String id) { this(id = "actual", id); }
                private Decorated(String ignored, String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); }
            }
        """)
        unresolved(report("add(new Decorated(\"card\"));", "<span wicket:id=\"card\"></span>"))
    }

    fun testADelegationCycleStaysBoundedAndUnresolved() {
        source("Decorated", """
            public class Decorated extends org.apache.wicket.markup.html.basic.Label {
                public Decorated(String id) { this(id, true); }
                private Decorated(String id, boolean other) { this(id); }
            }
        """)
        unresolved(report("add(new Decorated(\"card\"));", "<span wicket:id=\"card\"></span>"))
    }

    fun testUnsavedExternalConstructorEditInvalidatesEditorBindings() {
        val wrapper = decorated()
        val java = source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage() { add(new Decorated(\"card\")); } }")
        companion(html = "<div wicket:id=\"card\"></div>")
        assertNotNull(WicketIdPsi.pair(java.idValue("card")))
        val document = PsiDocumentManager.getInstance(project).getDocument(wrapper)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("super(id)", "super(\"different\")")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(WicketIdPsi.pair(java.idValue("card")))
        assertTrue(WicketEditorAnalysis.pages(java).single().report.javaSourceFiles.contains(wrapper.virtualFile.path))
    }

    fun testCanceledLayoutAnalysisDoesNotReturnPartialPromotedFacts() {
        decorated()
        val java = pageFile(body = "add(new Decorated(\"card\"));")
        val indicator = EmptyProgressIndicator()
        try {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketLayoutSetupAnalyzer().analyze(java.classes.single(), "page")
            }, indicator)
            fail("Cancellation must propagate")
        } catch (_: ProcessCanceledException) { }
    }

    fun testADeferredLocalAliasCannotHideCapturedRootMutation() {
        decorated()
        unresolved(report("""
            add(new Decorated("card"));
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") {
                @Override public void onClick() { org.apache.wicket.MarkupContainer alias = ExamplePage.this; alias.removeAll(); }
            });
        """, "<div wicket:id='card'></div><a wicket:id='title'></a>"))
    }

    fun testReattachingACapturedComponentToALocalCallbackContainerIsUnsafe() {
        decorated()
        unresolved(report("""
            Decorated card = new Decorated("card"); add(card);
            add(new org.apache.wicket.markup.html.list.ListView<String>("title", java.util.List.of("x")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) { item.add(card); }
            });
        """, "<div wicket:id='card'></div><div wicket:id='title'></div>"))
    }

    fun testCallbackFieldReassignmentDoesNotBecomeStableConstructionState() {
        decorated()
        unresolved(report("""
            card = new Decorated("card"); add(card);
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") {
                @Override public void onClick() { card = new Decorated("other"); }
            });
        """, "<div wicket:id='card'></div><a wicket:id='title'></a>", "private Decorated card;"))
    }

    fun testInlineComponentInitializationCannotRemoveCallerChildrenSilently() {
        decorated(members = "@Override protected void onInitialize() { super.onInitialize(); removeAll(); }")
        unresolved(report("Decorated card = new Decorated(\"card\"); card.add(new Label(\"title\")); add(card);"))
    }

    fun testAMetadataOrArrayCarrierCannotHideARootEscape() {
        decorated()
        source("External", "public class External { static void clear(Object[] values) { ((org.apache.wicket.MarkupContainer) values[0]).removeAll(); } }")
        unresolved(report("add(new Decorated(\"card\")); External.clear(new Object[] { this });", "<div wicket:id='card'></div>"))
    }

    fun testSourceDataConstructionCannotReachAStaticComponentReference() {
        decorated()
        source("DataValue", """
            public class DataValue {
                static org.apache.wicket.MarkupContainer root;
                public DataValue() { root.removeAll(); }
            }
        """)
        unresolved(report("add(new Decorated(\"card\")); new DataValue();", "<div wicket:id='card'></div>"))
    }

    fun testBoundaryFieldInitializationCannotReachAnEnclosingPage() {
        source("Box", """
            public class Box extends org.apache.wicket.markup.html.panel.Panel {
                private final Object value = getPage();
                public Box(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); }
            }
        """)
        unresolved(report("add(new Box(\"card\"), new Label(\"title\"));", "<div wicket:id='card'></div><span wicket:id='title'></span>"))
    }

    fun testAReadOnlySourceCallbackHelperMayInspectAnExistingComponent() {
        decorated()
        source("Reader", "public final class Reader { public static boolean inspect(org.apache.wicket.Component component) { return component.getId().equals(\"card\"); } }")
        matched(report("""
            Decorated card = new Decorated("card"); add(card);
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") { @Override public void onClick() { Reader.inspect(card); } });
        """, "<div wicket:id='card'></div><a wicket:id='title'></a>"), "card", "title")
    }

    fun testAReaderNamedMethodStillNeedsAGraphEffectProof() {
        decorated()
        source("Reader", "public final class Reader { public static boolean inspect(org.apache.wicket.MarkupContainer root) { root.removeAll(); return true; } }")
        unresolved(report("""
            add(new Decorated("card"));
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") { @Override public void onClick() { Reader.inspect(ExamplePage.this); } });
        """, "<div wicket:id='card'></div><a wicket:id='title'></a>"))
    }

    fun testStaticInitializationEffectsCannotHideBehindAReader() {
        decorated()
        source("Reader", "public final class Reader { static org.apache.wicket.MarkupContainer root; static { root.removeAll(); } public static String inspect(org.apache.wicket.Component c) { return c.getId(); } }")
        unresolved(report("""
            add(new Decorated("card"));
            add(new org.apache.wicket.markup.html.link.Link<Void>("title") { @Override public void onClick() { Reader.inspect(ExamplePage.this); } });
        """, "<div wicket:id='card'></div><a wicket:id='title'></a>"))
    }

    fun testRecursiveSourceCallsStayBoundedWithoutPromotedMatches() {
        decorated()
        val report = report("add(new Decorated(\"card\")); recurse();", "<div wicket:id='card'></div>", "private void recurse() { recurse(); }")
        unresolved(report)
        assertTrue(report.warnings.any { "Recursive" in it.message })
    }

    fun testBoundaryInitializationResolvesTheActualSourceOverride() {
        source("BaseBox", """
            public class BaseBox extends org.apache.wicket.markup.html.panel.Panel {
                public BaseBox(String id) { super(id); }
                @Override protected void onInitialize() { super.onInitialize(); setup(); }
                protected void setup() { }
            }
        """)
        source("Box", "public class Box extends BaseBox { public Box(String id) { super(id); } @Override protected void setup() { getPage().removeAll(); } }")
        unresolved(report("add(new Box(\"card\"), new Label(\"title\"));", "<div wicket:id='card'></div><span wicket:id='title'></span>"))
    }

    fun testAnOpaqueModelCannotCarryThePageIntoComponentConstruction() {
        source("Box", """
            public class Box extends org.apache.wicket.markup.html.panel.Panel {
                public Box(String id, org.apache.wicket.model.IModel<org.apache.wicket.MarkupContainer> model) {
                    super(id); model.getObject().removeAll();
                }
            }
        """)
        unresolved(report("add(new Box(\"card\", () -> this), new Label(\"title\"));", "<div wicket:id='card'></div><span wicket:id='title'></span>"))
    }

    fun testStaticIdsReuseConstantProvenanceDependenciesAndReadOnlyOrigins() {
        decorated()
        val prefix = source("Prefix", "public class Prefix { public static final String VALUE = \"ti\"; }")
        val ids = source("Ids", "public class Ids { public static final String TITLE = Prefix.VALUE + \"tle\"; }")
        val report = report("add(new Decorated(Ids.TITLE));", "<div wicket:id='title'></div>")
        matched(report, "title")
        val node = report.nodes.single { it.wicketId == "title" }
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        assertTrue(report.javaSourceFiles.containsAll(listOf(prefix.virtualFile.path, ids.virtualFile.path)))
        assertTrue(WicketContextExporter().export(report).contains("COMPUTED"))
    }

    fun testSourceLayoutBindingsEnablePairedEditingAfterAnOriginUseAudit() {
        decorated()
        report("add(new Decorated(\"card\"));", "<div wicket:id='card'></div>")
        val java = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java")
            .let { PsiManager.getInstance(project).findFile(it)!! }
        assertNotNull(WicketIdPsi.pair(java.idValue("card")))
        assertNotNull(dev.wicketlens.editor.WicketIdRenameTarget.at(java, java.text.indexOf("\"card\"")))
    }

    fun testFrameworkConfigurationThroughABaseAliasAuditsTheActualOverride() {
        decorated(members = """
            @Override public org.apache.wicket.Component setDefaultModel(org.apache.wicket.model.IModel<?> model) {
                getPage().removeAll(); return this;
            }
        """)
        unresolved(report("""
            org.apache.wicket.Component card = new Decorated("card");
            add(card); card.setDefaultModel(org.apache.wicket.model.Model.of("caption"));
        """, "<div wicket:id='card'></div>"))
    }

    fun testSafeFrameworkOverrideThroughABaseAliasRetainsTheBinding() {
        decorated(members = """
            @Override public org.apache.wicket.Component setMarkupId(String id) { return super.setMarkupId(id); }
        """)
        matched(report("""
            org.apache.wicket.Component card = new Decorated("card");
            add(card); card.setMarkupId("dom-card");
        """, "<div wicket:id='card'></div>"), "card")
    }
}
