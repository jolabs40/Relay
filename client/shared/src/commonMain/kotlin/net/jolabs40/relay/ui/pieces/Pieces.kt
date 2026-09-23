package net.jolabs40.relay.ui.pieces

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import net.jolabs40.relay.protocole.PieceJointe

/*
 * Joindre des fichiers : presse-papier, sélecteur, glisser-déposer. Chaque plateforme les lit à sa
 * façon (AWT sur le bureau) ; elle rend des pièces déjà prêtes — images réduites à ce que Claude
 * lit, vignette fabriquée —, et le nom de celles qu'elle a dû écarter.
 */

/** Ce qu'une prise de fichiers a donné : les pièces prêtes, et les noms écartés (illisibles, trop lourds). */
data class PiecesLues(val pieces: List<PieceJointe>, val refusees: List<String> = emptyList())

/** Vrai si le presse-papier porte une image ou des fichiers : Ctrl+V les joint au lieu de coller du texte. */
expect fun pressePapierPortePieces(): Boolean

expect suspend fun lirePressePapier(): PiecesLues

expect suspend fun choisirFichiers(): PiecesLues

/** Zone où déposer des fichiers ; [survol] suit l'entrée et la sortie d'un glisser. */
expect fun Modifier.deposerFichiers(survol: (Boolean) -> Unit, recevoir: (PiecesLues) -> Unit): Modifier

/** Ctrl+V joint l'image ou les fichiers du presse-papier ; s'il ne porte que du texte, il se colle. */
fun Modifier.collerPieces(coller: () -> Unit): Modifier = onPreviewKeyEvent { evenement ->
    val ctrlV = evenement.isCtrlPressed && evenement.key == Key.V
    if (ctrlV && evenement.type == KeyEventType.KeyDown && pressePapierPortePieces()) {
        coller()
        true
    } else {
        false
    }
}
