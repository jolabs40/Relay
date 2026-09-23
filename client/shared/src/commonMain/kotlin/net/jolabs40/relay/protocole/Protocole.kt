package net.jolabs40.relay.protocole

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/*
 * Le protocole du relais (relais/relay/serveur.py) : des objets JSON portant un champ `type`.
 * Les clés restent en snake_case, comme partout entre nos apps et leurs serveurs ; seules les
 * questions gardent le camelCase de l'outil AskUserQuestion, qu'elles recopient telles quelles.
 */

/** 2 : pièces jointes. Un relais plus ancien les ignorerait sans rien dire. */
const val VERSION_PROTOCOLE = 2

val jsonRelais = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class Projet(val nom: String, val chemin: String)

/**
 * Une pièce jointe à envoyer : le fichier entier, en base64 — le relais n'a pas forcément accès aux
 * fichiers du client (téléphone). Les images partent en bloc image, le reste est déposé sur le PC.
 */
@Serializable
data class PieceJointe(
    val nom: String,
    @SerialName("type_mime") val typeMime: String,
    val donnees: String,
    /** JPEG réduit, en base64 : ce que le fil affichera. Images seulement. */
    val vignette: String? = null,
) {
    val estImage: Boolean get() = typeMime in TYPES_IMAGE
}

/** Ce que le fil garde d'une pièce envoyée : jamais le fichier lui-même. */
@Serializable
data class PieceAffichee(
    val nom: String,
    @SerialName("type_mime") val typeMime: String = "",
    val taille: Long = 0,
    val vignette: String? = null,
) {
    val estImage: Boolean get() = typeMime in TYPES_IMAGE
}

/** Les formats d'image que Claude lit directement (API Messages). */
val TYPES_IMAGE = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

@Serializable
data class OptionQuestion(val label: String, val description: String = "")

@Serializable
data class Question(
    val question: String,
    val header: String = "",
    val options: List<OptionQuestion> = emptyList(),
    val multiSelect: Boolean = false,
)

/** Un élément du fil : tout ce que l'utilisateur doit voir, et rien d'autre. */
@Serializable
data class Evenement(
    val id: Int,
    val type: String,
    val horodatage: Long = 0,
    val texte: String? = null,
    val demande: String? = null,
    @SerialName("en_attente") val enAttente: Boolean = false,
    val questions: List<Question> = emptyList(),
    val outil: String? = null,
    val resume: String? = null,
    val detail: String? = null,
    val plan: String? = null,
    val erreur: Boolean = false,
    @SerialName("duree_ms") val dureeMs: Long? = null,
    @SerialName("cout_usd") val coutUsd: Double? = null,
    val tours: Int? = null,
    val reponse: JsonObject? = null,
    val pieces: List<PieceAffichee> = emptyList(),
) {
    companion object {
        const val PROMPT = "prompt"
        const val QUESTION = "question"
        const val PERMISSION = "permission"
        const val PLAN = "plan"
        const val RESULTAT = "resultat"
        const val INFO = "info"
    }
}

@Serializable
data class SessionRelais(
    val id: String,
    val projet: String,
    val cwd: String,
    val titre: String = "",
    val etat: String,
    val mode: String,
    val activite: String = "",
    @SerialName("claude_session_id") val claudeSessionId: String = "",
    @SerialName("en_file") val enFile: Int = 0,
    @SerialName("cree_a") val creeA: Long = 0,
    @SerialName("maj_a") val majA: Long = 0,
    val evenements: List<Evenement> = emptyList(),
) {
    val demandeEnAttente: Evenement? get() = evenements.lastOrNull { it.enAttente }

    companion object {
        const val DEMARRAGE = "demarrage"
        const val INACTIVE = "inactive"
        const val TRAVAILLE = "travaille"
        const val ATTENTE = "attente"
        const val ERREUR = "erreur"
    }
}

@Serializable
data class SessionPassee(
    val id: String,
    val resume: String = "",
    @SerialName("modifiee_a") val modifieeA: Long = 0,
)

/** Ce que le relais envoie, déjà décodé. */
sealed interface MessageRelais {
    data class Bonjour(
        val version: Int,
        val racine: String,
        val projets: List<Projet>,
        val modes: List<String>,
        val sessions: List<SessionRelais>,
    ) : MessageRelais

    data class Session(val session: SessionRelais) : MessageRelais
    data class SessionFermee(val id: String) : MessageRelais
    data class Historique(val chemin: String, val sessions: List<SessionPassee>) : MessageRelais
    data class Erreur(val message: String) : MessageRelais
    data object Inconnu : MessageRelais
}

@Serializable
private data class BonjourBrut(
    val version: Int,
    val racine: String = "",
    val projets: List<Projet> = emptyList(),
    val modes: List<String> = emptyList(),
    val sessions: List<SessionRelais> = emptyList(),
)

@Serializable
private data class HistoriqueBrut(val chemin: String, val sessions: List<SessionPassee> = emptyList())

fun decoderMessage(brut: String): MessageRelais {
    val objet = jsonRelais.parseToJsonElement(brut).jsonObject
    return when (objet["type"]?.jsonPrimitive?.content) {
        "bonjour" -> jsonRelais.decodeFromJsonElement(BonjourBrut.serializer(), objet).let {
            MessageRelais.Bonjour(it.version, it.racine, it.projets, it.modes, it.sessions)
        }
        "session" -> MessageRelais.Session(
            jsonRelais.decodeFromJsonElement(SessionRelais.serializer(), objet.getValue("session")),
        )
        "session_fermee" -> MessageRelais.SessionFermee(objet.getValue("id").jsonPrimitive.content)
        "historique" -> jsonRelais.decodeFromJsonElement(HistoriqueBrut.serializer(), objet).let {
            MessageRelais.Historique(it.chemin, it.sessions)
        }
        "erreur" -> MessageRelais.Erreur(objet["message"]?.jsonPrimitive?.content.orEmpty())
        else -> MessageRelais.Inconnu
    }
}

/** Les commandes du client, prêtes à envoyer. */
object Commandes {
    fun nouvelle(chemin: String, prompt: String, mode: String, reprendre: String?, pieces: List<PieceJointe> = emptyList()) =
        buildJsonObject {
            put("type", "nouvelle")
            put("chemin", chemin)
            put("prompt", prompt)
            put("mode", mode)
            if (reprendre != null) put("reprendre", reprendre)
            if (pieces.isNotEmpty()) put("pieces", jsonRelais.encodeToJsonElement(ListSerializer(PieceJointe.serializer()), pieces))
        }

    fun envoyer(session: String, prompt: String, pieces: List<PieceJointe> = emptyList()) = buildJsonObject {
        put("type", "envoyer")
        put("session", session)
        put("prompt", prompt)
        if (pieces.isNotEmpty()) put("pieces", jsonRelais.encodeToJsonElement(ListSerializer(PieceJointe.serializer()), pieces))
    }

    /** Réponse à AskUserQuestion : une valeur par question, les choix multiples joints par « , ». */
    fun repondreQuestions(session: String, demande: String, reponses: Map<String, String>) =
        repondre(session, demande, buildJsonObject {
            putJsonObject("reponses") { reponses.forEach { (question, valeur) -> put(question, valeur) } }
        })

    fun repondrePermission(session: String, demande: String, autoriser: Boolean, message: String?) =
        repondre(session, demande, buildJsonObject {
            put("autoriser", autoriser)
            if (!message.isNullOrBlank()) put("message", message)
        })

    fun repondrePlan(session: String, demande: String, approuver: Boolean, mode: String?, message: String?) =
        repondre(session, demande, buildJsonObject {
            put("approuver", approuver)
            if (mode != null) put("mode", mode)
            if (!message.isNullOrBlank()) put("message", message)
        })

    private fun repondre(session: String, demande: String, reponse: JsonObject) = buildJsonObject {
        put("type", "repondre")
        put("session", session)
        put("demande", demande)
        put("reponse", reponse)
    }

    fun interrompre(session: String) = simple("interrompre", session)
    fun fermer(session: String) = simple("fermer", session)

    fun mode(session: String, mode: String) = buildJsonObject {
        put("type", "mode")
        put("session", session)
        put("mode", mode)
    }

    /** Ferme toutes les sessions, puis le relais lui-même. */
    fun arreter() = buildJsonObject { put("type", "arreter") }

    fun historique(chemin: String) = buildJsonObject {
        put("type", "historique")
        put("chemin", chemin)
    }

    private fun simple(type: String, session: String) = buildJsonObject {
        put("type", type)
        put("session", session)
    }
}

/** Lecture d'une réponse enregistrée, pour l'afficher une fois la demande close. */
fun JsonObject.texte(cle: String): String? = (this[cle] as? JsonPrimitive)?.content
