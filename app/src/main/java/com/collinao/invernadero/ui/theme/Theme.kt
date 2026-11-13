package com.collinao.invernadero.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tema único y claro: una app de monitoreo en un entorno clínico debe verse igual y legible
 * siempre, así que no se usan colores dinámicos del sistema ni modo oscuro.
 */
private val EsquemaClaro = lightColorScheme(
    primary = Tinta,
    onPrimary = Color.White,
    secondary = Acento,
    onSecondary = Color.White,
    background = FondoMedio,
    onBackground = Tinta,
    surface = Color.White,
    onSurface = Tinta,
    surfaceVariant = Color(0xFFEFF1F6),
    onSurfaceVariant = TintaSuave,
    outline = Linea,
    error = Critico,
    onError = Color.White,
    errorContainer = CriticoSuave,
    onErrorContainer = CriticoOscuro
)

@Composable
fun InvernaderoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaClaro,
        typography = Typography,
        content = content
    )
}
