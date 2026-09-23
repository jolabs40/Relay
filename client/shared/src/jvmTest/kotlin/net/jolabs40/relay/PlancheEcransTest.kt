package net.jolabs40.relay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.jolabs40.relay.donnees.ClientRelais
import net.jolabs40.relay.ui.RelaisViewModel
import net.jolabs40.relay.ui.Volet
import net.jolabs40.relay.ui.ecrans.EcranRelais
import net.jolabs40.relay.ui.theme.ThemeRelay
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import net.jolabs40.relay.ui.pieces.lireFichiers

/**
 * Planche des écrans, rendue hors fenêtre à partir de faux messages du relais :
 * `./gradlew :shared:jvmTest -Pplanche=1`, images dans `shared/build/captures/`.
 */
class PlancheEcransTest {

    private val proprietaire = object : LifecycleOwner {
        val registre = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registre
    }

    private fun <T> surFilAwt(bloc: () -> T): T {
        var resultat: Result<T>? = null
        SwingUtilities.invokeAndWait { resultat = runCatching(bloc) }
        return resultat!!.getOrThrow()
    }

    private val bonjour = """
        {"type":"bonjour","version":1,"racine":"C:/Users/moi/Projets",
         "projets":[{"nom":"Projets","chemin":"C:/S"},{"nom":"BookVoice","chemin":"C:/S/BookVoice"},
                    {"nom":"MeepleTV","chemin":"C:/S/MeepleTV"},{"nom":"Relay","chemin":"C:/S/Relay"},
                    {"nom":"TVSlim Suite/TVSlim","chemin":"C:/S/TVSlim Suite/TVSlim"}],
         "modes":["default","acceptEdits","plan","bypassPermissions"],"sessions":[]}
    """.trimIndent()

    private fun session(id: String, projet: String, titre: String, etat: String, mode: String, activite: String, evenements: String, tour: String = "null") =
        """{"type":"session","session":{"id":"$id","projet":"$projet","cwd":"C:/S/$projet","titre":"$titre",
            "etat":"$etat","mode":"$mode","activite":"$activite","claude_session_id":"x","en_file":0,
            "cree_a":1,"maj_a":${id.hashCode().toLong() and 0xffff},"tour":$tour,"evenements":[$evenements]}}"""

    /** Il y a [minutes] minutes : les heures du fil tombent aujourd'hui, donc sans date. */
    private val maintenant = System.currentTimeMillis()
    private fun ilYa(minutes: Double) = maintenant - (minutes * 60_000).toLong()

    private val question = session(
        "q", "MeepleTV", "Ajouter un mode équipe au Mot Juste", "attente", "bypassPermissions", "",
        """
        {"id":1,"type":"prompt","texte":"Ajoute un mode équipe au Mot Juste : deux équipes, score commun."},
        {"id":2,"type":"question","demande":"d1","en_attente":true,"questions":[
          {"question":"Comment répartir les joueurs entre les équipes ?","header":"Équipes","multiSelect":false,"options":[
            {"label":"Tirage au sort","description":"La TV répartit les téléphones au hasard à chaque partie."},
            {"label":"Choix des joueurs","description":"Chacun choisit son équipe sur son téléphone avant la partie."},
            {"label":"Par l'hôte","description":"L'hôte compose les équipes depuis la TV."}]},
          {"question":"Quels écrans afficher le score d'équipe ?","header":"Score","multiSelect":true,"options":[
            {"label":"TV","description":"Bandeau permanent en haut du plateau."},
            {"label":"Téléphones","description":"Rappel discret sous la main du joueur."}]}]}
        """,
    )

    private val permission = session(
        "p", "BookVoice", "Nettoyer les builds", "attente", "default", "",
        """
        {"id":1,"type":"prompt","texte":"Nettoie les dossiers de build et relance les tests."},
        {"id":2,"type":"permission","horodatage":${ilYa(3.0)},"demande":"d2","en_attente":false,"outil":"Bash","resume":"Bash ./gradlew clean","detail":"./gradlew clean","reponse":{"autoriser":true}},
        {"id":3,"type":"permission","demande":"d3","en_attente":true,"outil":"Bash","resume":"Bash Supprime le cache Gradle","detail":"rm -rf ~/.gradle/caches/transforms-4\n./gradlew test --no-daemon"}
        """,
    )

    private val plan = session(
        "l", "Relay", "Notifications Android", "attente", "plan", "",
        """
        {"id":1,"type":"prompt","texte":"Prépare l'app Android : mêmes écrans, Wi-Fi maison."},
        {"id":2,"type":"plan","demande":"d4","en_attente":true,"plan":"# App Android de Relay\n\n## Étapes\n1. Ajouter la cible `androidTarget()` au module **shared**.\n2. Créer le module `:android` (Activity unique, `EcranRelais`).\n3. Appairage : saisie de l'adresse du PC et du jeton, stockés en `EncryptedSharedPreferences`.\n4. Relais : option `reseau_local` pour écouter sur `0.0.0.0`.\n\n## Vérification\n- `./gradlew :android:assembleDebug`\n- Essai sur l'émulateur, relais joint par `10.0.2.2`."}
        """,
    )

    private val resultat = session(
        "r", "TVSlim Suite/TVSlim", "Corriger le crash au démarrage", "inactive", "acceptEdits", "",
        """
        {"id":1,"type":"prompt","horodatage":${ilYa(9.0)},"texte":"L'app plante au démarrage sur l'émulateur, trouve et corrige."},
        {"id":2,"type":"question","horodatage":${ilYa(8.0)},"demande":"d5","en_attente":false,"questions":[{"question":"Corriger aussi la version TV ?","header":"Portée","multiSelect":false,"options":[{"label":"Oui"},{"label":"Non"}]}],"reponse":{"reponses":{"Corriger aussi la version TV ?":"Oui"}}},
        {"id":3,"type":"resultat","texte":"**Corrigé.** Le crash venait de `removeFirst()` appelé sur API 34 dans `JournalRepository.kt:118`.\n\n- Remplacé par `removeAt(0)` dans les deux applications.\n- Build `assembleDebug` : OK. Tests `:core:test` : 212 passés.\n\nRien n'est commité.","erreur":false,"duree_ms":187000,"cout_usd":0.41,"tours":14,
         "horodatage":${ilYa(5.9)},"debut":${ilYa(9.0)},
         "bilan":{"fichiers_modifies":4,"fichiers_crees":1,"lignes_ajoutees":86,"lignes_retirees":23,"tests_ecrits":3,"tests_lances":2,"tests_echoues":1,"actions":27}},
        {"id":4,"type":"prompt","horodatage":${ilYa(2.0)},"texte":"Commit, en français."},
        {"id":5,"type":"info","horodatage":${ilYa(1.5)},"texte":"Interrompu"}
        """,
    )

    private val travail = session(
        "t", "Projets", "Audit des CLAUDE.md", "travaille", "bypassPermissions", "Read BookVoice/CLAUDE.md",
        """{"id":1,"type":"prompt","horodatage":${ilYa(4.3)},"texte":"Audite tous les CLAUDE.md des sous-projets."}""",
        tour = """{"debut":${ilYa(4.3)},"bilan":{"fichiers_modifies":7,"fichiers_crees":2,"lignes_ajoutees":214,"lignes_retirees":58,"tests_ecrits":0,"tests_lances":1,"tests_echoues":0,"actions":41}}""",
    )

    @Test
    fun planche() {
        assumeTrue(System.getProperty("relay.planche") != null)
        val sortie = File(System.getProperty("relay.captures")).apply { mkdirs() }
        val client = ClientRelais(CoroutineScope(SupervisorJob() + Dispatchers.Main)) { null }
        val vm = surFilAwt {
            listOf(bonjour, travail, resultat, plan, permission, question).forEach(client::recevoir)
            RelaisViewModel(client)
        }
        capturer(sortie, "1-nouvelle", vm, Volet.Nouvelle, sombre = true)
        surFilAwt { vm.choisirProjet(client.projets.value.first { it.nom == "MeepleTV" }) }
        capturer(sortie, "1b-nouvelle-projet", vm, Volet.Nouvelle, sombre = true)
        capturer(sortie, "2-question", vm, Volet.Session("q"), sombre = true)
        capturer(sortie, "3-permission", vm, Volet.Session("p"), sombre = false)
        capturer(sortie, "4-plan", vm, Volet.Session("l"), sombre = true)
        capturer(sortie, "5-resultat", vm, Volet.Session("r"), sombre = true)
        capturer(sortie, "6-travail", vm, Volet.Session("t"), sombre = false)

        // Pièces jointes : une capture déjà envoyée dans le fil, et deux pièces prêtes à partir.
        val capture = File(sortie, "capture-exemple.png").also { ImageIO.write(fausseCapture(), "png", it) }
        val journal = File(sortie, "gradle-build.log").apply { writeText("BUILD FAILED") }
        val lues = lireFichiers(listOf(capture, journal))
        val vignette = lues.pieces.first().vignette
        surFilAwt {
            client.recevoir(
                session(
                    "i", "MeepleTV", "Le plateau déborde", "travaille", "bypassPermissions", "Read PlateauScreen.kt",
                    """{"id":1,"type":"prompt","texte":"Le plateau déborde à droite sur la TCL, regarde la capture.",
                        "pieces":[{"nom":"capture.png","type_mime":"image/png","taille":84211,"vignette":"$vignette"},
                                  {"nom":"logcat.txt","type_mime":"text/plain","taille":5120}]}""",
                ),
            )
            vm.ajouterPieces("i", lues)
        }
        capturer(sortie, "7-pieces", vm, Volet.Session("i"), sombre = true)
    }

    /** Une fausse capture d'écran : un dégradé et quelques cartes, pour que la vignette ait l'air vraie. */
    private fun fausseCapture(): BufferedImage {
        val image = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(24, 40, 72), 1920f, 1080f, Color(90, 40, 110))
        g.fillRect(0, 0, 1920, 1080)
        g.color = Color(255, 255, 255, 200)
        repeat(4) { i -> g.fillRoundRect(120 + i * 460, 300, 400, 480, 40, 40) }
        g.dispose()
        return image
    }

    private fun capturer(sortie: File, nom: String, vm: RelaisViewModel, volet: Volet, sombre: Boolean) {
        surFilAwt { vm.ouvrir(volet) }
        val scene = surFilAwt {
            ImageComposeScene(width = 1320, height = 880, density = Density(1f)) {
                CompositionLocalProvider(LocalLifecycleOwner provides proprietaire) {
                    ThemeRelay(sombre = sombre) { EcranRelais(vm) }
                }
            }
        }
        try {
            repeat(6) { i ->
                Thread.sleep(200)
                surFilAwt { scene.render((i + 1) * 500_000_000L) }
            }
            val image = surFilAwt { scene.render(5_000_000_000L) }
            File(sortie, "$nom.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            surFilAwt { scene.close() }
        }
    }
}
