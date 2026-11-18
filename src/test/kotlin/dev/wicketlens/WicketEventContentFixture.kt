package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketEventContentFixture {
    val host=WicketInitialContentFixture.host.replace("public DialogBox open(AjaxRequestTarget target)","""
        public DialogBox open(Component content,AjaxRequestTarget target) {
            setContent(content);open=true;return open(target);
        }
        public DialogBox open(AjaxRequestTarget target)
    """)
    val list="""
        package example;public class DetailList extends org.apache.wicket.markup.html.panel.Panel {
            public DetailList(String id) { super(id); }
            public DetailList addProperty(String property) { return addItem(property); }
            public DetailList addItem(String label) { return this; }
        }
    """
    val page="""
        package example;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.ajax.markup.html.AjaxLink;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.list.*;
        import org.apache.wicket.markup.html.panel.Fragment;
        public class EventPage extends WebPage {
            public EventPage() {
                DrawerBox details=new DrawerBox("detailsDrawer","Details");add(details);
                add(new ListView<String>("rows",java.util.List.of("Ada")) {
                    @Override protected void populateItem(ListItem<String> item) {
                        item.add(new AjaxLink<Void>("details") {
                            @Override public void onClick(AjaxRequestTarget target) {
                                Fragment content=new Fragment(DialogBox.CONTENT_ID,"personDetails",EventPage.this);
                                content.add(new DetailList("list").addProperty("name").addProperty("email"));
                                details.setFooterVisible(false);
                                details.open(content,target);
                            }
                        });
                    }
                });
            }
        }
    """
    const val markup="<div wicket:id='detailsDrawer'></div><div wicket:id='rows'><a wicket:id='details'>Details</a></div><wicket:fragment wicket:id='personDetails'><dl wicket:id='list'></dl></wicket:fragment>"
    fun install(fixture:CodeInsightTestFixture, pageSource:String=page, hostSource:String=host,
        drawerSource:String=WicketInitialContentFixture.drawer, listSource:String=list, markupSource:String=markup):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DialogBox.java",hostSource)
        fixture.addFileToProject("src/main/java/example/DrawerBox.java",drawerSource)
        fixture.addFileToProject("src/main/java/example/DetailList.java",listSource)
        fixture.addFileToProject("src/main/resources/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
        fixture.addFileToProject("src/main/resources/example/DetailList.html","<wicket:panel>Details</wicket:panel>")
        fixture.addFileToProject("src/main/resources/example/EventPage.html",markupSource)
        return fixture.addFileToProject("src/main/java/example/EventPage.java",pageSource) as PsiJavaFile
    }
}
