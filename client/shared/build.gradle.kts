plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    // Seule cible pour l'instant ; l'app Android ajoutera la sienne sans toucher à commonMain.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.lifecycle.viewmodel.compose)
            api(libs.lifecycle.runtime.compose)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.websockets)
            implementation(libs.markdown.renderer.m3)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            // Rendu des écrans hors fenêtre (PlancheEcransTest) : Skia natif et Dispatchers.Main.
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

tasks.named<Test>("jvmTest") {
    // Planche des écrans, seulement sur demande : -Pplanche=1
    providers.gradleProperty("planche").orNull?.let { systemProperty("relay.planche", it) }
    // Contre le vrai relais et le vrai Claude, seulement sur demande : -Preel=1
    providers.gradleProperty("reel").orNull?.let { systemProperty("relay.reel", it) }
    testLogging { showStandardStreams = true }
    systemProperty("relay.captures", layout.buildDirectory.dir("captures").get().asFile.absolutePath)
}

compose.resources {
    packageOfResClass = "net.jolabs40.relay.ressources"
    publicResClass = true
    generateResClass = always
}
