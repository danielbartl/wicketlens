package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketSubmissionFixture {
    val host = WicketInitialContentFixture.host
    val page = """
        package example;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.panel.Fragment;
        public class SubmitPage extends WebPage {
            public SubmitPage() {
                WebMarkupContainer people=new WebMarkupContainer("people");people.setOutputMarkupId(true);add(people);
                add(new DialogBox("save","Save") {
                    @Override protected void onConfirm(AjaxRequestTarget target) { success("Saved");target.add(people); }
                });
                add(new DialogBox("delete","Delete") {
                    @Override protected void onConfirm(AjaxRequestTarget target) { info("Deleted"); }
                });
            }
        }
    """
    const val markup = "<div wicket:id='people'></div><div wicket:id='save'></div><div wicket:id='delete'></div><wicket:fragment wicket:id='submitted'><b wicket:id='message'></b></wicket:fragment>"
    fun install(fixture: CodeInsightTestFixture, body: String = page, hostSource: String = host, html: String = markup): PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DialogBox.java", hostSource)
        fixture.addFileToProject("src/main/resources/example/DialogBox.html", WicketInitialContentFixture.hostMarkup)
        fixture.addFileToProject("src/main/resources/example/SubmitPage.html", html)
        return fixture.addFileToProject("src/main/java/example/SubmitPage.java", body) as PsiJavaFile
    }
}
