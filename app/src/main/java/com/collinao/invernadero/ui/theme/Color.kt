package com.collinao.invernadero.ui.theme

import androidx.compose.ui.graphics.Color

// --- Paleta clínica y suave: fondos casi blancos con un toque lavanda/menta, texto en gris casi negro ---

/** Color principal: texto y botones (gris casi negro, como en iOS). */
val Tinta = Color(0xFF1C1C1E)
val TintaSuave = Color(0xFF6B6B76)
val Linea = Color(0xFFE3E5EC)

/** Degradado de fondo de toda la app (lavanda muy claro → blanco azulado → menta muy clara). */
val FondoArriba = Color(0xFFE9EBF8)
val FondoMedio = Color(0xFFF4F6FB)
val FondoAbajo = Color(0xFFE5F2EE)

/** Superficie "vidrio": blanco semitransparente sobre el degradado. */
val Vidrio = Color(0xB3FFFFFF)
val VidrioBorde = Color(0xCCFFFFFF)

/** Acento clínico (verde azulado) para detalles. */
val Acento = Color(0xFF0F8B8D)

// --- Estados: se usan siempre junto con un texto, no solo el color ---
val Correcto = Color(0xFF2FAE60)
val CorrectoSuave = Color(0xFFDDF4E6)
val Advertencia = Color(0xFFE59A12)
val AdvertenciaSuave = Color(0xFFFFF0D2)
val Critico = Color(0xFFE5484D)
val CriticoSuave = Color(0xFFFCE1E2)
val CriticoOscuro = Color(0xFF7A1D21)
val AdvertenciaOscuro = Color(0xFF5C3B00)

/** Alarma a pantalla completa. */
val AlarmaFondoArriba = Color(0xFFE5484D)
val AlarmaFondoAbajo = Color(0xFF8E1B20)
