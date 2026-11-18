package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketRegisteredFormFixture {
    val wrapper = """
        package example;
        public class CustomField extends FieldBase<String,org.apache.wicket.markup.html.form.FormComponent<String>> {
            private final java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.markup.html.form.FormComponent<String>> inputFactory;
            public CustomField(String id,java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory) { this(id,null,factory); }
            public CustomField(String id,org.apache.wicket.model.IModel<String> model,java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory) { super(id,model); this.inputFactory=java.util.Objects.requireNonNull(factory); }
            @Override protected org.apache.wicket.markup.html.form.FormComponent<String> make(String id,org.apache.wicket.model.IModel<String> model) {
                org.apache.wicket.markup.html.form.FormComponent<String> component=inputFactory.apply(id,model);
                if(component==null || !id.equals(component.getId())) { throw new IllegalStateException("Invalid component ID"); }
                return component;
            }
        }
    """
    val input = """
        package example;
        public class PhoneInput extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
            private final org.apache.wicket.markup.html.form.DropDownChoice<String> code;
            private final org.apache.wicket.markup.html.form.TextField<String> number;
            public PhoneInput(String id,org.apache.wicket.model.IModel<String> model) {
                super(id,model);
                code=new org.apache.wicket.markup.html.form.DropDownChoice<>("code",org.apache.wicket.model.Model.of("+43"),java.util.List.of("+43","+49"));
                number=new org.apache.wicket.markup.html.form.TextField<>("number",org.apache.wicket.model.Model.of(""));
                add(code,number);
            }
            @Override public void convertInput() { setConvertedInput(code.getConvertedInput()+" "+number.getConvertedInput()); }
        }
    """
    const val page = """
        package example; public class ContactPage extends org.apache.wicket.markup.html.WebPage {
            public ContactPage() { add(new CustomField("phone",PhoneInput::new)); }
        }
    """
    const val inputMarkup = "<wicket:panel><select wicket:id='code'></select><input wicket:id='number'/></wicket:panel>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/FieldBase.java",WicketFormInputFixture.base)
        fixture.addFileToProject("src/main/java/example/CustomField.java",wrapper)
        fixture.addFileToProject("src/main/java/example/PhoneInput.java",input)
        fixture.addFileToProject("src/main/resources/example/CustomField.html",WicketFormInputFixture.markup)
        fixture.addFileToProject("src/main/resources/example/PhoneInput.html",inputMarkup)
        fixture.addFileToProject("src/main/resources/example/ContactPage.html","<div wicket:id='phone'></div>")
        return fixture.addFileToProject("src/main/java/example/ContactPage.java",page) as PsiJavaFile
    }
}
