package net.jolabs40.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.jolabs40.relay.donnees.ClientRelais
import net.jolabs40.relay.donnees.EtatConnexion
import net.jolabs40.relay.donnees.PointRelais
import net.jolabs40.relay.protocole.Commandes
import net.jolabs40.relay.protocole.Evenement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Le client Kotlin contre le vrai relais et le vrai Claude, qui consomme un tour de l'abonnement :
 * seulement sur demande, relais lancé (`python -m relay`), `./gradlew :shared:jvmTest -Preel=1`.
 */
class RelaisReelTest {

    @Test
    fun `une question remonte, la reponse repart, le compte rendu revient`(): Unit = runBlocking {
        assumeTrue(System.getProperty("relay.reel") != null)
        val config = Json.parseToJsonElement(File(System.getenv("APPDATA"), "Relay/config.json").readText()).jsonObject
        val point = PointRelais("127.0.0.1", config.getValue("port").jsonPrimitive.int, config.getValue("jeton").jsonPrimitive.content)
        val racine = config.getValue("racine").jsonPrimitive.content
        val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = ClientRelais(portee) { point }
        try {
            client.demarrer()
            withTimeout(15_000) { client.etat.first { it == EtatConnexion.Connecte } }

            client.envoyer(
                Commandes.nouvelle(
                    chemin = File(racine, "Relay").path,
                    prompt = "Test de Relay. Sans aucun autre outil, pose-moi avec AskUserQuestion la question " +
                        "« Quelle couleur ? » avec les options Rouge et Vert. Puis réponds en une phrase qui " +
                        "reprend exactement ma réponse.",
                    mode = "default",
                    reprendre = null,
                ),
            )
            val question = withTimeout(180_000) { client.alertes.first { it.evenement.type == Evenement.QUESTION } }
            val intitule = question.evenement.questions.single().question
            client.envoyer(Commandes.repondreQuestions(question.session.id, question.evenement.demande!!, mapOf(intitule to "Vert")))

            val fin = withTimeout(180_000) {
                client.alertes.first { it.session.id == question.session.id && it.evenement.type == Evenement.RESULTAT }
            }
            println("Compte rendu : ${fin.evenement.texte}")
            assertTrue(fin.evenement.texte.orEmpty().contains("Vert", ignoreCase = true))
            val session = client.sessions.value.getValue(question.session.id)
            assertEquals(listOf("prompt", "question", "resultat"), session.evenements.map { it.type })
            client.envoyer(Commandes.fermer(session.id))
        } finally {
            client.fermer()
            portee.cancel()
        }
    }
}
