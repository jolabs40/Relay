package net.jolabs40.relay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.jolabs40.relay.donnees.ClientRelais
import net.jolabs40.relay.donnees.EtatConnexion
import net.jolabs40.relay.protocole.Commandes
import net.jolabs40.relay.protocole.Projet
import net.jolabs40.relay.protocole.SessionPassee
import net.jolabs40.relay.protocole.SessionRelais

/** Ce qu'occupe le volet principal. */
sealed interface Volet {
    data object Nouvelle : Volet
    data class Session(val id: String) : Volet
}

data class EtatRelaisUi(
    val connexion: EtatConnexion = EtatConnexion.Connexion,
    /** Celles qui attendent l'utilisateur d'abord, puis les plus récemment actives. */
    val sessions: List<SessionRelais> = emptyList(),
    val projets: List<Projet> = emptyList(),
    val modes: List<String> = emptyList(),
    val historiques: Map<String, List<SessionPassee>> = emptyMap(),
    val volet: Volet = Volet.Nouvelle,
    /** Chemin du projet retenu dans la liste déroulante, pour la prochaine session. */
    val cheminChoisi: String? = null,
) {
    val projetChoisi: Projet? get() = projets.firstOrNull { it.chemin == cheminChoisi }

    val sessionOuverte: SessionRelais?
        get() = (volet as? Volet.Session)?.let { v -> sessions.firstOrNull { it.id == v.id } }
}

class RelaisViewModel(private val client: ClientRelais) : ViewModel() {

    private val volet = MutableStateFlow<Volet>(Volet.Nouvelle)
    private val cheminChoisi = MutableStateFlow<String?>(null)
    private val _brouillons = MutableStateFlow<Map<String, String>>(emptyMap())
    val brouillons: StateFlow<Map<String, String>> = _brouillons.asStateFlow()

    /** Vrai entre l'envoi d'une nouvelle session et son apparition : on l'ouvrira dès qu'elle arrive. */
    private var nouvelleEnCours = false
    private var connues: Set<String> = emptySet()

    val erreurs = client.erreurs
    val alertes = client.alertes

    val etat: StateFlow<EtatRelaisUi> = combine(
        client.etat,
        client.sessions,
        client.projets,
        combine(client.modes, client.historiques, ::Pair),
        combine(volet, cheminChoisi, ::Pair),
    ) { connexion, sessions, projets, (modes, historiques), (volet, choisi) ->
        EtatRelaisUi(
            connexion = connexion,
            sessions = sessions.values.sortedWith(
                compareByDescending<SessionRelais> { it.etat == SessionRelais.ATTENTE }.thenByDescending { it.majA },
            ),
            projets = projets,
            modes = modes,
            historiques = historiques,
            volet = volet,
            cheminChoisi = choisi,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EtatRelaisUi())

    init {
        viewModelScope.launch {
            client.sessions.collect { sessions ->
                val apparues = sessions.keys - connues
                if (nouvelleEnCours && apparues.isNotEmpty()) {
                    nouvelleEnCours = false
                    volet.value = Volet.Session(sessions.getValue(apparues.first()).id)
                }
                // Une session fermée ailleurs (autre client) ne laisse pas un volet vide.
                val courant = volet.value
                if (courant is Volet.Session && courant.id !in sessions) volet.value = Volet.Nouvelle
                connues = sessions.keys
            }
        }
    }

    fun ouvrir(v: Volet) {
        volet.value = v
    }

    /** Choisir un projet ouvre le formulaire de nouvelle session, et charge ses sessions passées. */
    fun choisirProjet(projet: Projet) {
        cheminChoisi.value = projet.chemin
        volet.value = Volet.Nouvelle
        historique(projet)
    }

    fun brouillon(session: String, texte: String) {
        _brouillons.update { it + (session to texte) }
    }

    private fun envoyer(commande: JsonObject, apres: () -> Unit = {}) {
        viewModelScope.launch { if (client.envoyer(commande)) apres() }
    }

    fun nouvelleSession(projet: Projet, prompt: String, mode: String, reprendre: String?) {
        nouvelleEnCours = true
        envoyer(Commandes.nouvelle(projet.chemin, prompt.trim(), mode, reprendre))
    }

    fun historique(projet: Projet) = envoyer(Commandes.historique(projet.chemin))

    fun envoyerPrompt(session: String) {
        val texte = _brouillons.value[session].orEmpty().trim()
        if (texte.isEmpty()) return
        envoyer(Commandes.envoyer(session, texte)) { _brouillons.update { it - session } }
    }

    fun repondreQuestions(session: String, demande: String, reponses: Map<String, String>) =
        envoyer(Commandes.repondreQuestions(session, demande, reponses))

    fun repondrePermission(session: String, demande: String, autoriser: Boolean, motif: String?) =
        envoyer(Commandes.repondrePermission(session, demande, autoriser, motif))

    fun repondrePlan(session: String, demande: String, approuver: Boolean, mode: String?, commentaire: String?) =
        envoyer(Commandes.repondrePlan(session, demande, approuver, mode, commentaire))

    fun interrompre(session: String) = envoyer(Commandes.interrompre(session))

    fun changerMode(session: String, mode: String) = envoyer(Commandes.mode(session, mode))

    fun fermer(session: String) = envoyer(Commandes.fermer(session))

    fun arreterRelais() = envoyer(Commandes.arreter())
}
