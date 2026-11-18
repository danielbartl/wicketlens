package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketSelectionFixture {
    val flow = """
            package example; public class Flow extends org.apache.wicket.markup.html.panel.Panel {
                private static final String SLOT="body";
                private final java.util.List<Entry> entries=new java.util.ArrayList<>();
                private final org.apache.wicket.markup.html.form.Form<Void> form;
                private int selected;
                public Flow(String id) { super(id); form=new org.apache.wicket.markup.html.form.Form<>("form"); add(form); form.add(new org.apache.wicket.markup.html.basic.Label(SLOT)); }
                public Flow add(String title, java.util.function.Function<String,? extends org.apache.wicket.Component> factory) { return add(org.apache.wicket.model.Model.of(title),factory); }
                public Flow add(org.apache.wicket.model.IModel<String> title, java.util.function.Function<String,? extends org.apache.wicket.Component> factory) { entries.add(new Entry(title,factory)); return this; }
                public Flow select(int value) { selected=value; show(); return this; }
                private void show() { org.apache.wicket.Component content=entries.get(selected).factory().apply(SLOT); if (!SLOT.equals(content.getId())) { throw new IllegalArgumentException(); } form.addOrReplace(content); }
                @Override protected void onInitialize() { super.onInitialize(); show(); }
                private record Entry(org.apache.wicket.model.IModel<String> title, java.util.function.Function<String,? extends org.apache.wicket.Component> factory) {}
            }
        """
    val page = """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() {
                    Flow flow=new Flow("flow"); add(flow);
                    flow.add("First", id -> { org.apache.wicket.markup.html.panel.Fragment step=new org.apache.wicket.markup.html.panel.Fragment(id,"first",this); step.add(new org.apache.wicket.markup.html.basic.Label("name")); return step; });
                    flow.add("Second", id -> { org.apache.wicket.markup.html.panel.Fragment step=new org.apache.wicket.markup.html.panel.Fragment(id,"second",this); step.add(new org.apache.wicket.markup.html.basic.Label("total")); return step; });
                }
            }
        """
    const val flowMarkup = "<wicket:panel><form wicket:id='form'><div wicket:id='body'></div></form></wicket:panel>"
    const val pageMarkup = "<div wicket:id='flow'></div><wicket:fragment wicket:id='first'><b wicket:id='name'></b></wicket:fragment><wicket:fragment wicket:id='second'><b wicket:id='total'></b></wicket:fragment>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = com.intellij.pom.java.LanguageLevel.JDK_25
        }
        fixture.addFileToProject("src/main/java/example/Flow.java", flow)
        fixture.addFileToProject("src/main/resources/example/Flow.html", flowMarkup)
        fixture.addFileToProject("src/main/resources/example/ExamplePage.html", pageMarkup)
        return fixture.addFileToProject("src/main/java/example/ExamplePage.java", page) as PsiJavaFile
    }
}
