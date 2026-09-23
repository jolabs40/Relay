package net.jolabs40.relay.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.jolabs40.relay.donnees.PointRelais
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Trouve le relais local, et le démarre s'il ne tourne pas.
 *
 * Le relais vit hors de la fenêtre : il continue quand on la ferme, et les sessions avec lui.
 * Il se lance par `python.exe -m relay` — et non `pythonw` : Java crée ses processus avec
 * `CREATE_NO_WINDOW`, si bien que Python reçoit une console invisible, et que chaque
 * `claude.exe` qu'il démarre en hérite au lieu d'ouvrir une fenêtre noire.
 */
class LanceurRelais(
    private val dossierDonnees: File = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Relay"),
    private val dossierRelais: File = File(InfosApp.DOSSIER_RELAIS),
) {
    private var dernierLancement = 0L

    suspend fun trouver(): PointRelais? = withContext(Dispatchers.IO) {
        lireConfig()?.let { point -> if (joignable(point.port)) return@withContext point }
        demarrer()
        // Le relais écrit config.json au premier démarrage : on le relit une fois le port ouvert.
        repeat(40) {
            delay(250)
            val point = lireConfig()
            if (point != null && joignable(point.port)) return@withContext point
        }
        null
    }

    private fun lireConfig(): PointRelais? = try {
        val config = Json.parseToJsonElement(File(dossierDonnees, "config.json").readText()).jsonObject
        PointRelais("127.0.0.1", config.getValue("port").jsonPrimitive.int, config.getValue("jeton").jsonPrimitive.content)
    } catch (_: Exception) {
        null
    }

    /** Un relais répond « relay » sur `/sante` ; un autre programme sur le même port, non. */
    private fun joignable(port: Int): Boolean = try {
        val connexion = URI("http://127.0.0.1:$port/sante").toURL().openConnection() as HttpURLConnection
        connexion.connectTimeout = 300
        connexion.readTimeout = 1_000
        try {
            connexion.responseCode == 200 && connexion.inputStream.bufferedReader().readText().startsWith("relay")
        } finally {
            connexion.disconnect()
        }
    } catch (_: Exception) {
        false
    }

    private fun demarrer() {
        val maintenant = System.currentTimeMillis()
        // Un relais qui meurt au démarrage ne doit pas être relancé en boucle.
        if (maintenant - dernierLancement < 15_000) return
        dernierLancement = maintenant
        val python = trouverPython() ?: return
        ProcessBuilder(python, "-m", "relay")
            .directory(dossierRelais)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun trouverPython(): String? {
        val chemins = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        return chemins.map { File(it, "python.exe") }
            .firstOrNull { it.isFile && !it.path.contains("WindowsApps", ignoreCase = true) }
            ?.absolutePath
    }
}
