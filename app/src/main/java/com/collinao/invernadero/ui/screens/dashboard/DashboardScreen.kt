package com.collinao.invernadero.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.data.local.AccionLocal
import com.collinao.invernadero.data.local.BitacoraLocal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.collinao.invernadero.data.model.ActuadorId
import com.collinao.invernadero.data.model.EstadoActuador
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.*

private enum class Nivel { NORMAL, ADVERTENCIA, CRITICO }

private fun nivelTemperatura(v: Double, t: Thresholds): Nivel = when {
    v > t.alertaTemperaturaMax || v < t.alertaTemperaturaMin -> Nivel.CRITICO
    v > t.temperaturaMax || v < t.temperaturaMin -> Nivel.ADVERTENCIA
    else -> Nivel.NORMAL
}

private fun nivelHumedad(v: Double, t: Thresholds): Nivel = when {
    v > t.alertaHumedadMax || v < t.alertaHumedadMin -> Nivel.CRITICO
    v > t.humedadAireMax || v < t.humedadAireMin -> Nivel.ADVERTENCIA
    else -> Nivel.NORMAL
}

@Composable
private fun textoHace(segundos: Long): String = when {
    segundos < 60 -> stringResource(R.string.hace_segundos, segundos)
    segundos < 3600 -> stringResource(R.string.hace_minutos, segundos / 60)
    else -> stringResource(R.string.hace_horas, segundos / 3600)
}

@Composable
fun DashboardScreen(
    navController: NavHostController,
    viewModel: DashboardViewModel = viewModel()
) {
    val sensorData by viewModel.sensorData.collectAsStateWithLifecycle()
    val enLinea by viewModel.enLinea.collectAsStateWithLifecycle()
    val segundosSenal by viewModel.segundosDesdeSenal.collectAsStateWithLifecycle()
    val segundosLectura by viewModel.segundosDesdeLectura.collectAsStateWithLifecycle()
    val actuadores by viewModel.actuadores.collectAsStateWithLifecycle()
    val umbrales by viewModel.umbrales.collectAsStateWithLifecycle()
    val alerta by viewModel.alertaMensaje.collectAsStateWithLifecycle()
    val conectadoServidor by viewModel.conectadoServidor.collectAsStateWithLifecycle()
    val pendientesDispositivo by viewModel.pendientesDispositivo.collectAsStateWithLifecycle()
    val accionesLocales by viewModel.accionesLocales.collectAsStateWithLifecycle()
    val accionesPendientes by viewModel.accionesPendientes.collectAsStateWithLifecycle()

    val hayLectura = sensorData.timestamp != 0L

    // Los números cambian con una animación suave en lugar de saltar
    val temperaturaAnimada by animateFloatAsState(sensorData.temperatura.toFloat(), tween(700), label = "temperatura")
    val humedadAnimada by animateFloatAsState(sensorData.humedadAire.toFloat(), tween(700), label = "humedad")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // --- Encabezado: título + estado de conexión ---
        Aparece(indice = 0) {
            Column {
                Text(stringResource(R.string.dashboard_titulo), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.dashboard_subtitulo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TintaSuave
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtiquetaEstado(
                        texto = stringResource(if (enLinea) R.string.dispositivo_en_linea else R.string.dispositivo_desconectado),
                        colorPunto = if (enLinea) Correcto else Critico
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = segundosSenal?.let { stringResource(R.string.ultima_senal, textoHace(it)) }
                            ?: stringResource(R.string.sin_senal),
                        style = MaterialTheme.typography.labelMedium,
                        color = TintaSuave
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3.1.1.3 / 3.1.2.7: aviso de conexión del teléfono con el servidor ---
        AnimatedVisibility(
            visible = !conectadoServidor,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column {
                Tarjeta(modifier = Modifier.fillMaxWidth(), color = AdvertenciaSuave) {
                    Text(
                        text = stringResource(R.string.sin_conexion_servidor_titulo),
                        style = MaterialTheme.typography.titleSmall,
                        color = Tinta
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sin_conexion_servidor_detalle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave
                    )
                    if (accionesPendientes > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.acciones_pendientes_locales, accionesPendientes),
                            style = MaterialTheme.typography.labelMedium,
                            color = Tinta
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // --- 3.1.1.4: el ESP32 está subiendo lecturas que guardó mientras no tenía conexión ---
        AnimatedVisibility(
            visible = conectadoServidor && pendientesDispositivo > 0,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column {
                Tarjeta(modifier = Modifier.fillMaxWidth(), color = Vidrio) {
                    Text(
                        text = stringResource(R.string.dispositivo_sincronizando, pendientesDispositivo),
                        style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // --- Alerta: entra y sale con animación ---
        AnimatedVisibility(
            visible = alerta != null,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column {
                Tarjeta(modifier = Modifier.fillMaxWidth(), color = CriticoSuave) {
                    Text(
                        text = alerta ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CriticoOscuro
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // --- Temperatura y humedad: lo más importante, en grande ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Aparece(modifier = Modifier.weight(1f), indice = 1) {
                TarjetaMedida(
                    modifier = Modifier.fillMaxWidth(),
                    titulo = stringResource(R.string.temperatura),
                    valor = if (hayLectura) "%.1f".format(temperaturaAnimada) else "--",
                    unidad = "°C",
                    rango = stringResource(R.string.rango_normal_temp, umbrales.temperaturaMin, umbrales.temperaturaMax),
                    nivel = if (hayLectura) nivelTemperatura(sensorData.temperatura, umbrales) else null
                )
            }
            Aparece(modifier = Modifier.weight(1f), indice = 2) {
                TarjetaMedida(
                    modifier = Modifier.fillMaxWidth(),
                    titulo = stringResource(R.string.humedad),
                    valor = if (hayLectura) "%.0f".format(humedadAnimada) else "--",
                    unidad = "%",
                    rango = stringResource(R.string.rango_normal_hum, umbrales.humedadAireMin, umbrales.humedadAireMax),
                    nivel = if (hayLectura) nivelHumedad(sensorData.humedadAire, umbrales) else null
                )
            }
        }

        Text(
            text = segundosLectura?.let { stringResource(R.string.ultima_lectura, textoHace(it)) }
                ?: stringResource(R.string.sin_lecturas),
            style = MaterialTheme.typography.labelSmall,
            color = TintaSuave,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )

        // --- Equipos: cuadrícula 2x2, al estilo de los accesos rápidos de iOS ---
        Spacer(modifier = Modifier.height(24.dp))
        Aparece(indice = 3) {
            Column {
                Text(stringResource(R.string.equipos), style = MaterialTheme.typography.titleLarge)
                if (!umbrales.automatizacionActiva) {
                    Text(
                        text = stringResource(R.string.automatizacion_desactivada),
                        style = MaterialTheme.typography.bodySmall,
                        color = TintaSuave
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        ActuadorId.todos.chunked(2).forEachIndexed { numFila, fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEachIndexed { numCol, id ->
                    Aparece(modifier = Modifier.weight(1f), indice = 4 + numFila * 2 + numCol) {
                        TileEquipo(
                            modifier = Modifier.fillMaxWidth(),
                            nombre = nombreActuador(id),
                            estado = actuadores[id] ?: EstadoActuador(),
                            automaticoDisponible = umbrales.automatizacionActiva,
                            alCambiarEstado = { viewModel.cambiarEstado(id, it) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- 3.1.2.7: base de datos local del teléfono (acciones y su estado de sincronización) ---
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.historial_local_titulo), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.historial_local_subtitulo),
            style = MaterialTheme.typography.bodySmall,
            color = TintaSuave
        )
        Spacer(modifier = Modifier.height(10.dp))
        Tarjeta(modifier = Modifier.fillMaxWidth()) {
            if (accionesLocales.isEmpty()) {
                Text(stringResource(R.string.historial_local_vacio), style = MaterialTheme.typography.bodySmall, color = TintaSuave)
            }
            accionesLocales.forEachIndexed { i, accion ->
                if (i > 0) Spacer(modifier = Modifier.height(10.dp))
                FilaAccionLocal(accion)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** Una acción de la base local: hora, detalle y estado (PENDIENTE / SINCRONIZADO / RECHAZADO). */
@Composable
private fun FilaAccionLocal(accion: AccionLocal) {
    val formato = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    val (texto, color) = when (accion.estado) {
        BitacoraLocal.SINCRONIZADO -> stringResource(R.string.estado_sincronizado) to Correcto
        BitacoraLocal.RECHAZADO -> stringResource(R.string.estado_rechazado) to Critico
        else -> stringResource(R.string.estado_pendiente) to Advertencia
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(accion.detalle, style = MaterialTheme.typography.bodySmall, color = Tinta)
            Text(
                text = formato.format(Date(accion.fecha)) +
                        (accion.sincronizadoEn?.let { " · " + stringResource(R.string.sincronizado_a_las, formato.format(Date(it))) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = TintaSuave
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        EtiquetaEstado(texto = texto, colorPunto = color)
    }
}

@Composable
private fun TarjetaMedida(
    modifier: Modifier,
    titulo: String,
    valor: String,
    unidad: String,
    rango: String,
    nivel: Nivel?
) {
    val (fondo, colorPunto, textoNivel) = when (nivel) {
        Nivel.CRITICO -> Triple(CriticoSuave, Critico, R.string.nivel_critico)
        Nivel.ADVERTENCIA -> Triple(AdvertenciaSuave, Advertencia, R.string.nivel_advertencia)
        Nivel.NORMAL -> Triple(Vidrio, Correcto, R.string.nivel_normal)
        null -> Triple(Vidrio, Linea, R.string.nivel_sin_datos)
    }
    Tarjeta(modifier = modifier, color = fondo) {
        Text(titulo, style = MaterialTheme.typography.labelLarge, color = TintaSuave)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = MaterialTheme.typography.displaySmall, color = Tinta)
            Text(
                text = unidad,
                style = MaterialTheme.typography.titleMedium,
                color = TintaSuave,
                modifier = Modifier.padding(start = 3.dp, bottom = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        EtiquetaEstado(
            texto = stringResource(textoNivel),
            colorPunto = colorPunto,
            fondo = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(rango, style = MaterialTheme.typography.labelSmall, color = TintaSuave)
    }
}

/**
 * Un equipo (bomba, ventilador, bocina, LED). Tocar el mosaico lo enciende/apaga (pasa a manual);
 * la etiqueta de abajo solo informa el modo (se cambia en Ajustes). Encendido = mosaico oscuro.
 */
@Composable
private fun TileEquipo(
    modifier: Modifier,
    nombre: String,
    estado: EstadoActuador,
    automaticoDisponible: Boolean,
    alCambiarEstado: (Boolean) -> Unit
) {
    val encendido = estado.encendido
    val fondo = if (encendido) Tinta else Vidrio
    val textoPrincipal by animateColorAsState(if (encendido) Color.White else Tinta, tween(350), label = "textoTile")
    val fondoPildora by animateColorAsState(
        if (encendido) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.8f), tween(350), label = "pildoraTile"
    )
    val textoPildora by animateColorAsState(if (encendido) Color.White else Tinta, tween(350), label = "textoPildoraTile")
    val esAuto = estado.automatico && automaticoDisponible

    Tarjeta(
        modifier = modifier,
        color = fondo,
        alHacerClic = { alCambiarEstado(!encendido) }
    ) {
        Text(nombre, style = MaterialTheme.typography.titleSmall, color = textoPrincipal)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(if (encendido) R.string.encendido else R.string.apagado),
            style = MaterialTheme.typography.headlineSmall,
            color = textoPrincipal
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(fondoPildora)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = stringResource(if (esAuto) R.string.modo_automatico else R.string.modo_manual),
                style = MaterialTheme.typography.labelMedium,
                color = textoPildora,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
