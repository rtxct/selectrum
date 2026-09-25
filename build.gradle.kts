plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.selectrum"
version = "1.0.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
	compileOnly("org.projectlombok:lombok:1.18.48")
	annotationProcessor("org.projectlombok:lombok:1.18.48")

    intellijPlatform {
        pluginVerifier()
        zipSigner()

        intellijIdeaCommunity("2025.1")
    }
}

java {
    toolchain {
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