package net.jolabs40.relay.ui.pieces

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jolabs40.relay.protocole.PieceJointe
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Au-delà, l'API réduit l'image elle-même : autant ne pas envoyer les pixels en trop. */
private const val COTE_MAX = 1568
private const val TAILLE_MAX_IMAGE = 5 * 1024 * 1024
private const val TAILLE_MAX_FICHIER = 20 * 1024 * 1024
private const val COTE_VIGNETTE = 160

private val EXTENSIONS_IMAGE = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
)

private val presse get() = Toolkit.getDefaultToolkit().systemClipboard

actual fun pressePapierPortePieces(): Boolean = try {
    presse.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) || presse.isDataFlavorAvailable(DataFlavor.imageFlavor)
} catch (_: IllegalStateException) {
    false // presse-papier tenu par une autre application
}

actual suspend fun lirePressePapier(): PiecesLues = withContext(Dispatchers.IO) {
    try {
        lireTransferable(presse.getContents(null))
    } catch (_: Exception) {
        PiecesLues(emptyList())
    }
}

actual suspend fun choisirFichiers(): PiecesLues {
    // Le sélecteur natif de Windows, modal, sur le fil d'AWT.
    val fichiers = withContext(Dispatchers.Main) {
        val dialogue = FileDialog(null as Frame?, "", FileDialog.LOAD)
        dialogue.isMultipleMode = true
        dialogue.isVisible = true
        dialogue.files.toList()
    }
    return withContext(Dispatchers.IO) { lireFichiers(fichiers) }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
actual fun Modifier.deposerFichiers(survol: (Boolean) -> Unit, recevoir: (PiecesLues) -> Unit): Modifier = composed {
    val portee = rememberCoroutineScope()
    val cible = remember(survol, recevoir) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = survol(true)
            override fun onExited(event: DragAndDropEvent) = survol(false)
            override fun onEnded(event: DragAndDropEvent) = survol(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                survol(false)
                val transferable = event.awtTransferable
                portee.launch {
                    val lues = withContext(Dispatchers.IO) { lireTransferable(transferable) }
                    if (lues.pieces.isNotEmpty() || lues.refusees.isNotEmpty()) recevoir(lues)
                }
                return true
            }
        }
    }
    dragAndDropTarget(shouldStartDragAndDrop = { true }, target = cible)
}

// ---------------------------------------------------------------------------- lecture

private fun lireTransferable(transferable: Transferable): PiecesLues = when {
    // Des fichiers d'abord : l'Explorateur, et les captures que l'on copie comme fichiers.
    transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ->
        lireFichiers((transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>())
    transferable.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
        val image = transferable.getTransferData(DataFlavor.imageFlavor) as Image
        val nom = "capture-${LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))}.png"
        PiecesLues(listOf(pieceImage(enBuffered(image), nom, brut = null, typeMime = "image/png")))
    }
    else -> PiecesLues(emptyList())
}

internal fun lireFichiers(fichiers: List<File>): PiecesLues {
    val pieces = mutableListOf<PieceJointe>()
    val refusees = mutableListOf<String>()
    fichiers.filter { it.isFile }.forEach { fichier ->
        val piece = runCatching { lireFichier(fichier) }.getOrNull()
        if (piece != null) pieces += piece else refusees += fichier.name
    }
    return PiecesLues(pieces, refusees)
}

private fun lireFichier(fichier: File): PieceJointe? {
    val typeImage = EXTENSIONS_IMAGE[fichier.extension.lowercase()]
    if (typeImage != null) {
        val octets = fichier.readBytes()
        val image = runCatching { ImageIO.read(fichier) }.getOrNull()
        return when {
            image != null -> pieceImage(image, fichier.name, octets, typeImage)
            // WebP, ou GIF que Java ne lit pas : tel quel s'il tient, sans vignette.
            octets.size <= TAILLE_MAX_IMAGE -> PieceJointe(fichier.name, typeImage, base64(octets))
            else -> null
        }
    }
    if (fichier.length() > TAILLE_MAX_FICHIER) return null
    val type = runCatching { java.nio.file.Files.probeContentType(fichier.toPath()) }.getOrNull()
    return PieceJointe(fichier.name, type ?: "application/octet-stream", base64(fichier.readBytes()))
}

/**
 * Une image telle que Claude la lira : l'original s'il est déjà assez petit, sinon réduite à
 * [COTE_MAX] et réencodée en PNG — net pour une capture d'écran —, en JPEG s'il reste trop lourd.
 */
private fun pieceImage(image: BufferedImage, nom: String, brut: ByteArray?, typeMime: String): PieceJointe {
    val vignette = base64(jpeg(reduire(image, COTE_VIGNETTE), 0.8f))
    val assezPetite = maxOf(image.width, image.height) <= COTE_MAX
    if (brut != null && assezPetite && brut.size <= TAILLE_MAX_IMAGE && typeMime != "image/gif") {
        return PieceJointe(nom, typeMime, base64(brut), vignette)
    }
    val reduite = reduire(image, COTE_MAX)
    val png = ByteArrayOutputStream().also { ImageIO.write(reduite, "png", it) }.toByteArray()
    if (png.size <= TAILLE_MAX_IMAGE) return PieceJointe(nom.avecExtension("png"), "image/png", base64(png), vignette)
    return PieceJointe(nom.avecExtension("jpg"), "image/jpeg", base64(jpeg(reduite, 0.9f)), vignette)
}

private fun String.avecExtension(extension: String) = substringBeforeLast('.', this) + "." + extension

private fun reduire(image: BufferedImage, coteMax: Int): BufferedImage {
    val echelle = minOf(1.0, coteMax.toDouble() / maxOf(image.width, image.height))
    val largeur = maxOf(1, (image.width * echelle).toInt())
    val hauteur = maxOf(1, (image.height * echelle).toInt())
    // RGB sans transparence : le JPEG n'en a pas, et un PNG de capture n'en a pas besoin.
    val sortie = BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB)
    val g = sortie.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, largeur, hauteur)
    g.drawImage(image, 0, 0, largeur, hauteur, null)
    g.dispose()
    return sortie
}

private fun jpeg(image: BufferedImage, qualite: Float): ByteArray {
    val ecrivain = ImageIO.getImageWritersByFormatName("jpg").next()
    val sortie = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(sortie).use { flux ->
        ecrivain.output = flux
        val reglages = ecrivain.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = qualite
        }
        ecrivain.write(null, IIOImage(image, null, null), reglages)
    }
    ecrivain.dispose()
    return sortie.toByteArray()
}

private fun enBuffered(image: Image): BufferedImage {
    if (image is BufferedImage) return image
    val largeur = image.getWidth(null)
    val hauteur = image.getHeight(null)
    val sortie = BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_ARGB)
    sortie.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
    return sortie
}

@OptIn(ExperimentalEncodingApi::class)
private fun base64(octets: ByteArray): String = Base64.encode(octets)
