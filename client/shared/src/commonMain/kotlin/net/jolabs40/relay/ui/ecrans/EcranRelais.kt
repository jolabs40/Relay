package net.jolabs40.relay.ui.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import net.jolabs40.relay.protocole.VERSION_PROTOCOLE
import net.jolabs40.relay.ressources.deposer_ici
import net.jolabs40.relay.ressources.relais_ancien
import net.jolabs40.relay.ressources.pieces_refusees
import net.jolabs40.relay.ui.pieces.deposerFichiers
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
    val pieces by vm.pieces.collectAsStateWithLifecycle()
    val versionRelais by vm.versionRelais.collectAsStateWithLifecycle()
    val messages = remember { SnackbarHostState() }
    var survol by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.refus.collect { noms -> messages.showSnackbar(getString(Res.string.pieces_refusees, noms.joinToString(", "))) }
    }

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
            if (etat.connexion == EtatConnexion.Connecte && versionRelais < VERSION_PROTOCOLE) BandeauRelaisAncien()
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
                val session = etat.sessionOuverte?.takeIf { etat.volet != Volet.Nouvelle }
                // Les fichiers déposés rejoignent la session ouverte, ou la nouvelle en préparation.
                val cle = session?.id ?: RelaisViewModel.CLE_NOUVELLE
                Box(Modifier.weight(1f).deposerFichiers(survol = { survol = it }, recevoir = { vm.ajouterPieces(cle, it) })) {
                    if (session == null) {
                        NouvelleSession(
                            projet = etat.projetChoisi,
                            modes = etat.modes,
                            historiques = etat.historiques,
                            pieces = pieces[cle].orEmpty(),
                            retirerPiece = { vm.retirerPiece(cle, it) },
                            collerPieces = { vm.collerPieces(cle) },
                            parcourirPieces = { vm.parcourirPieces(cle) },
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
                            pieces = pieces[id].orEmpty(),
                            retirerPiece = { vm.retirerPiece(id, it) },
                            collerPieces = { vm.collerPieces(id) },
                            parcourirPieces = { vm.parcourirPieces(id) },
                            envoyer = { vm.envoyerPrompt(id) },
                            interrompre = { vm.interrompre(id) },
                            changerMode = { vm.changerMode(id, it) },
                            fermer = { vm.fermer(id) },
                            actions = remember(id) { actionsPour(vm, id) },
                        )
                    }
                    if (survol) VoileDepot()
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
private fun BoxScope.VoileDepot() {
    Box(
        Modifier.matchParentSize().padding(12.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(Res.string.deposer_ici), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BandeauRelaisAncien() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.relais_ancien),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
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
