package dev.wicketlens

import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.DiagnosticCode

class WicketOwnedFormTest : WicketProjectPsiTestCase() {
    private fun report(field: String) = run {
        myFixture.addFileToProject("src/main/java/example/Field.java", """
            package example; public class Field extends org.apache.wicket.markup.html.panel.Panel {
                $field
                public Field(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("inside")); }
            }
        """)
        val java = pageFile(body = "add(new Field(\"field\"));")
        companion(html = "<div wicket:id='field'></div>")
        WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    fun testEmptyMetadataTokenDoesNotStoreItsComponentTypeArgument() {
        val r = report("private static final org.apache.wicket.MetaDataKey<java.util.Set<org.apache.wicket.Component>> KEY = new org.apache.wicket.MetaDataKey<>() {};")
        assertEquals(r.warnings.toString(), 1, r.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(r.nodes.single { it.wicketId == "field" }.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testMetadataTokenOverridesDoNotBypassInitializationAudit() {
        val r = report("private static final org.apache.wicket.MetaDataKey<org.apache.wicket.Component> KEY = new org.apache.wicket.MetaDataKey<>() { @Override public int hashCode() { throw new IllegalStateException(); } };")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testRealStaticComponentCollectionsRemainUnresolved() {
        val r = report("private static final java.util.Set<org.apache.wicket.Component> COMPONENTS = new java.util.HashSet<>();")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    private fun lazy(body: String = "return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");", extra: String = "") = run {
        myFixture.addFileToProject("src/main/java/example/LazyField.java", """
            package example; public class LazyField extends org.apache.wicket.markup.html.panel.Panel {
                private org.apache.wicket.markup.html.form.TextField<String> input;
                private static final org.apache.wicket.MetaDataKey<java.util.Set<LazyField>> KEY = new org.apache.wicket.MetaDataKey<>() {};
                public LazyField(String id) { super(id); }
                public org.apache.wicket.markup.html.form.TextField<String> getInput() {
                    if (input == null) { input = newInput(); input.setOutputMarkupId(true); add(input); }
                    return input;
                }
                protected org.apache.wicket.markup.html.form.TextField<String> newInput() { $body }
                public LazyField required() { getInput().setRequired(true); return this; }
                @Override protected void onInitialize() { super.onInitialize(); getInput(); }
                $extra
            }
        """)
        val java = pageFile(body = "add(new LazyField(\"field\").required());")
        companion(html = "<div wicket:id='field'><span wicket:id='preview'></span></div>")
        WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    fun testLazyOwnedInputConfigurationPreservesTheParentBindingOnly() {
        val r = lazy()
        assertEquals(r.warnings.toString(), 1, r.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertEquals(listOf("field"), r.nodes.filter { it.wicketId != null }.map { it.wicketId })
        assertTrue(r.nodes.single { it.wicketId == "field" }.diagnostics.any { it.message.contains("internal inputs") })
    }

    fun testLazyInputFactoryCannotMutateTheEnclosingPage() {
        val r = lazy("getPage().removeAll(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testAStaticBorrowedInputDoesNotBecomeAnOwnedCache() {
        val r = lazy("return EXISTING;", "private static org.apache.wicket.markup.html.form.TextField<String> EXISTING;")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    private fun composite(inputBody: String = "super(id, model);", members: String = "") = run {
        myFixture.addFileToProject("src/main/java/example/Composite.java", """
            package example;
            public class Composite extends org.apache.wicket.markup.html.panel.Panel {
                private org.apache.wicket.markup.html.form.FormComponent<String> input;
                private final java.util.function.BiFunction<String, org.apache.wicket.model.IModel<String>, ? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory;
                public Composite(String id) { this(id, Input::new); }
                private Composite(String id, java.util.function.BiFunction<String, org.apache.wicket.model.IModel<String>, ? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory) {
                    super(id); this.factory = java.util.Objects.requireNonNull(factory);
                }
                protected org.apache.wicket.markup.html.form.FormComponent<String> create(String id, org.apache.wicket.model.IModel<String> model) { return factory.apply(id, model); }
                public org.apache.wicket.markup.html.form.FormComponent<String> getInput() {
                    if (input == null) { input = create("input", null); add(input); }
                    return input;
                }
                @Override protected void onInitialize() { super.onInitialize(); getInput(); }
                public Composite required() { getInput().setRequired(true); return this; }
                static class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                    Input(String id, org.apache.wicket.model.IModel<String> model) { $inputBody }
                }
                $members
            }
        """)
        val java = pageFile(body = "add(new Composite(\"field\").required());")
        companion(html = "<div wicket:id='field'></div>")
        WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    fun testPrivateConstructorReferenceInputFactoryPreservesTheWrapperBinding() {
        val r = composite()
        assertEquals(r.warnings.toString(), 1, r.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertEquals(listOf("field"), r.nodes.filter { it.wicketId != null }.map { it.wicketId })
    }

    fun testConstructorReferenceInputCannotMutateAParentScope() {
        val r = composite("super(id, model); getPage().removeAll();")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testInstanceInitializersCannotHideAnEnclosingMutation() {
        val r = lazy("new Hidden(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Hidden { { getPage().removeAll(); } }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testImplicitSourceSuperclassConstructorsAreAudited() {
        val r = lazy("new Hidden(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Parent { Parent() { getPage().removeAll(); } } private class Hidden extends Parent { Hidden() {} }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testDataFieldQualifiersCannotHideGraphEffects() {
        val r = lazy("int n = touch().number; return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Data { int number; } private Data touch() { getPage().removeAll(); return new Data(); }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testImplicitObjectCallbacksCannotHideGraphEffects() {
        val r = lazy("String text = String.valueOf(new Hidden()); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Hidden { @Override public String toString() { getPage().removeAll(); return \"bad\"; } }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testSourceModelGettersCannotHideGraphEffects() {
        val r = lazy("org.apache.wicket.model.IModel<String> m = new Hidden(); String text = m.getObject(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Hidden implements org.apache.wicket.model.IModel<String> { public String getObject() { getPage().removeAll(); return \"bad\"; } }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testInitialModelValueWritesDoNotSkipSetterOwnership() {
        val r = lazy("org.apache.wicket.model.IModel<String> m = new Hidden(); org.apache.wicket.markup.html.form.TextField<String> f = new org.apache.wicket.markup.html.form.TextField<>(\"input\", m); f.setDefaultModelObject(\"changed\"); return f;",
            "private class Hidden implements org.apache.wicket.model.IModel<String> { public String getObject() { return \"ok\"; } public void setObject(String value) { getPage().removeAll(); } }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testKnownDataReceiverSubtypesCannotHideAnOverride() {
        val r = lazy("Data data = new Hidden(); String text = data.value(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private class Data { String value() { return \"ok\"; } } private class Hidden extends Data { @Override String value() { getPage().removeAll(); return \"bad\"; } }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testPrimitiveArrayInitializersCannotHideGraphEffects() {
        val r = lazy("int[] numbers = new int[] { touch() }; return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");",
            "private int touch() { getPage().removeAll(); return 1; }")
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }
    fun testOwnedInputEffectDepthLimitDoesNotPublishPartialBindings() {
        val helpers = (0..14).joinToString("\n") { index ->
            "private void depth$index() { ${if (index < 14) "depth${index + 1}();" else ""} }"
        }
        val r = lazy("depth0(); return new org.apache.wicket.markup.html.form.TextField<String>(\"input\");", helpers)
        assertTrue(r.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
        assertTrue(r.warnings.toString(), r.warnings.any { it.message.contains("128-call/12-level bound") })
    }

}
