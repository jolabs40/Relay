package net.jolabs40.relay.ui.cartes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.jolabs40.relay.protocole.Evenement
import net.jolabs40.relay.protocole.Question
import net.jolabs40.relay.protocole.texte
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.annule
import net.jolabs40.relay.ressources.approuver_executer
import net.jolabs40.relay.ressources.autoriser
import net.jolabs40.relay.ressources.autorise
import net.jolabs40.relay.ressources.autre
import net.jolabs40.relay.ressources.autre_indice
import net.jolabs40.relay.ressources.baseline_assignment_24
import net.jolabs40.relay.ressources.baseline_error_24
import net.jolabs40.relay.ressources.baseline_info_24
import net.jolabs40.relay.ressources.baseline_question_answer_24
import net.jolabs40.relay.ressources.baseline_security_24
import net.jolabs40.relay.ressources.baseline_task_alt_24
import net.jolabs40.relay.ressources.commentaire_plan_indice
import net.jolabs40.relay.ressources.demander_changements
import net.jolabs40.relay.ressources.details_tour
import net.jolabs40.relay.ressources.envoyer_reponse
import net.jolabs40.relay.ressources.executer_en
import net.jolabs40.relay.ressources.motif_indice
import net.jolabs40.relay.ressources.permission_titre
import net.jolabs40.relay.ressources.plan_approuve
import net.jolabs40.relay.ressources.plan_refuse
import net.jolabs40.relay.ressources.plan_titre
import net.jolabs40.relay.ressources.question_titre
import net.jolabs40.relay.ressources.refuse
import net.jolabs40.relay.ressources.refuse_motif
import net.jolabs40.relay.ressources.refuser
import net.jolabs40.relay.ressources.repondre
import net.jolabs40.relay.ressources.reponse_donnee
import net.jolabs40.relay.ressources.resultat_erreur
import net.jolabs40.relay.ressources.resultat_titre
import net.jolabs40.relay.ui.Modes
import net.jolabs40.relay.ui.duree
import net.jolabs40.relay.ui.libelleMode
import net.jolabs40.relay.ui.detailMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Ce qu'une carte peut renvoyer au relais. */
interface ActionsCartes {
    fun repondreQuestions(demande: String, reponses: Map<String, String>)
    fun repondrePermission(demande: String, autoriser: Boolean, motif: String?)
    fun repondrePlan(demande: String, approuver: Boolean, mode: String?, commentaire: String?)
}

private val LARGEUR_MAX = 820.dp

@Composable
fun CarteEvenement(evenement: Evenement, actions: ActionsCartes) {
    when (evenement.type) {
        Evenement.PROMPT -> BullePrompt(evenement.texte.orEmpty())
        Evenement.QUESTION -> CarteQuestion(evenement, actions)
        Evenement.PERMISSION -> CartePermission(evenement, actions)
        Evenement.PLAN -> CartePlan(evenement, actions)
        Evenement.RESULTAT -> CarteResultat(evenement)
        else -> LigneInfo(evenement)
    }
}

// ---------------------------------------------------------------------------- briques

@Composable
private fun Cadre(
    icone: DrawableResource,
    titre: String,
    enAttente: Boolean,
    couleur: Color = MaterialTheme.colorScheme.primary,
    contenu: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.widthIn(max = LARGEUR_MAX).fillMaxWidth(),
        border = BorderStroke(if (enAttente) 2.dp else 1.dp, if (enAttente) couleur else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icone), null, tint = couleur, modifier = Modifier.size(20.dp))
                Text(titre, style = MaterialTheme.typography.titleSmall, color = couleur)
            }
            contenu()
        }
    }
}

@Composable
private fun Conclusion(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun BlocCode(texte: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                Text(
                    texte,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun TexteMarkdown(texte: String) {
    // Les titres par défaut suivent l'échelle « display » : une affiche au milieu d'une carte.
    val t = MaterialTheme.typography
    SelectionContainer {
        Markdown(
            content = texte,
            typography = markdownTypography(h1 = t.titleLarge, h2 = t.titleMedium, h3 = t.titleSmall, h4 = t.titleSmall, h5 = t.labelLarge, h6 = t.labelLarge),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun JsonObject?.annulation(): String? = this?.texte("annule")

// ---------------------------------------------------------------------------- prompt

@Composable
private fun BullePrompt(texte: String) {
    Row(Modifier.widthIn(max = LARGEUR_MAX).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            SelectionContainer {
                Text(texte, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

// ---------------------------------------------------------------------------- question

@Composable
private fun CarteQuestion(evenement: Evenement, actions: ActionsCartes) {
    val choix = remember(evenement.demande) { mutableStateMapOf<String, Set<String>>() }
    val autres = remember(evenement.demande) { mutableStateMapOf<String, String>() }

    fun reponse(question: Question): String {
        val retenus = (choix[question.question] ?: emptySet()).filter { it != AUTRE }
        val libre = autres[question.question].orEmpty().trim()
            .takeIf { AUTRE in (choix[question.question] ?: emptySet()) && it.isNotEmpty() }
        return (retenus + listOfNotNull(libre)).joinToString(", ")
    }

    Cadre(Res.drawable.baseline_question_answer_24, stringResource(Res.string.question_titre), evenement.enAttente) {
        val donnees = evenement.reponse?.get("reponses")?.jsonObject
        evenement.questions.forEach { question ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (question.header.isNotBlank()) {
                    Text(question.header.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(question.question, style = MaterialTheme.typography.titleMedium)
                if (evenement.enAttente) {
                    val retenus = choix[question.question] ?: emptySet()
                    question.options.forEach { option ->
                        LigneOption(
                            libelle = option.label,
                            description = option.description,
                            multiple = question.multiSelect,
                            coche = option.label in retenus,
                            basculer = { choix[question.question] = basculer(retenus, option.label, question.multiSelect) },
                        )
                    }
                    LigneOption(
                        libelle = stringResource(Res.string.autre),
                        description = "",
                        multiple = question.multiSelect,
                        coche = AUTRE in retenus,
                        basculer = { choix[question.question] = basculer(retenus, AUTRE, question.multiSelect) },
                    )
                    if (AUTRE in retenus) {
                        OutlinedTextField(
                            value = autres[question.question].orEmpty(),
                            onValueChange = { autres[question.question] = it },
                            placeholder = { Text(stringResource(Res.string.autre_indice)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    val donnee = donnees?.texte(question.question)
                    val annulation = evenement.reponse.annulation()
                    when {
                        donnee != null -> Conclusion(stringResource(Res.string.reponse_donnee, donnee))
                        annulation != null -> Conclusion(stringResource(Res.string.annule, annulation))
                    }
                }
            }
        }
        if (evenement.enAttente) {
            val complet = evenement.questions.all { reponse(it).isNotEmpty() }
            Button(
                onClick = { actions.repondreQuestions(evenement.demande!!, evenement.questions.associate { it.question to reponse(it) }) },
                enabled = complet,
            ) { Text(stringResource(Res.string.repondre)) }
        }
    }
}

private const val AUTRE = "\u0000autre"

private fun basculer(retenus: Set<String>, valeur: String, multiple: Boolean): Set<String> = when {
    !multiple -> setOf(valeur)
    valeur in retenus -> retenus - valeur
    else -> retenus + valeur
}

@Composable
private fun LigneOption(libelle: String, description: String, multiple: Boolean, coche: Boolean, basculer: () -> Unit) {
    val forme = RoundedCornerShape(10.dp)
    val modificateur = if (multiple) {
        Modifier.toggleable(value = coche, role = Role.Checkbox, onValueChange = { basculer() })
    } else {
        Modifier.selectable(selected = coche, role = Role.RadioButton, onClick = basculer)
    }
    Surface(
        shape = forme,
        color = if (coche) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, if (coche) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().then(modificateur),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (multiple) Checkbox(checked = coche, onCheckedChange = null) else RadioButton(selected = coche, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(libelle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- permission

@Composable
private fun CartePermission(evenement: Evenement, actions: ActionsCartes) {
    var refus by remember(evenement.demande) { mutableStateOf(false) }
    var motif by remember(evenement.demande) { mutableStateOf("") }
    Cadre(
        Res.drawable.baseline_security_24,
        stringResource(Res.string.permission_titre),
        evenement.enAttente,
        couleur = MaterialTheme.colorScheme.tertiary,
    ) {
        Text(evenement.resume ?: evenement.outil.orEmpty(), style = MaterialTheme.typography.titleMedium)
        if (!evenement.detail.isNullOrBlank()) BlocCode(evenement.detail)
        if (evenement.enAttente) {
            if (refus) {
                OutlinedTextField(
                    value = motif,
                    onValueChange = { motif = it },
                    placeholder = { Text(stringResource(Res.string.motif_indice)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.repondrePermission(evenement.demande!!, true, null) }) {
                    Text(stringResource(Res.string.autoriser))
                }
                OutlinedButton(onClick = {
                    if (refus) actions.repondrePermission(evenement.demande!!, false, motif) else refus = true
                }) {
                    Text(stringResource(if (refus) Res.string.envoyer_reponse else Res.string.refuser))
                }
            }
        } else {
            val reponse = evenement.reponse
            val annulation = reponse.annulation()
            val message = reponse?.texte("message")
            Conclusion(
                when {
                    annulation != null -> stringResource(Res.string.annule, annulation)
                    reponse?.texte("autoriser") == "true" -> stringResource(Res.string.autorise)
                    !message.isNullOrBlank() -> stringResource(Res.string.refuse_motif, message)
                    else -> stringResource(Res.string.refuse)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------- plan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartePlan(evenement: Evenement, actions: ActionsCartes) {
    var mode by remember(evenement.demande) { mutableStateOf(Modes.EDITIONS) }
    var changements by remember(evenement.demande) { mutableStateOf(false) }
    var commentaire by remember(evenement.demande) { mutableStateOf("") }
    Cadre(Res.drawable.baseline_assignment_24, stringResource(Res.string.plan_titre), evenement.enAttente) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(12.dp)) { TexteMarkdown(evenement.plan.orEmpty()) }
        }
        if (evenement.enAttente) {
            if (changements) {
                OutlinedTextField(
                    value = commentaire,
                    onValueChange = { commentaire = it },
                    placeholder = { Text(stringResource(Res.string.commentaire_plan_indice)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { actions.repondrePlan(evenement.demande!!, false, null, commentaire) },
                        enabled = commentaire.isNotBlank(),
                    ) { Text(stringResource(Res.string.envoyer_reponse)) }
                    OutlinedButton(onClick = { changements = false }) { Text(stringResource(Res.string.approuver_executer)) }
                }
            } else {
                var ouvert by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = ouvert, onExpandedChange = { ouvert = it }) {
                    TextField(
                        value = libelleMode(mode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.executer_en)) },
                        supportingText = { Text(detailMode(mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = ouvert) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).widthIn(min = 320.dp),
                    )
                    ExposedDropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
                        Modes.APRES_PLAN.forEach { choix ->
                            DropdownMenuItem(
                                text = { Text(libelleMode(choix)) },
                                onClick = { mode = choix; ouvert = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { actions.repondrePlan(evenement.demande!!, true, mode, null) }) {
                        Text(stringResource(Res.string.approuver_executer))
                    }
                    OutlinedButton(onClick = { changements = true }) { Text(stringResource(Res.string.demander_changements)) }
                }
            }
        } else {
            val reponse = evenement.reponse
            val annulation = reponse.annulation()
            Conclusion(
                when {
                    annulation != null -> stringResource(Res.string.annule, annulation)
                    reponse?.texte("approuver") == "true" -> stringResource(Res.string.plan_approuve) +
                        reponse.texte("mode")?.let { " · ${libelleMode(it)}" }.orEmpty()
                    else -> stringResource(Res.string.plan_refuse, reponse?.texte("message").orEmpty())
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------- résultat

@Composable
private fun CarteResultat(evenement: Evenement) {
    val couleur = if (evenement.erreur) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Cadre(
        if (evenement.erreur) Res.drawable.baseline_error_24 else Res.drawable.baseline_task_alt_24,
        stringResource(if (evenement.erreur) Res.string.resultat_erreur else Res.string.resultat_titre),
        enAttente = false,
        couleur = couleur,
    ) {
        TexteMarkdown(evenement.texte.orEmpty())
        val ms = evenement.dureeMs
        if (ms != null) {
            Text(
                stringResource(Res.string.details_tour, duree(ms), evenement.tours ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------- info

@Composable
private fun LigneInfo(evenement: Evenement) {
    Row(
        Modifier.widthIn(max = LARGEUR_MAX).fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val couleur = if (evenement.erreur) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(painterResource(Res.drawable.baseline_info_24), null, tint = couleur, modifier = Modifier.size(16.dp))
        Text(
            evenement.texte.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = couleur,
            modifier = Modifier.padding(start = 6.dp).background(Color.Transparent),
        )
    }
}
