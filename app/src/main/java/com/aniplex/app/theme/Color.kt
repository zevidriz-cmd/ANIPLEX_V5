package com.aniplex.app.theme

import androidx.compose.ui.graphics.Color

// Brand Accents - Premium glowing lavender/violet and refined deep violet
val CrunchyrollOrange = Color(0xFFE50914) // Web Netflix Red accent color
val NetflixRed = Color(0xFFB20710)        // Deep dark red secondary accent

// Dark Theme Foundations - Modern obsidian space slate/indigo aesthetic
val BackgroundVoid = Color(0xFF0A0A0A)       // Pitch black background from website
val SurfaceDark = Color(0xFF141414)          // Sleek container base panel
val SurfaceDarkVariant = Color(0xFF1F1F1F)   // Polished dark grey container
val SurfaceGlass = Color(0x351F1F1F)         // Radiant semi-translucent glass overlay

// Accent Gradients - Exquisite warm cinematic neon red glow
val BrandGradient = listOf(CrunchyrollOrange, NetflixRed)

// Text Elements - High-end typography contrast
val TextPrimary = Color(0xFFF7F8FC)          // Premium silver-white primary text
val TextSecondary = Color(0xFF9E99B3)        // Sophisticated cool lavender secondary text
val TextMuted = Color(0xFF5D597A)            // Polished muted slate for subtle indicators

// Utility Colors
val GoldStar = Color(0xFFFFC107)             // Exquisite cinematic gold rating stars
val ErrorColor = Color(0xFFFF3355)           // Vibrant clear error red-pink
val SuccessColor = Color(0xFF00E676)         // Radiant modern emerald green

fun getAccentColor(name: String): Color {
    return when (name) {
        "Cosmic Red" -> Color(0xFFB20710)
        "Future Teal" -> Color(0xFF00E5FF)
        "Gold Master" -> Color(0xFFFFD700)
        "Pure Emerald" -> Color(0xFF00E676)
        "Purple Neon" -> Color(0xFFE50914)
        else -> Color(0xFFE50914)
    }
}


