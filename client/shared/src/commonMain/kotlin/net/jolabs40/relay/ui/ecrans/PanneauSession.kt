package net.jolabs40.relay.ui.ecrans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.jolabs40.relay.protocole.PieceJointe
import net.jolabs40.relay.protocole.SessionRelais
import net.jolabs40.relay.ressources.baseline_attach_file_24
import net.jolabs40.relay.ressources.joindre
import net.jolabs40.relay.ui.pieces.BarrePieces
import net.jolabs40.relay.ui.pieces.collerPieces
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.attend_reponse
import net.jolabs40.relay.ressources.baseline_close_24
import net.jolabs40.relay.ressources.baseline_send_24
import net.jolabs40.relay.ressources.baseline_stop_24
import net.jolabs40.relay.ressources.en_file
import net.jolabs40.relay.ressources.envoyer
import net.jolabs40.relay.ressources.fermer_session
import net.jolabs40.relay.ressources.interrompre
import net.jolabs40.relay.ressources.message_indice
import net.jolabs40.relay.ressources.reflexion
import net.jolabs40.relay.ressources.sans_titre
import net.jolabs40.relay.ressources.session_arretee
import net.jolabs40.relay.ui.cartes.ActionsCartes
import net.jolabs40.relay.ui.cartes.CarteEvenement
import net.jolabs40.relay.ui.couleurEtat
import net.jolabs40.relay.ui.libelleEtat
import net.jolabs40.relay.ui.libelleMode
import net.jolabs40.relay.ui.toucheEnvoi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PanneauSession(
    session: SessionRelais,
    modes: List<String>,
    brouillon: String,
    changerBrouillon: (String) -> Unit,
    pieces: List<PieceJointe>,
    retirerPiece: (Int) -> Unit,
    collerPieces: () -> Unit,
    parcourirPieces: () -> Unit,
    envoyer: () -> Unit,
    interrompre: () -> Unit,
    changerMode: (String) -> Unit,
    fermer: () -> Unit,
    actions: ActionsCartes,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        EnTete(session, modes, changerMode, fermer)
        HorizontalDivider()

        val liste = rememberLazyListState()
        // Le fil suit la fin : une nouvelle carte doit se voir sans défiler.
        LaunchedEffect(session.id, session.evenements.size) {
            if (session.evenements.isNotEmpty()) liste.animateScrollToItem(session.evenements.lastIndex)
        }
        LazyColumn(
            state = liste,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(session.evenements, key = { it.id }) { evenement -> CarteEvenement(evenement, actions) }
        }

        BarreActivite(session, interrompre)
        ZoneSaisie(session, brouillon, changerBrouillon, pieces, retirerPiece, collerPieces, parcourirPieces, envoyer)
    }
}

@Composable
private fun EnTete(session: SessionRelais, modes: List<String>, changerMode: (String) -> Unit, fermer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                session.projet,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                session.titre.ifBlank { stringResource(Res.string.sans_titre) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            libelleEtat(session.etat),
            style = MaterialTheme.typography.labelMedium,
            color = couleurEtat(session.etat),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        var menu by remember { mutableStateOf(false) }
        Box {
            AssistChip(onClick = { menu = true }, label = { Text(libelleMode(session.mode)) })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                modes.forEach { mode ->
                    DropdownMenuItem(text = { Text(libelleMode(mode)) }, onClick = { changerMode(mode); menu = false })
                }
            }
        }
        Infobulle(stringResource(Res.string.fermer_session)) {
            IconButton(onClick = fermer) { Icon(painterResource(Res.drawable.baseline_close_24), stringResource(Res.string.fermer_session)) }
        }
    }
}

@Composable
private fun BarreActivite(session: SessionRelais, interrompre: () -> Unit) {
    val occupe = session.etat == SessionRelais.TRAVAILLE || session.etat == SessionRelais.DEMARRAGE
    val message = when {
        session.etat == SessionRelais.ERREUR -> stringResource(Res.string.session_arretee)
        session.etat == SessionRelais.ATTENTE -> stringResource(Res.string.attend_reponse)
        occupe -> session.activite.ifBlank { stringResource(Res.string.reflexion) }
        else -> null
    } ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (occupe) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = couleurEtat(session.etat),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (session.enFile > 0) {
                Text(stringResource(Res.string.en_file, session.enFile), style = MaterialTheme.typography.labelSmall)
            }
            if (occupe || session.etat == SessionRelais.ATTENTE) {
                OutlinedButton(onClick = interrompre, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Icon(painterResource(Res.drawable.baseline_stop_24), null, Modifier.size(16.dp))
                    Text(stringResource(Res.string.interrompre), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ZoneSaisie(
    session: SessionRelais,
    brouillon: String,
    changer: (String) -> Unit,
    pieces: List<PieceJointe>,
    retirerPiece: (Int) -> Unit,
    collerPieces: () -> Unit,
    parcourirPieces: () -> Unit,
    envoyer: () -> Unit,
) {
    val active = session.etat != SessionRelais.ERREUR
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BarrePieces(pieces, retirerPiece, Modifier.padding(start = 52.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Infobulle(stringResource(Res.string.joindre)) {
                IconButton(onClick = parcourirPieces, enabled = active) {
                    Icon(painterResource(Res.drawable.baseline_attach_file_24), stringResource(Res.string.joindre))
                }
            }
            OutlinedTextField(
                value = brouillon,
                onValueChange = changer,
                enabled = active,
                placeholder = { Text(stringResource(Res.string.message_indice)) },
                maxLines = 8,
                modifier = Modifier.weight(1f).widthIn(max = 820.dp).collerPieces(collerPieces).toucheEnvoi(envoyer),
            )
            Infobulle(stringResource(Res.string.envoyer)) {
                FilledIconButton(onClick = envoyer, enabled = active && (brouillon.isNotBlank() || pieces.isNotEmpty())) {
                    Icon(painterResource(Res.drawable.baseline_send_24), stringResource(Res.string.envoyer))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Infobulle(texte: String, contenu: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(texte) } },
        state = rememberTooltipState(),
        content = contenu,
    )
}
