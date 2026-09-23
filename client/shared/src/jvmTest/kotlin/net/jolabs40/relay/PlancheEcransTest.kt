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
import javax.swing.SwingUtilities

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

    private fun session(id: String, projet: String, titre: String, etat: String, mode: String, activite: String, evenements: String) =
        """{"type":"session","session":{"id":"$id","projet":"$projet","cwd":"C:/S/$projet","titre":"$titre",
            "etat":"$etat","mode":"$mode","activite":"$activite","claude_session_id":"x","en_file":0,
            "cree_a":1,"maj_a":${id.hashCode().toLong() and 0xffff},"evenements":[$evenements]}}"""

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
        {"id":2,"type":"permission","demande":"d2","en_attente":false,"outil":"Bash","resume":"Bash ./gradlew clean","detail":"./gradlew clean","reponse":{"autoriser":true}},
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
        {"id":1,"type":"prompt","texte":"L'app plante au démarrage sur l'émulateur, trouve et corrige."},
        {"id":2,"type":"question","demande":"d5","en_attente":false,"questions":[{"question":"Corriger aussi la version TV ?","header":"Portée","multiSelect":false,"options":[{"label":"Oui"},{"label":"Non"}]}],"reponse":{"reponses":{"Corriger aussi la version TV ?":"Oui"}}},
        {"id":3,"type":"resultat","texte":"**Corrigé.** Le crash venait de `removeFirst()` appelé sur API 34 dans `JournalRepository.kt:118`.\n\n- Remplacé par `removeAt(0)` dans les deux applications.\n- Build `assembleDebug` : OK. Tests `:core:test` : 212 passés.\n\nRien n'est commité.","erreur":false,"duree_ms":187000,"cout_usd":0.41,"tours":14},
        {"id":4,"type":"prompt","texte":"Commit, en français."},
        {"id":5,"type":"info","texte":"Interrompu"}
        """,
    )

    private val travail = session(
        "t", "Projets", "Audit des CLAUDE.md", "travaille", "bypassPermissions", "Read BookVoice/CLAUDE.md",
        """{"id":1,"type":"prompt","texte":"Audite tous les CLAUDE.md des sous-projets."}""",
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
