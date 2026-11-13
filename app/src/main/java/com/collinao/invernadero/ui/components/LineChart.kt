package com.collinao.invernadero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.collinao.invernadero.R

/** Un punto del gráfico: instante (ms) y valor. */
data class PuntoGrafico(val tiempo: Long, val valor: Float)

/**
 * Gráfico de línea simple dibujado con Canvas (sin librerías externas).
 * El eje horizontal es proporcional al tiempo real, así los huecos sin datos se ven como huecos.
 * Las líneas punteadas son los límites (por ejemplo, los umbrales de temperatura).
 */
@Composable
fun LineChart(
    puntos: List<PuntoGrafico>,
    color: Color,
    unidad: String,
    modifier: Modifier = Modifier,
    limiteSuperior: Float? = null,
    limiteInferior: Float? = null,
    colorLimite: Color = Color(0xFFE74C3C),
    claveAnimacion: Any = Unit
) {
    if (puntos.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.grafica_sin_datos),
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    // Al cambiar de rango, la línea se vuelve a dibujar de izquierda a derecha
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(claveAnimacion) {
        progreso.snapTo(0f)
        progreso.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    // El rango vertical incluye los límites para que siempre se vean
    val valores = puntos.map { it.valor } + listOfNotNull(limiteSuperior, limiteInferior)
    val minDatos = valores.min()
    val maxDatos = valores.max()
    val margen = ((maxDatos - minDatos) * 0.08f).coerceAtLeast(0.5f)
    val minY = minDatos - margen
    val maxY = maxDatos + margen

    val t0 = puntos.first().tiempo
    val t1 = puntos.last().tiempo
    val duracion = (t1 - t0).coerceAtLeast(1L).toFloat()

    Row(modifier = modifier.fillMaxWidth().height(160.dp)) {
        Column(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("%.1f %s".format(maxY, unidad), style = MaterialTheme.typography.labelSmall)
            Text("%.1f %s".format(minY, unidad), style = MaterialTheme.typography.labelSmall)
        }

        Canvas(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp)) {
            val ancho = size.width
            val alto = size.height

            fun x(t: Long) = ancho * (t - t0) / duracion
            fun y(v: Float) = alto - (v - minY) / (maxY - minY) * alto

            val punteado = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            limiteSuperior?.let {
                drawLine(colorLimite.copy(alpha = 0.7f), Offset(0f, y(it)), Offset(ancho, y(it)), strokeWidth = 2f, pathEffect = punteado)
            }
            limiteInferior?.let {
                drawLine(colorLimite.copy(alpha = 0.7f), Offset(0f, y(it)), Offset(ancho, y(it)), strokeWidth = 2f, pathEffect = punteado)
            }

            val camino = Path().apply {
                moveTo(x(puntos[0].tiempo), y(puntos[0].valor))
                for (i in 1 until puntos.size) lineTo(x(puntos[i].tiempo), y(puntos[i].valor))
            }
            val medida = PathMeasure().apply { setPath(camino, false) }
            val parcial = Path()
            medida.getSegment(0f, medida.length * progreso.value, parcial, true)
            drawPath(
                path = parcial,
                color = color,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
