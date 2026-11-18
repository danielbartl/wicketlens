package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketRegisteredListFixture {
    val list = """
        package example;
        import org.apache.wicket.markup.html.list.*;
        import org.apache.wicket.model.*;
        public abstract class DecoratedList<T> extends ListView<T> {
            private final Decoration behavior;
            public DecoratedList(String id,IModel<java.util.List<T>> model) { this(id,model,true); }
            public DecoratedList(String id,IModel<java.util.List<T>> model,boolean exclusive) { super(id,model);behavior=new Decoration(exclusive);add(behavior); }
            @Override protected ListItem<T> newItem(int index,IModel<T> model) {
                ListItem<T> item=super.newItem(index,model);item.add(behavior);return item;
            }
            public static <T> DecoratedList<T> of(String id,IModel<java.util.List<T>> model,java.util.function.BiConsumer<ListItem<T>,T> callback) { return of(id,model,true,callback); }
            public static <T> DecoratedList<T> of(String id,IModel<java.util.List<T>> model,boolean exclusive,java.util.function.BiConsumer<ListItem<T>,T> callback) {
                java.util.Objects.requireNonNull(callback);
                return new DecoratedList<T>(id,model,exclusive) {
                    @Override protected void populateItem(ListItem<T> item) { callback.accept(item,item.getModelObject()); }
                };
            }
        }
    """
    val behavior = """
        package example;public class Decoration extends org.apache.wicket.behavior.Behavior {
            private final boolean exclusive;
            public Decoration(boolean exclusive) { this.exclusive=exclusive; }
            @Override public void onComponentTag(org.apache.wicket.Component component,org.apache.wicket.markup.ComponentTag tag) { if(exclusive) tag.put("data-group","one"); }
        }
    """
    val page = """
        package example;public class CallbackPage extends org.apache.wicket.markup.html.WebPage {
            record Row(String title,String body) implements java.io.Serializable {}
            public CallbackPage() {
                add(new org.apache.wicket.markup.html.basic.Label("heading","Sections"));
                add(DecoratedList.of("sections",org.apache.wicket.model.Model.ofList(java.util.List.of(new Row("First","One"))), (item,model) -> {
                    item.add(new org.apache.wicket.markup.html.basic.Label("title",model.title()));
                    item.add(new org.apache.wicket.markup.html.basic.Label("body",model.body()));
                }));
            }
        }
    """
    const val markup = "<h1 wicket:id='heading'></h1><details wicket:id='sections'><summary wicket:id='title'></summary><div wicket:id='body'></div></details>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model -> model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25 }
        fixture.addFileToProject("src/main/java/example/Decoration.java",behavior)
        fixture.addFileToProject("src/main/java/example/DecoratedList.java",list)
        fixture.addFileToProject("src/main/resources/example/CallbackPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/CallbackPage.java",page) as PsiJavaFile
    }
}
