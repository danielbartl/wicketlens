package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketTreeFixture {
    fun initializeDefaultProjectAnnotations() {
        // This application-lived service must precede heavy fixture listener tracking.
        com.intellij.testFramework.TestApplicationManager.getInstance()
        com.intellij.codeInsight.ExternalAnnotationsManager.getInstance(com.intellij.openapi.project.ProjectManager.getInstance().defaultProject)
    }

    val node = """
        package example; public abstract class Branch extends org.apache.wicket.extensions.markup.html.repeater.tree.Node<String> {
            public Branch(String id, org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree<String> tree, org.apache.wicket.model.IModel<String> model) { super(id,tree,model); }
            @Override protected org.apache.wicket.MarkupContainer createJunctionComponent(String id) {
                return new org.apache.wicket.ajax.markup.html.AjaxLink<Void>(id) { @Override public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) {} };
            }
        }
    """
    val nested = """
        package example; public class Nested extends org.apache.wicket.extensions.markup.html.repeater.tree.NestedTree<String> {
            public Nested(String id, org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider<String> provider) { super(id,provider); }
            @Override public org.apache.wicket.Component newNodeComponent(String id, org.apache.wicket.model.IModel<String> model) {
                Branch node=new Branch(id,this,model) { @Override protected org.apache.wicket.Component createContent(String contentId, org.apache.wicket.model.IModel<String> contentModel) { return Nested.this.newContentComponent(contentId,contentModel); } };
                node.setOutputMarkupId(true); return node;
            }
            @Override protected org.apache.wicket.Component newContentComponent(String id, org.apache.wicket.model.IModel<String> model) { return new Fields(id,model,null); }
        }
    """
    val table = """
        package example; public class Tabular extends org.apache.wicket.extensions.markup.html.repeater.tree.TableTree<String,String> {
            public Tabular(String id, org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider<String> provider) { super(id,java.util.List.of(),provider,20); }
            @Override public org.apache.wicket.Component newNodeComponent(String id, org.apache.wicket.model.IModel<String> model) {
                return new Branch(id,this,model) { @Override protected org.apache.wicket.Component createContent(String contentId, org.apache.wicket.model.IModel<String> contentModel) { return newContentComponent(contentId,contentModel); } };
            }
            @Override protected org.apache.wicket.Component newContentComponent(String id, org.apache.wicket.model.IModel<String> model) { return new Fields(id,model,null); }
        }
    """
    val fields = """
        package example; public final class Fields extends org.apache.wicket.markup.html.panel.Panel {
            public Fields(String id, org.apache.wicket.model.IModel<String> model, Runnable action) {
                super(id,model);
                org.apache.wicket.ajax.markup.html.AjaxLink<Void> link=new org.apache.wicket.ajax.markup.html.AjaxLink<>("link") { @Override public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) { action.run(); } };
                link.setVisible(action!=null); add(link);
                org.apache.wicket.markup.html.WebMarkupContainer plain=new org.apache.wicket.markup.html.WebMarkupContainer("plain"); plain.setVisible(action==null); add(plain);
                org.apache.wicket.MarkupContainer holder=action!=null ? link : plain;
                holder.add(marker("icon",model.getObject()),new org.apache.wicket.markup.html.basic.Label("label",model));
                org.apache.wicket.MarkupContainer other=action!=null ? plain : link;
                other.add(new org.apache.wicket.markup.html.WebMarkupContainer("icon"),new org.apache.wicket.markup.html.WebMarkupContainer("label"));
            }
            static org.apache.wicket.Component marker(String id,String value) { return value!=null ? new org.apache.wicket.markup.html.basic.Label(id,value) : new org.apache.wicket.markup.html.WebMarkupContainer(id).setVisible(false); }
        }
    """
    val page = """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage() { add(new Nested("folders",null)); Tabular table=new Tabular("accounts",null); add(table); }
        }
    """
    const val fieldsMarkup="<wicket:panel><a wicket:id='link'><i wicket:id='icon'></i><b wicket:id='label'></b></a><span wicket:id='plain'><i wicket:id='icon'></i><b wicket:id='label'></b></span></wicket:panel>"
    const val pageMarkup="<div wicket:id='folders'></div><div wicket:id='accounts'></div>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }
        for((name,text) in listOf("Branch" to node,"Nested" to nested,"Tabular" to table,"Fields" to fields)) fixture.addFileToProject("src/main/java/example/$name.java",text)
        fixture.addFileToProject("src/main/resources/example/Fields.html",fieldsMarkup)
        fixture.addFileToProject("src/main/resources/example/ExamplePage.html",pageMarkup)
        return fixture.addFileToProject("src/main/java/example/ExamplePage.java",page) as PsiJavaFile
    }
}
