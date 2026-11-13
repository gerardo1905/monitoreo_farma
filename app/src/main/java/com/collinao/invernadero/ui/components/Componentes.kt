package com.collinao.invernadero.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.ui.theme.Correcto
import com.collinao.invernadero.ui.theme.FondoAbajo
import com.collinao.invernadero.ui.theme.FondoArriba
import com.collinao.invernadero.ui.theme.FondoMedio
import com.collinao.invernadero.ui.theme.Linea
import com.collinao.invernadero.ui.theme.Tinta
import com.collinao.invernadero.ui.theme.TintaSuave
import com.collinao.invernadero.ui.theme.Vidrio
import com.collinao.invernadero.ui.theme.VidrioBorde

/** Fondo degradado de toda la app. Respeta las barras del sistema y el teclado. */
@Composable
fun FondoApp(modifier: Modifier = Modifier, contenido: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoArriba, FondoMedio, FondoAbajo)))
            .systemBarsPadding()
            .imePadding(),
        content = contenido
    )
}

/**
 * Tarjeta de "vidrio": blanco translúcido, esquinas muy redondeadas y borde fino.
 * Si es pulsable, se encoge suavemente al tocarla (respuesta táctil al estilo iOS).
 */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    color: Color = Vidrio,
    relleno: PaddingValues = PaddingValues(16.dp),
    radio: Dp = 24.dp,
    alHacerClic: (() -> Unit)? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(radio)
    val fuente = remember { MutableInteractionSource() }
    val presionada by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionada && alHacerClic != null) 0.97f else 1f,
        animationSpec = tween(140),
        label = "escalaTarjeta"
    )
    val colorAnimado by animateColorAsState(color, tween(350), label = "colorTarjeta")
    Surface(
        modifier = modifier.graphicsLayer { scaleX = escala; scaleY = escala },
        shape = forma,
        color = colorAnimado,
        border = BorderStroke(1.dp, VidrioBorde)
    ) {
        Column(
            modifier = Modifier
                .clip(forma)
                .then(
                    if (alHacerClic != null) Modifier.clickable(
                        interactionSource = fuente,
                        indication = null,
                        onClick = alHacerClic
                    ) else Modifier
                )
                .padding(relleno),
            content = contenido
        )
    }
}

/**
 * Hace que su contenido aparezca suavemente (fundido + pequeño desplazamiento hacia arriba).
 * Con [indice] creciente, los elementos entran uno tras otro.
 */
@Composable
fun Aparece(modifier: Modifier = Modifier, indice: Int = 0, contenido: @Composable () -> Unit) {
    val estado = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = estado,
        modifier = modifier,
        enter = fadeIn(tween(450, delayMillis = indice * 70)) +
                slideInVertically(tween(450, delayMillis = indice * 70)) { it / 10 }
    ) { contenido() }
}

/** Botón principal: píldora oscura de ancho completo (como el "Continuar" de iOS). */
@Composable
fun BotonPrincipal(
    texto: String,
    alHacerClic: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    cargando: Boolean = false
) {
    Button(
        onClick = alHacerClic,
        enabled = habilitado && !cargando,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tinta,
            contentColor = Color.White,
            disabledContainerColor = Tinta.copy(alpha = 0.3f),
            disabledContentColor = Color.White
        )
    ) {
        if (cargando) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(texto, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Botón secundario: píldora clara con texto oscuro. */
@Composable
fun BotonSecundario(
    texto: String,
    alHacerClic: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    colorTexto: Color = Tinta
) {
    Button(
        onClick = alHacerClic,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.75f),
            contentColor = colorTexto,
            disabledContainerColor = Color.White.copy(alpha = 0.4f),
            disabledContentColor = TintaSuave
        ),
        border = BorderStroke(1.dp, Linea)
    ) {
        Text(texto, style = MaterialTheme.typography.titleSmall)
    }
}

/** Enlace de texto discreto. */
@Composable
fun BotonTexto(texto: String, alHacerClic: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = alHacerClic, modifier = modifier) {
        Text(texto, color = Tinta, style = MaterialTheme.typography.labelLarge)
    }
}

/** Campo de texto redondeado. Si es contraseña, incluye el botón Ver/Ocultar. */
@Composable
fun CampoTexto(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    esContrasena: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = if (esContrasena && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (esContrasena) KeyboardType.Password else teclado),
        trailingIcon = if (esContrasena) {
            {
                TextButton(onClick = { visible = !visible }) {
                    Text(
                        text = stringResource(if (visible) R.string.ocultar else R.string.ver),
                        color = TintaSuave,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White.copy(alpha = 0.75f),
            focusedBorderColor = Tinta,
            unfocusedBorderColor = Linea,
            focusedLabelColor = Tinta,
            cursorColor = Tinta
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Encabezado de pantalla: botón de volver redondo (opcional) y título grande. */
@Composable
fun EncabezadoPantalla(titulo: String, alVolver: (() -> Unit)? = null, subtitulo: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (alVolver != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
                    .clickable(onClick = alVolver),
                contentAlignment = Alignment.Center
            ) {
                IconoApp(Icono.ATRAS, Tinta, tamano = 22.dp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(titulo, style = MaterialTheme.typography.headlineLarge, color = Tinta)
        if (subtitulo != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = TintaSuave)
        }
    }
}

/** Etiqueta pequeña con punto de color: estados como "En rango", "Crítico", "Verificado". */
@Composable
fun EtiquetaEstado(texto: String, colorPunto: Color, fondo: Color = Color.White.copy(alpha = 0.7f)) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colorPunto)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium, color = Tinta)
    }
}

/** Opción de un selector en píldoras (rangos de tiempo, categorías). El cambio de color es suave. */
@Composable
fun Pildora(texto: String, seleccionada: Boolean, alHacerClic: () -> Unit) {
    val fondo by animateColorAsState(
        if (seleccionada) Tinta else Color.White.copy(alpha = 0.75f), tween(250), label = "fondoPildora"
    )
    val colorTexto by animateColorAsState(if (seleccionada) Color.White else Tinta, tween(250), label = "textoPildora")
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fondo)
            .clickable(onClick = alHacerClic)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.labelLarge,
            color = colorTexto,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Interruptor con el verde de "activo". */
@Composable
fun InterruptorApp(marcado: Boolean, alCambiar: (Boolean) -> Unit, habilitado: Boolean = true) {
    Switch(
        checked = marcado,
        onCheckedChange = alCambiar,
        enabled = habilitado,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Correcto,
            checkedThumbColor = Color.White,
            checkedBorderColor = Correcto,
            uncheckedTrackColor = Linea,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Linea
        )
    )
}

/** Logotipo de la app: cuadrado oscuro con una cruz blanca (sin imágenes, dibujado con Canvas). */
@Composable
fun Marca(tamano: Dp = 64.dp) {
    Canvas(modifier = Modifier.size(tamano)) {
        drawRoundRect(color = Tinta, cornerRadius = CornerRadius(size.width * 0.3f))
        val grosor = size.width * 0.14f
        val largo = size.width * 0.5f
        val centro = size.width / 2f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(centro - largo / 2, centro - grosor / 2),
            size = Size(largo, grosor),
            cornerRadius = CornerRadius(grosor / 2)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(centro - grosor / 2, centro - largo / 2),
            size = Size(grosor, largo),
            cornerRadius = CornerRadius(grosor / 2)
        )
    }
}

/** Nombre visible de cada actuador (viene de strings.xml). */
@Composable
fun nombreActuador(id: String): String = stringResource(
    when (id) {
        ActuadorId.BOMBA -> R.string.actuador_bomba
        ActuadorId.VENTILADOR -> R.string.actuador_ventilador
        ActuadorId.BOCINA -> R.string.actuador_bocina
        ActuadorId.LED -> R.string.actuador_led
        else -> R.string.actuador_desconocido
    }
)


/** Iconos de la app, dibujados con Canvas (no dependen de ninguna librería de iconos). */
enum class Icono { INICIO, GRAFICAS, AJUSTES, REGISTROS, PERFIL, ATRAS }

@Composable
fun IconoApp(icono: Icono, color: Color, modifier: Modifier = Modifier, tamano: Dp = 24.dp) {
    Canvas(modifier = modifier.size(tamano)) {
        val w = size.width
        val h = size.height
        val grosor = w * 0.085f
        val trazo = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(w * x, h * y)

        when (icono) {
            Icono.INICIO -> {
                val casa = Path().apply {
                    moveTo(w * 0.14f, h * 0.5f); lineTo(w * 0.5f, h * 0.17f); lineTo(w * 0.86f, h * 0.5f)
                    moveTo(w * 0.25f, h * 0.42f); lineTo(w * 0.25f, h * 0.83f)
                    lineTo(w * 0.75f, h * 0.83f); lineTo(w * 0.75f, h * 0.42f)
                    moveTo(w * 0.42f, h * 0.83f); lineTo(w * 0.42f, h * 0.62f)
                    lineTo(w * 0.58f, h * 0.62f); lineTo(w * 0.58f, h * 0.83f)
                }
                drawPath(casa, color, style = trazo)
            }

            Icono.GRAFICAS -> {
                val ejes = Path().apply {
                    moveTo(w * 0.15f, h * 0.15f); lineTo(w * 0.15f, h * 0.85f); lineTo(w * 0.88f, h * 0.85f)
                }
                drawPath(ejes, color, style = trazo)
                val linea = Path().apply {
                    moveTo(w * 0.28f, h * 0.68f); lineTo(w * 0.46f, h * 0.48f)
                    lineTo(w * 0.62f, h * 0.6f); lineTo(w * 0.86f, h * 0.28f)
                }
                drawPath(linea, color, style = trazo)
            }

            Icono.AJUSTES -> {
                // Engranaje: anillo exterior, centro y 8 dientes
                val centro = Offset(w / 2, h / 2)
                drawCircle(color, radius = w * 0.27f, center = centro, style = trazo)
                drawCircle(color, radius = w * 0.1f, center = centro, style = trazo)
                for (i in 0 until 8) {
                    val ang = i * PI / 4
                    val dx = cos(ang).toFloat()
                    val dy = sin(ang).toFloat()
                    drawLine(
                        color = color,
                        start = Offset(centro.x + dx * w * 0.3f, centro.y + dy * w * 0.3f),
                        end = Offset(centro.x + dx * w * 0.42f, centro.y + dy * w * 0.42f),
                        strokeWidth = w * 0.13f,
                        cap = StrokeCap.Round
                    )
                }
            }

            Icono.REGISTROS -> {
                listOf(0.28f, 0.5f, 0.72f).forEach { y ->
                    drawCircle(color, radius = w * 0.05f, center = p(0.2f, y))
                    drawLine(color, p(0.36f, y), p(0.84f, y), strokeWidth = grosor, cap = StrokeCap.Round)
                }
            }

            Icono.PERFIL -> {
                drawCircle(color, radius = w * 0.16f, center = p(0.5f, 0.32f), style = trazo)
                val hombros = Path().apply {
                    moveTo(w * 0.2f, h * 0.85f)
                    cubicTo(w * 0.2f, h * 0.52f, w * 0.8f, h * 0.52f, w * 0.8f, h * 0.85f)
                }
                drawPath(hombros, color, style = trazo)
            }

            Icono.ATRAS -> {
                val flecha = Path().apply {
                    moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.36f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f)
                }
                drawPath(flecha, color, style = Stroke(width = grosor * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Una pestaña de la barra inferior: solo icono; la activa se ve como una píldora oscura. */
@Composable
private fun ItemBarra(icono: Icono, descripcion: String, seleccionado: Boolean, alHacerClic: () -> Unit) {
    val fondo by animateColorAsState(if (seleccionado) Tinta else Color.Transparent, tween(300), label = "fondoItem")
    val colorIcono by animateColorAsState(if (seleccionado) Color.White else TintaSuave, tween(300), label = "iconoItem")
    val escala by animateFloatAsState(if (seleccionado) 1f else 0.92f, tween(300), label = "escalaItem")
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .size(width = 56.dp, height = 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(fondo)
            .semantics { contentDescription = descripcion }
            .clickable(onClick = alHacerClic),
        contentAlignment = Alignment.Center
    ) {
        IconoApp(icono, colorIcono, tamano = 24.dp)
    }
}

/** Datos de una pestaña principal. */
data class Pestana(val ruta: String, val icono: Icono, val descripcion: String)

/** Barra de navegación inferior flotante, igual en todas las pantallas principales. */
@Composable
fun BarraNavegacion(
    pestanas: List<Pestana>,
    rutaActual: String?,
    alSeleccionar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Tarjeta(modifier = modifier, radio = 32.dp, relleno = PaddingValues(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pestanas.forEach { p ->
                ItemBarra(
                    icono = p.icono,
                    descripcion = p.descripcion,
                    seleccionado = p.ruta == rutaActual,
                    alHacerClic = { alSeleccionar(p.ruta) }
                )
            }
        }
    }
}
