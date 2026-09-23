pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
plugins {
    // Fournit le JDK 21 d'Adoptium, qui porte jpackage (absent du JBR d'Android Studio).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "Relay"

// `shared` : protocole, connexion au relais, état et écrans — tout ce que l'app Android reprendra.
// `windows` : la fenêtre, l'icône de la zone de notification et le lancement du relais.
include(":shared", ":windows")
