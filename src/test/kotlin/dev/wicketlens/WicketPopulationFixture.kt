package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketPopulationFixture {
    const val model = """
        package example;
        public record Entry(String label,java.util.List<Entry> items) implements java.io.Serializable {
            public boolean isGroup() { return !items.isEmpty(); }
        }
    """
    const val page = """
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.list.*;
        import org.apache.wicket.markup.html.link.*;
        import org.apache.wicket.model.*;
        public class MenuPage extends WebPage {
            public MenuPage() {
                WebMarkupContainer sidebar=new Sidebar("sidebar");add(sidebar);
                sidebar.add(new ListView<Entry>("entries",Model.ofList(java.util.List.<Entry>of())) {
                    @Override protected void populateItem(ListItem<Entry> item) {
                        Entry entry=item.getModelObject();
                        if(entry.isGroup()) {
                            item.add(new WebMarkupContainer("link").setVisible(false));
                            item.add(group("group",entry));
                            item.setVisible(true);
                        } else {
                            populateLink(item);
                            item.add(new WebMarkupContainer("group").setVisible(false));
                        }
                    }
                });
            }
            private WebMarkupContainer group(String id,Entry entry) {
                WebMarkupContainer details=new WebMarkupContainer(id);
                details.add(new Label("label",entry.label()));
                details.add(new ListView<Entry>("children",Model.ofList(entry.items())) {
                    @Override protected void populateItem(ListItem<Entry> item) { populateLink(item); }
                });
                details.add(AttributeModifier.replace("open",""));
                return details;
            }
            private void populateLink(ListItem<Entry> item) {
                Entry entry=item.getModelObject();
                AbstractLink link=newLink("link",entry);
                link.add(new Label("label",entry.label()));
                link.add(new Label("badge",Model.of("1")) {
                    @Override protected void onConfigure() { super.onConfigure();setVisible(true); }
                });
                item.add(link);
                item.setVisible(true);
                if(selected(entry)) { link.add(AttributeModifier.replace("aria-current","page")); }
                else { link.add(AttributeModifier.remove("aria-current")); }
            }
            protected AbstractLink newLink(String id,Entry entry) { return new BookmarkablePageLink<Void>(id,MenuPage.class); }
            protected boolean selected(Entry entry) { return false; }
            public static final class Sidebar extends WebMarkupContainer { public Sidebar(String id) { super(id);add(AttributeModifier.replace("class","menu")); } }
        }
    """
    const val markup = "<html xmlns:wicket='http://wicket.apache.org'><body><aside wicket:id='sidebar'><ul><li wicket:id='entries'><a wicket:id='link'><b wicket:id='label'></b><span wicket:id='badge'></span></a><details wicket:id='group'><summary wicket:id='label'></summary><ul><li wicket:id='children'><a wicket:id='link'><b wicket:id='label'></b><span wicket:id='badge'></span></a></li></ul></details></li></ul></aside></body></html>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }
        fixture.addFileToProject("src/main/java/example/Entry.java",model)
        fixture.addFileToProject("src/main/resources/example/MenuPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/MenuPage.java",page) as PsiJavaFile
    }
}
