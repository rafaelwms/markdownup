
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.markdownup"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
    }
}

kotlin { jvmToolchain(25) }

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "261"; untilBuild = provider { null } }
    }
}

tasks.buildSearchableOptions { enabled = false }
tasks.named("instrumentCode") { enabled = false }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
tasks.test { useJUnitPlatform() }
tasks.named("instrumentTestCode") { enabled = false }
