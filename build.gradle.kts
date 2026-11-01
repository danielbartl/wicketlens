import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.wicketlens"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Libraries attached to fixture Java projects, never to the plugin's runtime.
val wicketFixture = configurations.create("wicketFixture") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val springFixture = configurations.create("springFixture") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val springTestFixture = configurations.create("springTestFixture") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    wicketFixture("org.apache.wicket:wicket-core:10.11.0")
    springFixture("org.apache.wicket:wicket-spring:10.11.0")
    springFixture("org.springframework:spring-web:6.2.12")
    springTestFixture("org.junit.platform:junit-platform-console-standalone:1.13.4")
    springTestFixture("org.mockito:mockito-core:5.21.0")
    springTestFixture("org.apache.wicket:wicket-tester:10.11.0")
    springTestFixture("jakarta.servlet:jakarta.servlet-api:6.0.0")
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.mcpServer")
        bundledModule("intellij.xml.psi")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    // Compile against the IDE's bundled stdlib rather than shipping another copy.
    compileOnly(kotlin("stdlib", "2.4.0"))
    testImplementation(kotlin("stdlib", "2.4.0"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

// Isolated MCP test host. Defaults to the probe; semantic tests opt into their own disposable project/tools.
val runMcpSpike by intellijPlatformTesting.runIde.registering {
    task {
        val structureFixture = providers.gradleProperty("mcpStructureFixture").map(String::toBoolean).orElse(false)
        val spikeProject = providers.gradleProperty("mcpSpikeProject")
            .orElse(structureFixture.flatMap { semantic -> layout.buildDirectory.dir(if (semantic) "mcp-structure/project" else "mcp-spike/project").map { it.asFile.absolutePath } })
        val spikePort = providers.gradleProperty("mcpSpikePort").orElse("64343")
        val spikeTools = providers.gradleProperty("mcpSpikeTools").orElse("wicketlens_mcp_probe")
        args("mcpServer", "--project=${spikeProject.get()}", "--port=${spikePort.get()}",
            "--invocation-mode=direct", "--allowed-tools=${spikeTools.get()}")
        systemProperty("idea.load.plugins.id", "dev.wicketlens,com.intellij.java,com.intellij.mcpServer")
        doFirst {
            if (structureFixture.get()) {
                val javaHome = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }.get().metadata.installationPath.asFile
                val code = ProcessBuilder("python3", layout.projectDirectory.file("tools/prepare_mcp_structure_fixture.py").asFile.absolutePath,
                    "--project-dir", spikeProject.get(), "--ide-config", sandboxConfigDirectory.get().asFile.absolutePath,
                    "--java-home", javaHome.absolutePath, "--libraries", wicketFixture.asPath,
                    "--spring-libraries", springFixture.asPath).inheritIO().start().waitFor()
                check(code == 0) { "Could not prepare the disposable MCP structure project" }
            } else file(spikeProject.get()).mkdirs()
        }
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    from("LICENSE") { into("META-INF") }
}

intellijPlatform {
    sandboxContainer = layout.buildDirectory.dir("idea-sandbox")
    // There are no custom Configurable pages requiring searchable-option indexing.
    buildSearchableOptions = false

    pluginConfiguration {
        name = "WicketLens"
        ideaVersion {
            sinceBuild = "262.10968.63"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            local(providers.provider { intellijPlatform.platformPath.toFile() })
        }
    }
}

tasks.test {
    useJUnit()
    inputs.files(wicketFixture)
    inputs.files(springFixture)
    inputs.files(springTestFixture)
    inputs.dir("examples/demo/src")
    inputs.dir("examples/spring-demo/src")
    systemProperty("wicketlens.test.demo", layout.projectDirectory.dir("examples/demo/src").asFile.absolutePath)
    systemProperty("wicketlens.test.springDemo", layout.projectDirectory.dir("examples/spring-demo/src").asFile.absolutePath)
    doFirst {
        systemProperty("wicketlens.test.libraries", wicketFixture.asPath)
        systemProperty("wicketlens.test.springLibraries", springFixture.asPath)
        systemProperty("wicketlens.test.springTestLibraries", springTestFixture.asPath)
        systemProperty("wicketlens.test.javac", javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(25)
        }.get().executablePath.asFile.absolutePath)
    }
    // Load only this plugin and its dependencies, excluding unrelated bundled plugins.
    val mcpHostTests = providers.gradleProperty("mcpHostTests").map(String::toBoolean).orElse(false).get()
    val verificationMode = if (mcpHostTests) "host" else "normal"
    outputs.file(layout.buildDirectory.file("reports/release-records/$verificationMode.json"))
    systemProperty("idea.load.plugins.id", if (mcpHostTests) "dev.wicketlens,com.intellij.java,com.intellij.mcpServer" else "dev.wicketlens,com.intellij.java")
    if (mcpHostTests) {
        include("**/WicketMcpHostTest*")
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/mcpHostTest"))
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/mcpHostTest"))
        binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/mcpHostTest/binary"))
    } else exclude("**/WicketMcpHostTest*")
    inputs.files(fileTree("tools") { include("*.py") })
    doFirst {
        check(ProcessBuilder("python3", "tools/check_release.py", "--record-start", verificationMode)
            .directory(layout.projectDirectory.asFile).inheritIO().start().waitFor() == 0)
    }
    doLast {
        check(ProcessBuilder("python3", "tools/check_release.py", "--record-tests", verificationMode)
            .directory(layout.projectDirectory.asFile).inheritIO().start().waitFor() == 0)
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named("verifyPlugin") {
    outputs.file(layout.buildDirectory.file("reports/release-records/verifier.json"))
    doLast {
        check(ProcessBuilder("python3", "tools/check_release.py", "--record-verifier")
            .directory(layout.projectDirectory.asFile).inheritIO().start().waitFor() == 0)
    }
}
