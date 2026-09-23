package net.jolabs40.relay.ui.ecrans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedButton
import net.jolabs40.relay.protocole.PieceJointe
import net.jolabs40.relay.protocole.Projet
import net.jolabs40.relay.ressources.baseline_attach_file_24
import net.jolabs40.relay.ressources.joindre
import net.jolabs40.relay.ui.pieces.BarrePieces
import net.jolabs40.relay.ui.pieces.collerPieces
import net.jolabs40.relay.protocole.SessionPassee
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.baseline_folder_open_24
import net.jolabs40.relay.ressources.baseline_history_24
import net.jolabs40.relay.ressources.choisir_projet
import net.jolabs40.relay.ressources.demarrer
import net.jolabs40.relay.ressources.mode
import net.jolabs40.relay.ressources.nouvelle_session
import net.jolabs40.relay.ressources.projet
import net.jolabs40.relay.ressources.prompt_indice
import net.jolabs40.relay.ressources.prompt_reprise_indice
import net.jolabs40.relay.ressources.raccourci_envoi
import net.jolabs40.relay.ressources.reprise
import net.jolabs40.relay.ressources.reprise_chargement
import net.jolabs40.relay.ressources.reprise_nouvelle
import net.jolabs40.relay.ressources.reprise_vide
import net.jolabs40.relay.ui.Modes
import net.jolabs40.relay.ui.dateCourte
import net.jolabs40.relay.ui.detailMode
import net.jolabs40.relay.ui.libelleMode
import net.jolabs40.relay.ui.toucheEnvoi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun NouvelleSession(
    projet: Projet?,
    modes: List<String>,
    historiques: Map<String, List<SessionPassee>>,
    pieces: List<PieceJointe>,
    retirerPiece: (Int) -> Unit,
    collerPieces: () -> Unit,
    parcourirPieces: () -> Unit,
    demarrer: (Projet, String, String, String?) -> Unit,
    connecte: Boolean,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(Modes.AUTONOME) }
    var reprendre by rememberSaveable(projet?.chemin) { mutableStateOf<String?>(null) }
    var prompt by rememberSaveable { mutableStateOf("") }

    val choisi = projet
    val peutDemarrer = connecte && choisi != null && (prompt.isNotBlank() || pieces.isNotEmpty() || reprendre != null)
    val lancer = { if (peutDemarrer) demarrer(choisi!!, prompt, mode, reprendre) }

    Row(modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.Center) {
        Column(Modifier.weight(1f).widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.nouvelle_session), style = MaterialTheme.typography.headlineSmall)
            if (choisi == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Res.drawable.baseline_folder_open_24), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(Res.string.choisir_projet),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(choisi.nom, style = MaterialTheme.typography.titleMedium)
                Text(choisi.chemin, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(stringResource(Res.string.mode), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { m ->
                    FilterChip(selected = m == mode, onClick = { mode = m }, label = { Text(libelleMode(m)) })
                }
            }
            Text(detailMode(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (choisi != null) {
                Text(stringResource(Res.string.reprise), style = MaterialTheme.typography.titleSmall)
                ListeReprise(historiques[choisi.chemin], reprendre) { reprendre = it }
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = {
                    Text(stringResource(if (reprendre != null) Res.string.prompt_reprise_indice else Res.string.prompt_indice))
                },
                supportingText = { Text(stringResource(Res.string.raccourci_envoi)) },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().collerPieces(collerPieces).toucheEnvoi(lancer),
            )
            BarrePieces(pieces, retirerPiece)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = lancer, enabled = peutDemarrer) { Text(stringResource(Res.string.demarrer)) }
                OutlinedButton(onClick = parcourirPieces) {
                    Icon(painterResource(Res.drawable.baseline_attach_file_24), null, Modifier.size(18.dp))
                    Text(stringResource(Res.string.joindre), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ListeReprise(passees: List<SessionPassee>?, choisie: String?, choisir: (String?) -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
    ) {
        LazyColumn {
            item {
                LigneReprise(stringResource(Res.string.reprise_nouvelle), null, choisie == null) { choisir(null) }
            }
            when {
                passees == null -> item { Text(stringResource(Res.string.reprise_chargement), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                passees.isEmpty() -> item { Text(stringResource(Res.string.reprise_vide), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                else -> items(passees, key = { it.id }) { passee ->
                    LigneReprise(passee.resume.ifBlank { passee.id }, passee.modifieeA, choisie == passee.id) { choisir(passee.id) }
                }
            }
        }
    }
}

@Composable
private fun LigneReprise(texte: String, date: Long?, choisie: Boolean, choisir: () -> Unit) {
    Surface(
        onClick = choisir,
        color = if (choisie) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Res.drawable.baseline_history_24), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(texte, Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (date != null) {
                Text(dateCourte(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
