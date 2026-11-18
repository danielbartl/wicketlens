package dev.wicketlens

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.ui.SimpleTextAttributes
import dev.wicketlens.ui.WicketProjectViewDecorator

class WicketProjectViewTest : WicketProjectPsiTestCase() {
    private fun source(name: String, declaration: String) = myFixture.addFileToProject(
        "src/main/java/example/$name.java", "package example; $declaration") as PsiJavaFile

    private fun classNode(type: PsiClass) = ClassTreeNode(project, type, ViewSettings.DEFAULT)

    private fun presentation(node: ProjectViewNode<*>): PresentationData {
        node.update() // Invoke the registered decorator through the real Project tree update.
        return node.presentation
    }

    private fun marked(data: PresentationData) = WicketProjectViewDecorator.hasMarker(data.getIcon(false))

    fun testNativeClassNodesMarkPagesPanelsFormPanelsAndComponentsThroughCustomBases() {
        val cases = listOf(
            Pair("BasePage", "abstract class BasePage extends org.apache.wicket.markup.html.WebPage {}"),
            Pair("ConcretePage", "class ConcretePage extends BasePage {}"),
            Pair("RawPage", "abstract class RawPage extends org.apache.wicket.Page {}"),
            Pair("DetailsPanel", "class DetailsPanel extends org.apache.wicket.markup.html.panel.Panel { DetailsPanel(String id) { super(id); } }"),
            Pair("InputPanel", "class InputPanel extends org.apache.wicket.markup.html.form.FormComponentPanel<String> { InputPanel(String id) { super(id); } }"),
            Pair("Badge", "class Badge extends org.apache.wicket.markup.html.basic.Label { Badge(String id) { super(id); } }"),
        )
        cases.forEach { (name, declaration) ->
            val file = source(name, declaration)
            assertTrue(name, marked(presentation(classNode(file.classes.single()))))
        }
    }

    fun testFileNodesUseTheirPrimaryClassAndNestedClassesGetTheirOwnRole() {
        val file = source("Main", """
            class Helper extends org.apache.wicket.markup.html.WebPage {}
            class Main {
                static class NestedPage extends org.apache.wicket.markup.html.WebPage {}
                static class NestedPanel extends org.apache.wicket.markup.html.panel.Panel { NestedPanel(String id) { super(id); } }
            }
        """.trimIndent())
        assertFalse(marked(presentation(PsiFileNode(project, file, ViewSettings.DEFAULT))))
        val main = file.classes.single { it.name == "Main" }
        assertTrue(marked(presentation(classNode(main.innerClasses.single { it.name == "NestedPage" }))))
        assertTrue(marked(presentation(classNode(main.innerClasses.single { it.name == "NestedPanel" }))))
        val page = pageFile()
        assertTrue(marked(presentation(PsiFileNode(project, page, ViewSettings.DEFAULT))))
    }

    fun testNamesHtmlFilesAndLibraryClassesDoNotAcquireMarkers() {
        val fake = source("LooksLikePage", "class LooksLikePage {}")
        assertFalse(marked(presentation(classNode(fake.classes.single()))))
        val html = companion()
        assertFalse(marked(presentation(PsiFileNode(project, html, ViewSettings.DEFAULT))))
        val library = JavaPsiFacade.getInstance(project).findClass("org.apache.wicket.markup.html.WebPage", module.getModuleWithLibrariesScope())!!
        assertFalse(marked(presentation(classNode(library))))
    }

    fun testDecorationPreservesPresentationAndDoesNotModifySources() {
        val file = pageFile()
        val before = file.text to file.modificationStamp
        val node = classNode(file.classes.single())
        val data = PresentationData("ExamplePage", "example", AllIcons.Nodes.Class, null)
        data.addText("ExamplePage", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        data.tooltip = "existing tooltip"
        val original = data.coloredText.single()
        val decorator = WicketProjectViewDecorator()
        decorator.decorate(node, data)
        decorator.decorate(node, data)
        assertSame(original, data.coloredText.first())
        assertTrue(marked(data))
        assertEquals(1, data.coloredText.size)
        val icon = data.getIcon(false) as com.intellij.ui.BadgeIcon
        assertSame(AllIcons.Nodes.Class, icon.icon)
        assertEquals(AllIcons.Nodes.Class.iconWidth, icon.iconWidth)
        assertEquals(AllIcons.Nodes.Class.iconHeight, icon.iconHeight)
        assertEquals("example", data.locationString)
        assertEquals("existing tooltip", data.tooltip)
        assertEquals(before, file.text to file.modificationStamp)
        val color = icon.paint as java.awt.Color
        assertTrue("The marker must be orange: $color", color.red > color.green && color.green > color.blue)
    }

    fun testCommittedSuperclassChangesRefreshTheRoleWithoutAPluginCache() {
        val file = source("Changing", "abstract class Changing extends org.apache.wicket.markup.html.WebPage {}")
        fun current() = marked(presentation(classNode(file.classes.single())))
        assertTrue(current())
        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(file.text.replace("org.apache.wicket.markup.html.WebPage", "org.apache.wicket.markup.html.panel.Panel")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(current())
        WriteCommandAction.runWriteCommandAction(project) { document.setText("package example; class Changing {}") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertFalse(current())
    }

    fun testIndexingWithholdsMarkersAndSmartModeRestoresThem() {
        val node = classNode(pageFile().classes.single())
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        try {
            val data = PresentationData()
            WicketProjectViewDecorator().decorate(node, data)
            assertFalse(marked(data))
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        assertTrue(marked(presentation(node)))
    }

    fun testDecorationHonorsCancellation() {
        val node = classNode(pageFile().classes.single())
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketProjectViewDecorator().decorate(node, PresentationData())
            }, indicator)
        }
    }

    fun testNativeRendererKeepsNamesWithoutExtraTextAndShowsTheOrangeBadge() {
        val files = listOf(
            pageFile(),
            source("DetailsPanel", "class DetailsPanel extends org.apache.wicket.markup.html.panel.Panel { DetailsPanel(String id) { super(id); } }"),
            source("Badge", "class Badge extends org.apache.wicket.markup.html.basic.Label { Badge(String id) { super(id); } }"),
            source("Person", "class Person {}"),
        )
        val root = javax.swing.tree.DefaultMutableTreeNode("example")
        val renderer = com.intellij.ide.util.treeView.NodeRenderer()
        val tree = javax.swing.JTree(root).apply { cellRenderer = renderer; isRootVisible = false; rowHeight = 28 }
        files.forEachIndexed { index, file ->
            val node = classNode(file.classes.single())
            val data = presentation(node)
            assertEquals(index < 3, marked(data))
            // Headless fixtures cache empty platform glyphs. Load the platform's
            // actual class SVG explicitly for the offscreen renderer preview.
            val glyph = com.intellij.openapi.util.IconLoader.getIcon("/nodes/class.svg", AllIcons::class.java)
            val badge = data.getIcon(false) as? com.intellij.ui.BadgeIcon
            data.setIcon(if (badge == null) glyph else com.intellij.ui.BadgeIcon(glyph, badge.paint, badge.provider))
            val row = javax.swing.tree.DefaultMutableTreeNode(node)
            root.add(row)
            val rendered = renderer.getTreeCellRendererComponent(tree, row, false, false, true, index, false) as com.intellij.ui.SimpleColoredComponent
            val text = rendered.getCharSequence(false).toString()
            assertTrue(text, text.contains(file.classes.single().name!!))
            assertFalse(text, text.contains("Wicket "))
        }
        (tree.model as javax.swing.tree.DefaultTreeModel).reload()
        tree.setSize(600, 132)
        tree.doLayout()
        val image = java.awt.image.BufferedImage(tree.width, tree.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { tree.paint(graphics) } finally { graphics.dispose() }
        val output = java.nio.file.Path.of("build/reports/project-view/preview.png")
        java.nio.file.Files.createDirectories(output.parent)
        javax.imageio.ImageIO.write(image, "png", output.toFile())
    }
}
