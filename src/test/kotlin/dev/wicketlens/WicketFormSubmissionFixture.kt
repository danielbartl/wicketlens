package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketFormSubmissionFixture {
    val button = """
        package example;
        public class SubmitButton extends org.apache.wicket.ajax.markup.html.form.AjaxButton {
            public SubmitButton(String id) { this(id,(org.apache.wicket.model.IModel<String>)null); }
            public SubmitButton(String id,org.apache.wicket.model.IModel<String> label) { super(id,label); }
            public SubmitButton(String id,org.apache.wicket.model.IModel<String> label,org.apache.wicket.markup.html.form.Form<?> form) { super(id,label,form); }
        }
    """
    val page = """
        package example;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.form.Form;
        public class FormPage extends WebPage {
            public FormPage() {
                Form<Void> form=new Form<>("invoiceForm");form.setOutputMarkupId(true);add(form);
                form.add(new SubmitButton("submit") {
                    @Override protected void onSubmit(AjaxRequestTarget target) { success("Saved");target.add(form); }
                    @Override protected void onError(AjaxRequestTarget target) { error("Please fix the form");target.add(form); }
                });
            }
        }
    """
    const val markup = "<html xmlns:wicket='http://wicket.apache.org'><body><form wicket:id='invoiceForm'><button wicket:id='submit'>Save</button></form></body></html>"
    fun install(fixture:CodeInsightTestFixture,body:String=page,buttonSource:String=button,html:String=markup):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/SubmitButton.java",buttonSource)
        fixture.addFileToProject("src/main/resources/example/FormPage.html",html)
        return fixture.addFileToProject("src/main/java/example/FormPage.java",body) as PsiJavaFile
    }
}
