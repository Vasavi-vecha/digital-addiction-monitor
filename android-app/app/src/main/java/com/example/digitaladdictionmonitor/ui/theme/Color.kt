package com.example.digitaladdictionmonitor.ui.theme

import androidx.compose.ui.graphics.Color

// Fixed semantic colors for risk severity -- deliberately not theme-derived,
// so "this is good" / "this needs attention" reads the same regardless of
// the device's Material You palette.
val GoodColor = Color(0xFF2E7D32)
val GoodContainer = Color(0xFFE8F5E9)
val CautionColor = Color(0xFFB26A00)
val CautionContainer = Color(0xFFFFF3DC)
val ConcernColor = Color(0xFFC62828)
val ConcernContainer = Color(0xFFFFEBEE)

// Accent colors for ranked suggestion cards, indexed by (rank - 1).
// Deliberately distinct from the severity colors above -- rank is about
// expectedImpact ordering, not good/bad, so reusing Good/Caution/Concern
// would misleadingly imply severity.
val SuggestionAccentColors = listOf(
    Color(0xFF6A4C93),
    Color(0xFF1E88E5),
    Color(0xFF00897B),
)

// Brand gradient for the logo and home header -- reuses the first/last
// SuggestionAccentColors so the mark and the in-app accents read as one
// consistent palette instead of introducing a fourth unrelated color pair.
val BrandGradientStart = Color(0xFF6A4C93)
val BrandGradientEnd = Color(0xFF00897B)