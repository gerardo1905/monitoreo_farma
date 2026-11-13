package com.collinao.invernadero.ui.screens.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.AuditCategoria
import com.collinao.invernadero.data.model.AuditEvent
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una fila de la lista: o un encabezado de día, o un registro. */
private sealed interface Fila {
    data class Dia(val titulo: String) : Fila
    data class Registro(val evento: AuditEvent) : Fila
}

@Composable
fun HistoryScreen(
    navController: NavHostController,
    viewModel: HistoryViewModel = viewModel()
) {
    val esAdmin by viewModel.esAdmin.collectAsStateWithLifecycle()
    val categoria by viewModel.categoria.collectAsStateWithLifecycle()
    val registros by viewModel.registros.collectAsStateWithLifecycle()

    val formatoDia = remember { SimpleDateFormat("EEEE dd/MM/yyyy", Locale.getDefault()) }
    val formatoHora = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 12.dp)) {
        EncabezadoPantalla(
            titulo = stringResource(R.string.registros_titulo)
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (esAdmin) {
            null -> Text(stringResource(R.string.verificando_permisos), color = TintaSuave)

            false -> Text(
                text = stringResource(R.string.registros_restringido),
                color = MaterialTheme.colorScheme.error
            )

            true -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AuditCategoria.todas.forEach { cat ->
                        Pildora(
                            texto = AuditCategoria.titulo(cat),
                            seleccionada = cat == categoria,
                            alHacerClic = { viewModel.seleccionarCategoria(cat) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.registros_cantidad, registros.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (registros.isEmpty()) {
                    Text(stringResource(R.string.registros_vacio), color = TintaSuave)
                } else {
                    // Agrupa por día para que la lista sea fácil de recorrer
                    val filas = remember(registros) {
                        buildList<Fila> {
                            var diaActual = ""
                            registros.forEach { ev ->
                                val dia = formatoDia.format(Date(ev.timestamp))
                                if (dia != diaActual) {
                                    diaActual = dia
                                    add(Fila.Dia(dia.replaceFirstChar { it.uppercase() }))
                                }
                                add(Fila.Registro(ev))
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filas, key = {
                            when (it) {
                                is Fila.Dia -> "dia-${it.titulo}"
                                is Fila.Registro -> it.evento.id.ifEmpty { "t-${it.evento.timestamp}" }
                            }
                        }) { fila ->
                            when (fila) {
                                is Fila.Dia -> Text(
                                    text = fila.titulo,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TintaSuave,
                                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                                )

                                is Fila.Registro -> TarjetaRegistro(fila.evento, formatoHora)
                            }
                        }
                    }
                }
            }
        }

        if (esAdmin != true) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TarjetaRegistro(evento: AuditEvent, formatoHora: SimpleDateFormat) {
    Tarjeta(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        radio = 18.dp,
        relleno = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = evento.tipo.replace('_', ' '),
                style = MaterialTheme.typography.titleSmall,
                color = colorDeTipo(evento.tipo)
            )
            Text(
                text = formatoHora.format(Date(evento.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = evento.detalle, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.registros_por, evento.usuario),
            style = MaterialTheme.typography.labelSmall,
            color = TintaSuave
        )
    }
}

/** Colores simples para reconocer de un vistazo lo importante. */
@Composable
private fun colorDeTipo(tipo: String): Color = when {
    tipo.contains("FALLIDO") || tipo.contains("BLOQUEADA") || tipo.contains("RECHAZADO") ||
            tipo.contains("DESHABILITADA") || tipo == "ALERTA_CRITICA" -> Critico

    tipo.contains("EXITOSO") || tipo.contains("CREADA") || tipo.contains("HABILITADA") ||
            tipo.contains("NORMALIZADA") -> Color(0xFF1F8A4C)

    else -> MaterialTheme.colorScheme.onSurface
}
