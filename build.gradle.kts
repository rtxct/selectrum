plugins {
    // Java compilation and building.
    id("java")

    // Lombok.
    id("io.freefair.lombok") version "9.7.0"

    // Intellij API.
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.selectrum"
version = "1.0.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()

    // Use Intellij Plataform for plugin development dependencies.
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Lombok
	compileOnly("org.projectlombok:lombok:1.18.48")
	annotationProcessor("org.projectlombok:lombok:1.18.48")

    // Intellij API.
    intellijPlatform {
        pluginVerifier()
        zipSigner()

        intellijIdeaCommunity("2025.1")
    }
}

java {
    toolchain {
        // Java compile/build version.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        id.set("com.selectrum")
        name.set("Selectrum")

        version.set(project.version.toString())

        description.set("""
            Time-based automatic theme switching for JetBrains IDEs.
            Configure a YAML schedule to change the IDE theme at specific hours of the day.
        """.trimIndent())

        ideaVersion {
            sinceBuild.set("251")
        }
    }
}

tasks.buildPlugin {
    // Defines the name of the final distributable plugin file
    archiveFileName.set("selectrum.zip")
}