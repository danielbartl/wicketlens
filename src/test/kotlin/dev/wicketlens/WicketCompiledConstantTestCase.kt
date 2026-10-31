package dev.wicketlens

import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.concurrent.TimeUnit

abstract class WicketCompiledConstantTestCase : WicketProjectPsiTestCase() {
    protected lateinit var jar: Path
    protected lateinit var libraryName: String

    override fun setUp() {
        super.setUp()
        val directory = Files.createTempDirectory("wicketlens-constants")
        VfsRootAccess.allowRootAccess(testRootDisposable, directory.toString())
        Disposer.register(testRootDisposable) { directory.toFile().deleteRecursively() }
        val classes = Files.createDirectories(directory.resolve("classes"))
        val definitions = mapOf(
            "Ids" to """
                public class Ids {
                    public static final String TITLE = "title";
                    public static final String ESCAPED = "ti\"tle";
                    public static final String TOO_LONG = "${"x".repeat(4097)}";
                    public static final boolean ENABLED = true, DISABLED = false;
                    public static final byte BYTE = -2;
                    public static final short SHORT = 3;
                    public static final char LETTER = 't';
                    public static final int COUNT = 2;
                    public static final long LARGE = 4294967296L;
                    public static final float FLOAT = 1.5f;
                    public static final double DOUBLE = 2.5;
                    public static final String RUNTIME = new String("title");
                    public static final String BLOCK;
                    public static final Integer BOXED = 2;
                    public static String MUTABLE = "title";
                    public final String INSTANCE = "title";
                    private static final String PRIVATE = "title";
                    static final String PACKAGE = "title";
                    public static String title() { return "title"; }
                    static { BLOCK = "title"; System.setProperty("wicketlens.fixture.must.not.load", "loaded"); }
                    public static class Nested { public static final String TITLE = "title"; }
                }
            """.trimIndent(),
            "Alias" to "public class Alias extends Ids {}",
            "Constants" to "public interface Constants { String TITLE = \"title\"; }",
            "Other" to "public interface Other { String TITLE = \"different\"; }",
        )
        val sources = definitions.map { (name, text) -> directory.resolve("$name.java").also { Files.writeString(it, "package lib; $text") } }
        val log = directory.resolve("compile.log")
        val process = ProcessBuilder(listOf(System.getProperty("wicketlens.test.javac"), "--release", "17", "-d", classes.toString()) + sources.map { it.toString() })
            .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue("Fixture compiler timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals(Files.readString(log), 0, process.exitValue())
        } finally { if (process.isAlive) process.destroyForcibly() }
        jar = directory.resolve("constants.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            Files.walk(classes).use { paths -> paths.filter(Files::isRegularFile).forEach { path ->
                output.putNextEntry(JarEntry(classes.relativize(path).toString().replace('\\', '/')))
                output.write(Files.readAllBytes(path)); output.closeEntry()
            } }
        }
        libraryName = "constants-${directory.fileName}"
        PsiTestUtil.addLibrary(testRootDisposable, module, libraryName, directory.toString(), jar.fileName.toString())
    }

    protected fun page(body: String, imports: String = "", members: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.basic.Label;
        $imports
        public class ExamplePage extends WebPage { public ExamplePage() { $body } $members }
    """.trimIndent()) as PsiJavaFile

    protected fun report(body: String, id: String = "title", imports: String = "", members: String = ""): PageReport {
        val java = page(body, imports, members)
        companion(html = "<span wicket:id='$id'></span>")
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    protected fun matched(report: PageReport, id: String = "title") {
        assertEquals(report.matching.toString(), listOf(DiagnosticCode.MATCHED), report.matching!!.findings.map { it.code })
        assertEquals(id, report.children().single().wicketId)
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    protected fun uncertain(report: PageReport) = assertTrue(report.matching.toString(), report.matching!!.findings.none {
        it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
    })

    protected fun attachWrongSource() {
        val root = Files.createDirectories(jar.parent.resolve("attached/lib"))
        Files.writeString(root.resolve("Ids.java"), "package lib; public class Ids { public static final String TITLE = \"wrongSource\"; }")
        val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.parent)!!
        ModuleRootModificationUtil.updateModel(module) { model ->
            val library = model.orderEntries.filterIsInstance<LibraryOrderEntry>().single { it.libraryName == libraryName }.library!!
            val editor = library.modifiableModel
            editor.addRoot(virtual, OrderRootType.SOURCES)
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { editor.commit() }
        }
    }
}
