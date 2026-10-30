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

dependencies {
    wicketFixture("org.apache.wicket:wicket-core:10.11.0")
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.java")
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

kotlin {
    jvmToolchain(25)
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
    inputs.dir("examples/demo/src")
    systemProperty("wicketlens.test.demo", layout.projectDirectory.dir("examples/demo/src").asFile.absolutePath)
    doFirst {
        systemProperty("wicketlens.test.libraries", wicketFixture.asPath)
        systemProperty("wicketlens.test.javac", javaToolchains.compilerFor {
            languageVersion = JavaLanguageVersion.of(25)
        }.get().executablePath.asFile.absolutePath)
    }
    // Load only this plugin and its dependencies, excluding unrelated bundled plugins.
    systemProperty("idea.load.plugins.id", "dev.wicketlens,com.intellij.java")
    testLogging {
        events("passed", "skipped", "failed")
    }
}
