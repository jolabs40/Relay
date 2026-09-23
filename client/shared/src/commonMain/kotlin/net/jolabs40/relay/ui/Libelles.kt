package net.jolabs40.relay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.jolabs40.relay.protocole.SessionRelais
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.etat_attente
import net.jolabs40.relay.ressources.etat_demarrage
import net.jolabs40.relay.ressources.etat_erreur
import net.jolabs40.relay.ressources.etat_inactive
import net.jolabs40.relay.ressources.etat_travaille
import net.jolabs40.relay.ressources.mode_accept_edits
import net.jolabs40.relay.ressources.mode_accept_edits_detail
import net.jolabs40.relay.ressources.mode_bypass
import net.jolabs40.relay.ressources.mode_bypass_detail
import net.jolabs40.relay.ressources.mode_default
import net.jolabs40.relay.ressources.mode_default_detail
import net.jolabs40.relay.ressources.mode_plan
import net.jolabs40.relay.ressources.mode_plan_detail
import net.jolabs40.relay.ui.theme.CouleursEtat
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Les modes de permission du SDK, dans l'ordre où on les propose. */
object Modes {
    const val DEFAUT = "default"
    const val EDITIONS = "acceptEdits"
    const val PLAN = "plan"
    const val AUTONOME = "bypassPermissions"

    /** Après approbation d'un plan, on ne repropose pas le mode plan. */
    val APRES_PLAN = listOf(EDITIONS, AUTONOME, DEFAUT)
}

private fun ressourceMode(mode: String): Pair<StringResource, StringResource>? = when (mode) {
    Modes.DEFAUT -> Res.string.mode_default to Res.string.mode_default_detail
    Modes.EDITIONS -> Res.string.mode_accept_edits to Res.string.mode_accept_edits_detail
    Modes.PLAN -> Res.string.mode_plan to Res.string.mode_plan_detail
    Modes.AUTONOME -> Res.string.mode_bypass to Res.string.mode_bypass_detail
    else -> null
}

@Composable
fun libelleMode(mode: String): String = ressourceMode(mode)?.let { stringResource(it.first) } ?: mode

@Composable
fun detailMode(mode: String): String = ressourceMode(mode)?.let { stringResource(it.second) } ?: ""

@Composable
fun libelleEtat(etat: String): String = stringResource(
    when (etat) {
        SessionRelais.ATTENTE -> Res.string.etat_attente
        SessionRelais.TRAVAILLE -> Res.string.etat_travaille
        SessionRelais.INACTIVE -> Res.string.etat_inactive
        SessionRelais.ERREUR -> Res.string.etat_erreur
        else -> Res.string.etat_demarrage
    },
)

fun couleurEtat(etat: String): Color = when (etat) {
    SessionRelais.ATTENTE -> CouleursEtat.attente
    SessionRelais.TRAVAILLE, SessionRelais.DEMARRAGE -> CouleursEtat.travaille
    SessionRelais.ERREUR -> CouleursEtat.erreur
    else -> CouleursEtat.inactive
}

/** « 42 s », « 3 min 05 s » : la durée d'un tour, sans dépendre d'un formateur JVM. */
fun duree(ms: Long): String {
    val secondes = ms / 1000
    return if (secondes < 60) "$secondes s" else "${secondes / 60} min ${(secondes % 60).toString().padStart(2, '0')} s"
}
