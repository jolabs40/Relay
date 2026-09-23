package net.jolabs40.relay.ui.ecrans

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.jolabs40.relay.donnees.EtatConnexion
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.connexion_en_cours
import net.jolabs40.relay.ressources.deconnecte
import net.jolabs40.relay.ressources.deconnecte_raison
import net.jolabs40.relay.ressources.erreur_relais
import net.jolabs40.relay.ressources.non_connecte
import net.jolabs40.relay.ui.RelaisViewModel
import net.jolabs40.relay.ui.Volet
import net.jolabs40.relay.ui.cartes.ActionsCartes
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun EcranRelais(vm: RelaisViewModel) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val brouillons by vm.brouillons.collectAsStateWithLifecycle()
    val messages = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.erreurs.collect { erreur ->
            messages.showSnackbar(
                if (erreur == null) getString(Res.string.non_connecte) else getString(Res.string.erreur_relais, erreur),
            )
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(messages) }) { marges ->
        Column(Modifier.fillMaxSize().padding(marges)) {
            BandeauConnexion(etat.connexion)
            Row(Modifier.weight(1f)) {
                PanneauSessions(
                    projets = etat.projets,
                    projetChoisi = etat.projetChoisi,
                    choisirProjet = vm::choisirProjet,
                    sessions = etat.sessions,
                    volet = etat.volet,
                    ouvrir = vm::ouvrir,
                    modifier = Modifier.width(300.dp),
                )
                VerticalDivider()
                Box(Modifier.weight(1f)) {
                    val session = etat.sessionOuverte
                    if (session == null || etat.volet == Volet.Nouvelle) {
                        NouvelleSession(
                            projet = etat.projetChoisi,
                            modes = etat.modes,
                            historiques = etat.historiques,
                            demarrer = vm::nouvelleSession,
                            connecte = etat.connexion == EtatConnexion.Connecte,
                        )
                    } else {
                        val id = session.id
                        PanneauSession(
                            session = session,
                            modes = etat.modes,
                            brouillon = brouillons[id].orEmpty(),
                            changerBrouillon = { vm.brouillon(id, it) },
                            envoyer = { vm.envoyerPrompt(id) },
                            interrompre = { vm.interrompre(id) },
                            changerMode = { vm.changerMode(id, it) },
                            fermer = { vm.fermer(id) },
                            actions = remember(id) { actionsPour(vm, id) },
                        )
                    }
                }
            }
        }
    }
}

private fun actionsPour(vm: RelaisViewModel, session: String) = object : ActionsCartes {
    override fun repondreQuestions(demande: String, reponses: Map<String, String>) =
        vm.repondreQuestions(session, demande, reponses)

    override fun repondrePermission(demande: String, autoriser: Boolean, motif: String?) =
        vm.repondrePermission(session, demande, autoriser, motif)

    override fun repondrePlan(demande: String, approuver: Boolean, mode: String?, commentaire: String?) =
        vm.repondrePlan(session, demande, approuver, mode, commentaire)
}

@Composable
private fun BandeauConnexion(connexion: EtatConnexion) {
    when (connexion) {
        EtatConnexion.Connecte -> Unit
        EtatConnexion.Connexion -> Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(Res.string.connexion_en_cours),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        is EtatConnexion.Deconnecte -> Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                connexion.raison?.let { stringResource(Res.string.deconnecte_raison, it) } ?: stringResource(Res.string.deconnecte),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
