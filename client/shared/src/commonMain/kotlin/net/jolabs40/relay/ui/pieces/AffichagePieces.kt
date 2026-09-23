package net.jolabs40.relay.ui.pieces

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.jolabs40.relay.protocole.PieceAffichee
import net.jolabs40.relay.protocole.PieceJointe
import net.jolabs40.relay.ressources.Res
import net.jolabs40.relay.ressources.baseline_close_24
import net.jolabs40.relay.ressources.baseline_description_24
import net.jolabs40.relay.ressources.retirer
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Les pièces prêtes à partir, au-dessus du champ de saisie, chacune avec sa croix. */
@Composable
fun BarrePieces(pieces: List<PieceJointe>, retirer: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pieces.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.forEachIndexed { index, piece ->
            Box {
                Piece(piece.nom, piece.vignette, piece.estImage, cote = 64.dp)
                Surface(
                    onClick = { retirer(index) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.baseline_close_24),
                        stringResource(Res.string.retirer),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}

/** Les pièces d'un prompt déjà envoyé, dans le fil. */
@Composable
fun PiecesDuFil(pieces: List<PieceAffichee>, modifier: Modifier = Modifier) {
    if (pieces.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pieces.forEach { Piece(it.nom, it.vignette, it.estImage, cote = 96.dp) }
    }
}

@Composable
private fun Piece(nom: String, vignette: String?, estImage: Boolean, cote: Dp) {
    val image = remember(vignette) { vignette?.let(::decoder) }
    val forme = RoundedCornerShape(8.dp)
    if (estImage && image != null) {
        Image(
            bitmap = image,
            contentDescription = nom,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(cote).clip(forme),
        )
        return
    }
    Surface(
        shape = forme,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Res.drawable.baseline_description_24), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                nom,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 14.dp),
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun decoder(base64: String): ImageBitmap? = runCatching { Base64.decode(base64).decodeToImageBitmap() }.getOrNull()
