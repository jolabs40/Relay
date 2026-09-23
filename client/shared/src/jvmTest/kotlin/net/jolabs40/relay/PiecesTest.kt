package net.jolabs40.relay

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.jolabs40.relay.protocole.Commandes
import net.jolabs40.relay.protocole.MessageRelais
import net.jolabs40.relay.protocole.PieceJointe
import net.jolabs40.relay.protocole.decoderMessage
import net.jolabs40.relay.ui.pieces.lireFichiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class PiecesTest {

    private val dossier: File = Files.createTempDirectory("relay-pieces").toFile().apply { deleteOnExit() }

    private fun image(nom: String, largeur: Int, hauteur: Int): File =
        File(dossier, nom).also { ImageIO.write(BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB), "png", it) }

    @Test
    fun `une petite image part telle quelle, avec sa vignette`() {
        val fichier = image("petite.png", 400, 300)
        val piece = lireFichiers(listOf(fichier)).pieces.single()
        assertEquals("image/png", piece.typeMime)
        assertTrue(piece.estImage)
        assertTrue(Base64.decode(piece.donnees).contentEquals(fichier.readBytes()))
        val vignette = ImageIO.read(ByteArrayInputStream(Base64.decode(piece.vignette!!)))
        assertEquals(160, maxOf(vignette.width, vignette.height))
    }

    @Test
    fun `une grande capture est reduite a 1568 pixels`() {
        val piece = lireFichiers(listOf(image("ecran.png", 3840, 2160))).pieces.single()
        val reduite = ImageIO.read(ByteArrayInputStream(Base64.decode(piece.donnees)))
        assertEquals(1568, reduite.width)
        assertEquals(882, reduite.height)
    }

    @Test
    fun `un fichier ordinaire part tel quel sans vignette, un trop lourd est ecarte`() {
        val journal = File(dossier, "build.log").apply { writeText("BUILD SUCCESSFUL") }
        val lourd = File(dossier, "enorme.bin").apply { outputStream().use { it.channel.position(21L * 1024 * 1024); it.write(0) } }
        val lues = lireFichiers(listOf(journal, lourd))
        val piece = lues.pieces.single()
        assertEquals("build.log", piece.nom)
        assertFalse(piece.estImage)
        assertNull(piece.vignette)
        assertEquals("BUILD SUCCESSFUL", Base64.decode(piece.donnees).decodeToString())
        assertEquals(listOf("enorme.bin"), lues.refusees)
    }

    @Test
    fun `les pieces voyagent dans la commande et reviennent dans le fil`() {
        val commande = Commandes.envoyer("s", "Regarde", listOf(PieceJointe("a.png", "image/png", "QUJD", "dmln")))
        val piece = commande.getValue("pieces").jsonArray.single().jsonObject
        assertEquals("image/png", piece.getValue("type_mime").jsonPrimitive.content)
        assertEquals("QUJD", piece.getValue("donnees").jsonPrimitive.content)
        // Sans pièce, la clé n'existe pas : le relais d'avant les pièces jointes l'ignorerait de toute façon.
        assertFalse("pieces" in Commandes.envoyer("s", "x"))

        val session = (decoderMessage(
            """{"type":"session","session":{"id":"s","projet":"P","cwd":"C:/P","etat":"inactive","mode":"default",
               "evenements":[{"id":1,"type":"prompt","texte":"","pieces":[{"nom":"a.png","type_mime":"image/png","taille":3,"vignette":"dmln"}]}]}}""",
        ) as MessageRelais.Session).session
        val affichee = session.evenements.single().pieces.single()
        assertTrue(affichee.estImage)
        assertEquals("dmln", affichee.vignette)
    }
}
