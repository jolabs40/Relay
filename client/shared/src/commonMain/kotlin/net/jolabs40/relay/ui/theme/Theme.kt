package net.jolabs40.relay.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Un ocre chaud pour ce qui attend l'utilisateur, un vert pour ce qui est fait. Les surfaces sont
 * posées à la main : un bureau n'a pas de Material You, et les rôles laissés par défaut tireraient
 * vers le mauve de Material.
 */

private val schemaClair = lightColorScheme(
    primary = Color(0xFF9A4A25),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF370E00),
    secondary = Color(0xFF3F6B5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC1ECD9),
    onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFF5C5F8F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E0FF),
    onTertiaryContainer = Color(0xFF181B48),
    background = Color(0xFFFBF8F5),
    onBackground = Color(0xFF1F1B18),
    surface = Color(0xFFFBF8F5),
    onSurface = Color(0xFF1F1B18),
    surfaceVariant = Color(0xFFEDE3DC),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD7C2B8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1ED),
    surfaceContainer = Color(0xFFF1EBE6),
    surfaceContainerHigh = Color(0xFFEBE5E0),
    surfaceContainerHighest = Color(0xFFE5DFDA),
)

private val schemaSombre = darkColorScheme(
    primary = Color(0xFFFFB595),
    onPrimary = Color(0xFF581E02),
    primaryContainer = Color(0xFF7A3311),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFFA6D0BE),
    onSecondary = Color(0xFF0E372B),
    secondaryContainer = Color(0xFF274E41),
    onSecondaryContainer = Color(0xFFC1ECD9),
    tertiary = Color(0xFFC3C3FC),
    onTertiary = Color(0xFF2D315E),
    tertiaryContainer = Color(0xFF444776),
    onTertiaryContainer = Color(0xFFE0E0FF),
    background = Color(0xFF161312),
    onBackground = Color(0xFFEBE0DB),
    surface = Color(0xFF161312),
    onSurface = Color(0xFFEBE0DB),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
    surfaceContainerLowest = Color(0xFF100D0C),
    surfaceContainerLow = Color(0xFF1F1B19),
    surfaceContainer = Color(0xFF231F1D),
    surfaceContainerHigh = Color(0xFF2E2927),
    surfaceContainerHighest = Color(0xFF393432),
)

/** Pastilles d'état des sessions, lisibles d'un coup d'œil dans la liste. */
object CouleursEtat {
    val attente = Color(0xFFE08A3C)
    val travaille = Color(0xFF4A8FD9)
    val inactive = Color(0xFF3E9E72)
    val erreur = Color(0xFFD1443F)
}

@Composable
fun ThemeRelay(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (sombre) schemaSombre else schemaClair, content = contenu)
}
