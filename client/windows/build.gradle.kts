import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val versionApp: String = providers.gradleProperty("versionApp").get()
version = versionApp

/**
 * Le dossier du relais Python, que l'app lance quand elle ne le trouve pas. Usage personnel, sur
 * cette machine : le chemin des sources est gravé à la compilation plutôt que d'embarquer Python.
 */
val dossierRelais = rootProject.layout.projectDirectory.dir("../relais").asFile.canonicalPath

val genererInfosApp by tasks.registering {
    val dossier = layout.buildDirectory.dir("generated/infosApp/kotlin")
    val version = versionApp
    val relais = dossierRelais
    inputs.property("version", version)
    inputs.property("relais", relais)
    outputs.dir(dossier)
    doLast {
        val fichier = dossier.get().file("net/jolabs40/relay/windows/InfosApp.kt").asFile
        fichier.parentFile.mkdirs()
        val relaisEchappe = relais.replace("\\", "\\\\")
        fichier.writeText(
            """
            |package net.jolabs40.relay.windows
            |
            |/** Généré par la tâche Gradle `genererInfosApp` : ne pas modifier à la main. */
            |internal object InfosApp {
            |    const val VERSION = "$version"
            |    const val DOSSIER_RELAIS = "$relaisEchappe"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir(genererInfosApp)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

val jdkPaquetage = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.ADOPTIUM
}

compose.desktop {
    application {
        mainClass = "net.jolabs40.relay.windows.MainKt"
        javaHome = jdkPaquetage.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Relay"
            packageVersion = versionApp
            description = "Remote for Claude Code sessions"
            vendor = "jolabs40"
            modules("java.net.http", "jdk.unsupported", "jdk.crypto.ec", "jdk.localedata", "jdk.accessibility")
            windows {
                iconFile.set(project.file("packaging/relay.ico"))
                // Ne jamais changer : c'est lui qui fait qu'une nouvelle version remplace l'ancienne.
                upgradeUuid = "5d0f3c8e-7a41-4c2b-9f63-1e8b2a7d4c90"
                perUserInstall = true
                dirChooser = false
                menu = true
                menuGroup = "Relay"
                shortcut = true
            }
        }
    }
}
