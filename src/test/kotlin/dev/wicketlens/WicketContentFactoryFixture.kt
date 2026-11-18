package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketContentFactoryFixture {
    val host = """
        package example; public class TableHost extends org.apache.wicket.markup.html.panel.Panel {
            private static final String TOOLBAR="toolbar";
            public TableHost(String id) { super(id); add(new org.apache.wicket.markup.html.WebMarkupContainer(TOOLBAR).setVisible(false)); }
            public TableHost setToolbar(java.util.function.Function<String,? extends org.apache.wicket.Component> factory) {
                org.apache.wicket.Component content=factory.apply(TOOLBAR);
                if (content==null || !TOOLBAR.equals(content.getId())) { throw new IllegalArgumentException("Bad id: "+(content==null ? "null" : content.getId())); }
                replace(content);
                return this;
            }
        }
    """
    val input = """
        package example; public class SearchField extends org.apache.wicket.markup.html.form.TextField<String> {
            public SearchField(String id) { super(id,org.apache.wicket.model.Model.of("")); }
            public SearchField setHint(String value) { add(org.apache.wicket.AttributeModifier.replace("placeholder",value)); return self(); }
            private SearchField self() { return this; }
        }
    """
    val page = """
        package example; public class FactoryPage extends org.apache.wicket.markup.html.WebPage {
            public FactoryPage() {
                TableHost table=new TableHost("table"); add(table);
                table.setToolbar(id -> {
                    org.apache.wicket.markup.html.panel.Fragment toolbar=new org.apache.wicket.markup.html.panel.Fragment(id,"toolbarFragment",this);
                    org.apache.wicket.markup.html.form.Form<Void> form=new org.apache.wicket.markup.html.form.Form<>("searchForm");
                    toolbar.add(form);
                    form.add(new SearchField("search").setHint("Find").add(new org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior("input") {
                        @Override protected void onUpdate(org.apache.wicket.ajax.AjaxRequestTarget target) { target.add(table); }
                    }));
                    return toolbar;
                });
            }
        }
    """
    const val hostMarkup = "<wicket:panel><div wicket:id='toolbar'></div></wicket:panel>"
    const val pageMarkup = "<div wicket:id='table'></div><wicket:fragment wicket:id='toolbarFragment'><form wicket:id='searchForm'><input wicket:id='search'/></form></wicket:fragment>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = com.intellij.pom.java.LanguageLevel.JDK_25
        }
        fixture.addFileToProject("src/main/java/example/TableHost.java", host)
        fixture.addFileToProject("src/main/resources/example/TableHost.html", hostMarkup)
        fixture.addFileToProject("src/main/java/example/SearchField.java", input)
        fixture.addFileToProject("src/main/resources/example/FactoryPage.html", pageMarkup)
        return fixture.addFileToProject("src/main/java/example/FactoryPage.java", page) as PsiJavaFile
    }
}
