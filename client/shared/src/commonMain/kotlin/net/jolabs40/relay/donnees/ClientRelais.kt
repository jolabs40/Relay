package net.jolabs40.relay.donnees

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.jolabs40.relay.protocole.Evenement
import net.jolabs40.relay.protocole.MessageRelais
import net.jolabs40.relay.protocole.Projet
import net.jolabs40.relay.protocole.SessionPassee
import net.jolabs40.relay.protocole.SessionRelais
import net.jolabs40.relay.protocole.VERSION_PROTOCOLE
import net.jolabs40.relay.protocole.decoderMessage

/** Où joindre le relais. Sur Windows, lu dans `%APPDATA%\Relay` ; sur Android, saisi à l'appairage. */
data class PointRelais(val hote: String, val port: Int, val jeton: String)

sealed interface EtatConnexion {
    data object Connexion : EtatConnexion
    data object Connecte : EtatConnexion
    data class Deconnecte(val raison: String?) : EtatConnexion
}

/** Ce qui mérite de tirer l'utilisateur de ce qu'il fait : une demande, ou un tour terminé. */
data class Alerte(val session: SessionRelais, val evenement: Evenement)

/**
 * La connexion au relais, maintenue tant que le client vit : coupée, elle se rouvre d'elle-même,
 * et le relais renvoie alors l'état complet de chaque session (message `bonjour`). Rien n'est
 * donc perdu quand la fenêtre se ferme : les sessions vivent dans le relais.
 */
class ClientRelais(
    private val scope: CoroutineScope,
    private val trouverRelais: suspend () -> PointRelais?,
) {
    private val http = HttpClient(CIO) { install(WebSockets) }

    private val _etat = MutableStateFlow<EtatConnexion>(EtatConnexion.Connexion)
    val etat: StateFlow<EtatConnexion> = _etat.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, SessionRelais>>(emptyMap())
    val sessions: StateFlow<Map<String, SessionRelais>> = _sessions.asStateFlow()

    /** Version du protocole annoncée par le relais : plus ancienne que la nôtre, il faut le relancer. */
    private val _versionRelais = MutableStateFlow(VERSION_PROTOCOLE)
    val versionRelais: StateFlow<Int> = _versionRelais.asStateFlow()

    private val _projets = MutableStateFlow<List<Projet>>(emptyList())
    val projets: StateFlow<List<Projet>> = _projets.asStateFlow()

    private val _modes = MutableStateFlow(listOf("default", "acceptEdits", "plan", "bypassPermissions"))
    val modes: StateFlow<List<String>> = _modes.asStateFlow()

    private val _historiques = MutableStateFlow<Map<String, List<SessionPassee>>>(emptyMap())
    val historiques: StateFlow<Map<String, List<SessionPassee>>> = _historiques.asStateFlow()

    /** Message du relais ou de la socket ; `null` quand la commande n'a pas pu partir, faute de connexion. */
    private val _erreurs = MutableSharedFlow<String?>(extraBufferCapacity = 8)
    val erreurs: SharedFlow<String?> = _erreurs.asSharedFlow()

    private val _alertes = MutableSharedFlow<Alerte>(extraBufferCapacity = 16)
    val alertes: SharedFlow<Alerte> = _alertes.asSharedFlow()

    private var connexion: DefaultClientWebSocketSession? = null
    private var boucle: Job? = null

    fun demarrer() {
        if (boucle?.isActive == true) return
        boucle = scope.launch {
            var attente = 1_000L
            while (isActive) {
                _etat.value = EtatConnexion.Connexion
                val raison = try {
                    val point = trouverRelais()
                    if (point == null) {
                        "relais introuvable"
                    } else {
                        ouvrir(point)
                        attente = 1_000L
                        null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e::class.simpleName
                }
                connexion = null
                _etat.value = EtatConnexion.Deconnecte(raison)
                delay(attente)
                attente = (attente * 2).coerceAtMost(10_000L)
            }
        }
    }

    private suspend fun ouvrir(point: PointRelais) {
        http.webSocket(host = point.hote, port = point.port, path = "/ws", request = {
            parameter("jeton", point.jeton)
        }) {
            connexion = this
            for (trame in incoming) {
                if (trame is Frame.Text) recevoir(trame.readText())
            }
        }
    }

    internal fun recevoir(brut: String) {
        when (val message = decoderMessage(brut)) {
            is MessageRelais.Bonjour -> {
                _versionRelais.value = message.version
                _projets.value = message.projets
                if (message.modes.isNotEmpty()) _modes.value = message.modes
                _sessions.value = message.sessions.associateBy { it.id }
                _etat.value = EtatConnexion.Connecte
            }
            is MessageRelais.Session -> {
                val precedente = _sessions.value[message.session.id]
                _sessions.update { it + (message.session.id to message.session) }
                nouveautes(precedente, message.session).forEach { _alertes.tryEmit(Alerte(message.session, it)) }
            }
            is MessageRelais.SessionFermee -> _sessions.update { it - message.id }
            is MessageRelais.Historique -> _historiques.update { it + (message.chemin to message.sessions) }
            is MessageRelais.Erreur -> _erreurs.tryEmit(message.message)
            MessageRelais.Inconnu -> Unit
        }
    }

    suspend fun envoyer(commande: JsonObject): Boolean {
        val active = connexion
        if (active == null) {
            _erreurs.tryEmit(null)
            return false
        }
        return try {
            active.send(commande.toString())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _erreurs.tryEmit(e.message.orEmpty())
            false
        }
    }

    fun fermer() {
        boucle?.cancel()
        http.close()
    }

    companion object {
        /** Les événements apparus depuis le dernier état, qui appellent l'utilisateur. */
        fun nouveautes(avant: SessionRelais?, apres: SessionRelais): List<Evenement> {
            val connus = avant?.evenements?.associateBy { it.id }.orEmpty()
            return apres.evenements.filter { evenement ->
                evenement.id !in connus && (evenement.enAttente || evenement.type == Evenement.RESULTAT)
            }
        }
    }
}
