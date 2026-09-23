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
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        pluginVerifier()
        zipSigner()
    }
}

java {
    toolchain {
        // JetBrains Runtime for 2025.x is based on JDK 21.
        // Change to JavaLanguageVersion.of(25) if targeting JBR 25+.
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
            Configure a JSON schedule to change your IDE theme at specific hours of the day.
        """.trimIndent())

        ideaVersion {
            sinceBuild.set("251")
        }
    }
}
