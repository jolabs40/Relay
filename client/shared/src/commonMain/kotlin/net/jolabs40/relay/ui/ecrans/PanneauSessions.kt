package net.jolabs40.relay.ui.ecrans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.jolabs40.relay.protocole.Projet
import net.jolabs40.relay.protocole.SessionRelais
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.aucun_projet
import net.jolabs40.relay.ressources.baseline_folder_open_24
import net.jolabs40.relay.ressources.projet
import net.jolabs40.relay.ressources.rechercher_projet
import net.jolabs40.relay.ressources.aucune_session
import net.jolabs40.relay.ressources.baseline_add_24
import net.jolabs40.relay.ressources.nouvelle_session
import net.jolabs40.relay.ressources.sans_titre
import net.jolabs40.relay.ressources.sessions
import net.jolabs40.relay.ui.Volet
import net.jolabs40.relay.ui.couleurEtat
import net.jolabs40.relay.ui.libelleEtat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun PanneauSessions(
    projets: List<Projet>,
    projetChoisi: Projet?,
    choisirProjet: (Projet) -> Unit,
    sessions: List<SessionRelais>,
    volet: Volet,
    ouvrir: (Volet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListeProjets(projets, projetChoisi, choisirProjet)
        FilledTonalButton(onClick = { ouvrir(Volet.Nouvelle) }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(Res.drawable.baseline_add_24), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.nouvelle_session))
        }
        Text(
            stringResource(Res.string.sessions),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        if (sessions.isEmpty()) {
            Text(
                stringResource(Res.string.aucune_session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(sessions, key = { it.id }) { session ->
                LigneSession(session, choisie = volet == Volet.Session(session.id)) { ouvrir(Volet.Session(session.id)) }
            }
        }
    }
}

/** Liste déroulante des projets ; taper filtre, choisir ouvre une nouvelle session sur ce projet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeProjets(projets: List<Projet>, choisi: Projet?, choisir: (Projet) -> Unit) {
    var ouvert by remember { mutableStateOf(false) }
    var saisie by remember { mutableStateOf("") }
    val filtres = remember(projets, saisie) {
        projets.filter { saisie.isBlank() || it.nom.contains(saisie.trim(), ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = ouvert,
        onExpandedChange = { ouvert = it; if (it) saisie = "" },
    ) {
        OutlinedTextField(
            value = if (ouvert) saisie else choisi?.nom.orEmpty(),
            onValueChange = { saisie = it; ouvert = true },
            label = { Text(stringResource(Res.string.projet)) },
            placeholder = { Text(stringResource(Res.string.rechercher_projet)) },
            leadingIcon = { Icon(painterResource(Res.drawable.baseline_folder_open_24), null, Modifier.size(20.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ouvert) },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
            if (filtres.isEmpty()) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.aucun_projet)) }, onClick = {}, enabled = false)
            }
            filtres.forEach { projet ->
                DropdownMenuItem(
                    text = { Text(projet.nom, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(painterResource(Res.drawable.baseline_folder_open_24), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = {
                        choisir(projet)
                        ouvert = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LigneSession(session: SessionRelais, choisie: Boolean, ouvrir: () -> Unit) {
    Surface(
        onClick = ouvrir,
        shape = RoundedCornerShape(10.dp),
        color = if (choisie) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(couleurEtat(session.etat), CircleShape))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    session.projet,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    session.titre.ifBlank { stringResource(Res.string.sans_titre) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (session.etat == SessionRelais.ATTENTE) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    session.activite.ifBlank { libelleEtat(session.etat) },
                    style = MaterialTheme.typography.labelSmall,
                    color = couleurEtat(session.etat),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
