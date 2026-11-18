package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketFormInputFixture {
    val base = """
        package example;
        public abstract class FieldBase<T,C extends org.apache.wicket.markup.html.form.FormComponent<T>> extends org.apache.wicket.markup.html.panel.GenericPanel<T> {
            private C input;
            private final org.apache.wicket.markup.html.basic.Label label;
            private final org.apache.wicket.markup.html.basic.Label feedback;
            public FieldBase(String id,org.apache.wicket.model.IModel<T> model) {
                super(id,model);
                add(new org.apache.wicket.markup.html.TransparentWebMarkupContainer("shell"));
                label=new org.apache.wicket.markup.html.basic.Label("label","Period");
                feedback=new org.apache.wicket.markup.html.basic.Label("feedback","");
                add(label,feedback);
            }
            protected abstract C make(String id,org.apache.wicket.model.IModel<T> model);
            public C getInput() { if(input==null) { input=make("input",getModel()); input.setOutputMarkupId(true); add(input); } return input; }
            @Override protected void onInitialize() { super.onInitialize(); getInput(); }
        }
    """
    val field = """
        package example;
        public class Field extends FieldBase<String,Field.Input> {
            public Field(String id,org.apache.wicket.model.IModel<String> model) { super(id,model); }
            @Override protected Input make(String id,org.apache.wicket.model.IModel<String> model) { return new Input(id,model); }
            public final class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                private final org.apache.wicket.markup.html.form.TextField<String> begin;
                private final org.apache.wicket.markup.html.form.TextField<String> end;
                Input(String id,org.apache.wicket.model.IModel<String> model) {
                    super(id,model);
                    begin=part("begin",org.apache.wicket.model.Model.of(""));
                    end=part("end",org.apache.wicket.model.Model.of(""));
                    add(begin,end);
                    add((org.apache.wicket.validation.IValidator<String>) validatable -> { if(validatable.getValue()!=null && validatable.getValue().startsWith("bad:")) validatable.error(new org.apache.wicket.validation.ValidationError().setMessage("bad range")); });
                }
                private org.apache.wicket.markup.html.form.TextField<String> part(String id,org.apache.wicket.model.IModel<String> model) {
                    org.apache.wicket.markup.html.form.TextField<String> value=new org.apache.wicket.markup.html.form.TextField<>(id,model);
                    value.add(org.apache.wicket.AttributeModifier.replace("aria-label",id)); return value;
                }
                @Override public void convertInput() { setConvertedInput(begin.getConvertedInput()+":"+end.getConvertedInput()); }
                @Override public boolean checkRequired() { return !isRequired() || !org.apache.wicket.util.string.Strings.isEmpty(begin.getInput()) && !org.apache.wicket.util.string.Strings.isEmpty(end.getInput()); }
            }
        }
    """
    const val markup = "<wicket:panel><section wicket:id='shell'><label wicket:id='label'></label><div wicket:id='input'></div><small wicket:id='feedback'></small></section></wicket:panel>"
    const val inputMarkup = "<wicket:panel><input wicket:id='begin'/><input wicket:id='end'/></wicket:panel>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/FieldBase.java",base)
        fixture.addFileToProject("src/main/resources/example/Field.html",markup)
        fixture.addFileToProject("src/main/resources/example/Field\$Input.html",inputMarkup)
        return fixture.addFileToProject("src/main/java/example/Field.java",field) as PsiJavaFile
    }
}
