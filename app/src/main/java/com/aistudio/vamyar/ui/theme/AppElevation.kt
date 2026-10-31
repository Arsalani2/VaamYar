package com.aistudio.vamyar.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Centralized elevation constants (fix bug.md/NB-55).
 *
 * Page-level cards render FLAT: separation from the background comes from each
 * card's existing BorderStroke, so Compose's default black drop shadow never
 * reads as a dark halo («سیاهی») around boxes on light backgrounds.
 * Dialogs (8dp over a dark scrim) and the FAB keep their standard elevation
 * and intentionally do not use these constants.
 */
object AppElevation {
    /** Flat elevation for page-level cards (dashboard, loans, receivables, settings). */
    val Card = 0.dp
}
