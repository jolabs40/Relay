package net.jolabs40.relay

import net.jolabs40.relay.donnees.ClientRelais
import net.jolabs40.relay.protocole.Commandes
import net.jolabs40.relay.protocole.Evenement
import net.jolabs40.relay.protocole.MessageRelais
import net.jolabs40.relay.protocole.decoderMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les messages tels que les écrit relais/relay — un changement de ce côté doit casser ici. */
class ProtocoleTest {

    private val session = """
        {"type": "session", "session": {"id": "abc", "projet": "Relay", "cwd": "C:/x", "titre": "Essai",
         "etat": "attente", "mode": "plan", "activite": "", "claude_session_id": "sid", "en_file": 0,
         "cree_a": 1, "maj_a": 2, "champ_futur": true, "evenements": [
           {"id": 1, "type": "prompt", "horodatage": 1, "texte": "Fais-le"},
           {"id": 2, "type": "question", "horodatage": 2, "demande": "d1", "en_attente": true,
            "questions": [{"question": "Thé ou café ?", "header": "Boisson", "multiSelect": false,
                           "options": [{"label": "Thé", "description": ""}, {"label": "Café"}]}]}
         ]}}
    """.trimIndent()

    @Test
    fun `une session se decode, champs inconnus compris`() {
        val message = decoderMessage(session) as MessageRelais.Session
        val s = message.session
        assertEquals("attente", s.etat)
        assertEquals("sid", s.claudeSessionId)
        assertEquals("d1", s.demandeEnAttente?.demande)
        assertEquals(listOf("Thé", "Café"), s.evenements[1].questions[0].options.map { it.label })
    }

    @Test
    fun `seules les demandes et les comptes rendus nouveaux alertent`() {
        val avant = (decoderMessage(session) as MessageRelais.Session).session
        val apres = avant.copy(
            evenements = avant.evenements + Evenement(id = 3, type = Evenement.RESULTAT, texte = "Fini") +
                Evenement(id = 4, type = Evenement.INFO, texte = "Interrompu"),
        )
        assertEquals(listOf(3), ClientRelais.nouveautes(avant, apres).map { it.id })
        // Au premier état reçu, la question en attente alerte aussi.
        assertEquals(listOf(2), ClientRelais.nouveautes(null, avant).map { it.id })
    }

    @Test
    fun `les reponses partent au format attendu par le relais`() {
        val commande = Commandes.repondreQuestions("abc", "d1", mapOf("Thé ou café ?" to "Thé"))
        assertEquals(
            """{"type":"repondre","session":"abc","demande":"d1","reponse":{"reponses":{"Thé ou café ?":"Thé"}}}""",
            commande.toString(),
        )
        val plan = Commandes.repondrePlan("abc", "d2", approuver = true, mode = "acceptEdits", message = null)
        assertTrue(plan.toString().contains(""""reponse":{"approuver":true,"mode":"acceptEdits"}"""))
    }

    @Test
    fun `bonjour et erreur`() {
        val bonjour = decoderMessage(
            """{"type":"bonjour","version":1,"racine":"C:/r","projets":[{"nom":"A","chemin":"C:/r/A"}],"modes":["plan"],"sessions":[]}""",
        ) as MessageRelais.Bonjour
        assertEquals("A", bonjour.projets.single().nom)
        assertEquals(MessageRelais.Erreur("Session inconnue"), decoderMessage("""{"type":"erreur","message":"Session inconnue","ref":null}"""))
        assertEquals(MessageRelais.Inconnu, decoderMessage("""{"type":"futur"}"""))
    }
}
