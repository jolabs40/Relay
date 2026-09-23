package net.jolabs40.relay.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Entrée envoie, Maj+Entrée va à la ligne — la convention des messageries sur un bureau. */
fun Modifier.toucheEnvoi(envoyer: () -> Unit): Modifier = onPreviewKeyEvent { evenement ->
    val entree = evenement.key == Key.Enter || evenement.key == Key.NumPadEnter
    if (entree && !evenement.isShiftPressed) {
        if (evenement.type == KeyEventType.KeyDown) envoyer()
        true
    } else {
        false
    }
}

/** « 23/09 14:05 » dans le fuseau de l'appareil. */
expect fun dateCourte(millis: Long): String

/** « 14:05 » pour aujourd'hui, « 22/09 14:05 » au-delà : l'heure d'un élément du fil. */
expect fun heure(millis: Long): String
