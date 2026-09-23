package net.jolabs40.relay.windows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ApplicationScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.jolabs40.relay.donnees.Alerte
import net.jolabs40.relay.donnees.ClientRelais
import net.jolabs40.relay.protocole.Evenement
import net.jolabs40.relay.protocole.SessionRelais
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.alerte_permission
import net.jolabs40.relay.ressources.alerte_plan
import net.jolabs40.relay.ressources.alerte_question
import net.jolabs40.relay.ressources.alerte_resultat
import net.jolabs40.relay.ressources.app_name
import net.jolabs40.relay.ressources.arreter_relais
import net.jolabs40.relay.ressources.ic_relay
import net.jolabs40.relay.ressources.ouvrir
import net.jolabs40.relay.ressources.quitter
import net.jolabs40.relay.ui.RelaisViewModel
import net.jolabs40.relay.ui.Volet
import net.jolabs40.relay.ui.ecrans.EcranRelais
import net.jolabs40.relay.ui.theme.ThemeRelay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Window as FenetreAwt

fun main() {
    // Dispatchers.Main est la file d'événements Swing (kotlinx-coroutines-swing).
    val portee = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val lanceur = LanceurRelais()
    val client = ClientRelais(portee, lanceur::trouver)
    client.demarrer()
    val vm = RelaisViewModel(client)

    application {
        CompositionLocalProvider(LocalLifecycleOwner provides CycleApplication) { Application(vm, client) }
    }
}

/**
 * La zone de notification vit hors de toute fenêtre, là où Compose Desktop ne fournit aucun
 * cycle de vie : celui de l'application, actif tant qu'elle tourne, en tient lieu.
 */
private object CycleApplication : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
}

@Composable
private fun ApplicationScope.Application(vm: RelaisViewModel, client: ClientRelais) {
    var visible by remember { mutableStateOf(true) }
    var fenetre by remember { mutableStateOf<FenetreAwt?>(null) }
    val tray = rememberTrayState()
    val etat by vm.etat.collectAsStateWithLifecycle()
    val enAttente = etat.sessions.count { it.etat == SessionRelais.ATTENTE }
    val nom = stringResource(Res.string.app_name)
    var derniereAlerte by remember { mutableStateOf<String?>(null) }

    fun montrer() {
        derniereAlerte?.let { vm.ouvrir(Volet.Session(it)) }
        derniereAlerte = null
        visible = true
        fenetre?.let { it.toFront(); it.requestFocus() }
    }

    LaunchedEffect(Unit) {
        vm.alertes.collect { alerte ->
            val regardee = visible && fenetre?.isFocused == true && etat.volet == Volet.Session(alerte.session.id)
            if (!regardee) {
                derniereAlerte = alerte.session.id
                tray.sendNotification(notification(alerte))
            }
        }
    }

    Tray(
        icon = painterResource(Res.drawable.ic_relay),
        state = tray,
        tooltip = if (enAttente > 0) "$nom ($enAttente)" else nom,
        onAction = ::montrer,
        menu = {
            Item(stringResource(Res.string.ouvrir), onClick = ::montrer)
            Item(stringResource(Res.string.arreter_relais), onClick = vm::arreterRelais)
            Separator()
            Item(stringResource(Res.string.quitter), onClick = {
                client.fermer()
                exitApplication()
            })
        },
    )

    // Fermer la fenêtre la range dans la zone de notification : les alertes continuent d'arriver.
    Window(
        visible = visible,
        onCloseRequest = { visible = false },
        title = if (enAttente > 0) "$nom ($enAttente)" else nom,
        icon = painterResource(Res.drawable.ic_relay),
        state = rememberWindowState(width = 1320.dp, height = 880.dp),
    ) {
        LaunchedEffect(window) { fenetre = window }
        ThemeRelay { EcranRelais(vm) }
    }
}

private suspend fun notification(alerte: Alerte): Notification {
    val projet = alerte.session.projet
    val evenement = alerte.evenement
    val (titre, texte) = when (evenement.type) {
        Evenement.QUESTION -> getString(Res.string.alerte_question, projet) to
            evenement.questions.joinToString(" · ") { it.question }
        Evenement.PERMISSION -> getString(Res.string.alerte_permission, projet) to evenement.resume.orEmpty()
        Evenement.PLAN -> getString(Res.string.alerte_plan, projet) to alerte.session.titre
        else -> getString(Res.string.alerte_resultat, projet) to evenement.texte.orEmpty()
    }
    return Notification(titre, texte.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(200))
}
