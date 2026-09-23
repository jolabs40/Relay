package net.jolabs40.relay.ui.cartes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import net.jolabs40.relay.protocole.Bilan
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.bilan_actions
import net.jolabs40.relay.ressources.bilan_crees
import net.jolabs40.relay.ressources.bilan_echecs
import net.jolabs40.relay.ressources.bilan_lignes
import net.jolabs40.relay.ressources.bilan_lignes_ajoutees
import net.jolabs40.relay.ressources.bilan_lignes_retirees
import net.jolabs40.relay.ressources.bilan_modifies
import net.jolabs40.relay.ressources.bilan_tests_ecrits
import net.jolabs40.relay.ressources.bilan_tests_lances
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * « 3 fichiers modifiés · 1 créé · +120 −14 lignes · 2 tests écrits · 12 actions » : ce qu'un tour
 * a fait, en chiffres. Les compteurs à zéro se taisent ; les lignes gardent le vert et le rouge d'un diff.
 */
@Composable
fun LigneBilan(bilan: Bilan, modifier: Modifier = Modifier) {
    val morceaux = buildList {
        if (bilan.fichiersModifies > 0) add(pluralStringResource(Res.plurals.bilan_modifies, bilan.fichiersModifies, bilan.fichiersModifies))
        if (bilan.fichiersCrees > 0) add(pluralStringResource(Res.plurals.bilan_crees, bilan.fichiersCrees, bilan.fichiersCrees))
        if (bilan.lignesAjoutees + bilan.lignesRetirees > 0) add(LIGNES)
        if (bilan.testsEcrits > 0) add(pluralStringResource(Res.plurals.bilan_tests_ecrits, bilan.testsEcrits, bilan.testsEcrits))
        if (bilan.testsLances > 0) {
            val lances = pluralStringResource(Res.plurals.bilan_tests_lances, bilan.testsLances, bilan.testsLances)
            add(if (bilan.testsEchoues > 0) "$lances (${pluralStringResource(Res.plurals.bilan_echecs, bilan.testsEchoues, bilan.testsEchoues)})" else lances)
        }
        if (bilan.actions > 0) add(pluralStringResource(Res.plurals.bilan_actions, bilan.actions, bilan.actions))
    }
    if (morceaux.isEmpty()) return

    // Un côté à zéro se tait : « +581 lignes » plutôt que « +581 −0 lignes ».
    val lignes = stringResource(Res.string.bilan_lignes, bilan.lignesAjoutees, bilan.lignesRetirees)
    val lignesAjoutees = stringResource(Res.string.bilan_lignes_ajoutees, bilan.lignesAjoutees)
    val lignesRetirees = stringResource(Res.string.bilan_lignes_retirees, bilan.lignesRetirees)
    val plus = "+${bilan.lignesAjoutees}"
    val moins = "−${bilan.lignesRetirees}"
    val vert = MaterialTheme.colorScheme.secondary
    val rouge = MaterialTheme.colorScheme.error
    val texte = buildAnnotatedString {
        morceaux.forEachIndexed { i, morceau ->
            if (i > 0) append(" · ")
            if (morceau != LIGNES) {
                append(morceau)
                return@forEachIndexed
            }
            // Le « +N » et le « −N » de la chaîne traduite, colorés là où ils sont.
            var reste = when {
                bilan.lignesRetirees == 0 -> lignesAjoutees
                bilan.lignesAjoutees == 0 -> lignesRetirees
                else -> lignes
            }
            listOf(plus to vert, moins to rouge).forEach { (nombre, couleur) ->
                val position = reste.indexOf(nombre)
                if (position < 0) return@forEach
                append(reste.substring(0, position))
                withStyle(SpanStyle(color = couleur)) { append(nombre) }
                reste = reste.substring(position + nombre.length)
            }
            append(reste)
        }
    }
    Text(texte, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Repère la place des lignes dans la liste : leur texte, coloré, est composé à part. */
private const val LIGNES = "\u0000lignes"
