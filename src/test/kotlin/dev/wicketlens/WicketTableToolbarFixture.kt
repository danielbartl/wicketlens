package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketTableToolbarFixture {
    val host = """
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.panel.*;
        import org.apache.wicket.extensions.markup.html.repeater.data.table.*;
        import org.apache.wicket.markup.repeater.data.*;
        import org.apache.wicket.model.*;
        public class TableHost extends Panel {
            private static final String CONTENT="content";
            private final DataTable<String,String> table;
            private EmptyToolbar emptyToolbar;
            public TableHost(String id) {
                super(id);
                table=new RowsTable("grid",java.util.List.of(),new IDataProvider<String>() {
                    public java.util.Iterator<String> iterator(long first,long count) { return java.util.List.<String>of().iterator(); }
                    public long size() { return 0; }
                    public IModel<String> model(String value) { return Model.of(value); }
                },5);
                add(table);
            }
            public TableHost setEmptyState(java.util.function.Function<String,? extends Component> factory) {
                Component content=factory.apply(CONTENT);
                if(content==null || !CONTENT.equals(content.getId())) { throw new IllegalArgumentException(); }
                if(emptyToolbar==null) {
                    emptyToolbar=new EmptyToolbar(table);
                    table.addBottomToolbar(emptyToolbar);
                }
                emptyToolbar.cell.addOrReplace(content);
                return this;
            }
            private int columns() { return 7; }
            private final class EmptyToolbar extends AbstractToolbar {
                private final WebMarkupContainer cell;
                EmptyToolbar(DataTable<String,String> table) {
                    super(table);
                    cell=new WebMarkupContainer("cell");
                    cell.add(AttributeModifier.replace("colspan",(IModel<Integer>)TableHost.this::columns));
                    add(cell);
                }
                @Override protected void onConfigure() { super.onConfigure(); setVisible(getTable().getRowCount()==0); }
            }
            private final class RowsTable extends DataTable<String,String> {
                RowsTable(String id,java.util.List<? extends IColumn<String,String>> columns,IDataProvider<String> provider,long count) { super(id,columns,provider,count); }
            }
        }
    """
    val content = """
        package example;
        public class EmptyView extends org.apache.wicket.markup.html.panel.Panel {
            public EmptyView(String id,String title) { this(id,org.apache.wicket.model.Model.of(title),null); }
            public EmptyView(String id,String title,String message) { this(id,org.apache.wicket.model.Model.of(title),org.apache.wicket.model.Model.of(message)); }
            public EmptyView(String id,org.apache.wicket.model.IModel<String> title,org.apache.wicket.model.IModel<String> messageModel) {
                super(id);
                add(new org.apache.wicket.markup.html.basic.Label("title",title));
                org.apache.wicket.markup.html.basic.Label message=new org.apache.wicket.markup.html.basic.Label("message",messageModel);
                message.setVisible(messageModel!=null && messageModel.getObject()!=null);
                add(message);
            }
        }
    """
    val page = """
        package example;
        public class EmptyPage extends org.apache.wicket.markup.html.WebPage {
            public EmptyPage() { add(new TableHost("empty").setEmptyState(id -> new EmptyView(id,"No results","Try again"))); }
        }
    """
    const val hostMarkup = "<wicket:panel><table wicket:id='grid'></table></wicket:panel>"
    const val toolbarMarkup = "<wicket:panel><tr><td wicket:id='cell'><div wicket:id='content'></div></td></tr></wicket:panel>"
    const val contentMarkup = "<wicket:panel><h3 wicket:id='title'></h3><p wicket:id='message'></p></wicket:panel>"
    const val pageMarkup = "<div wicket:id='empty'></div>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel(fixture.module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel = com.intellij.pom.java.LanguageLevel.JDK_25
        }
        fixture.addFileToProject("src/main/java/example/TableHost.java",host)
        fixture.addFileToProject("src/main/resources/example/TableHost.html",hostMarkup)
        fixture.addFileToProject("src/main/resources/example/TableHost\$EmptyToolbar.html",toolbarMarkup)
        fixture.addFileToProject("src/main/java/example/EmptyView.java",content)
        fixture.addFileToProject("src/main/resources/example/EmptyView.html",contentMarkup)
        fixture.addFileToProject("src/main/resources/example/EmptyPage.html",pageMarkup)
        return fixture.addFileToProject("src/main/java/example/EmptyPage.java",page) as PsiJavaFile
    }
}
