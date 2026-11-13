package com.collinao.invernadero.ui.screens.charts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.theme.Acento
import com.collinao.invernadero.ui.theme.TintaSuave

@Composable
fun ChartsScreen(
    navController: NavHostController,
    viewModel: ChartsViewModel = viewModel()
) {
    val rango by viewModel.rango.collectAsStateWithLifecycle()
    val lecturas by viewModel.lecturas.collectAsStateWithLifecycle()
    val umbrales by viewModel.umbrales.collectAsStateWithLifecycle()
    val errorLecturas by viewModel.errorLecturas.collectAsStateWithLifecycle()
    val totalGuardadas by viewModel.totalGuardadas.collectAsStateWithLifecycle()

    val temperaturas = lecturas.map { PuntoGrafico(it.timestamp, it.temperatura.toFloat()) }
    val humedades = lecturas.map { PuntoGrafico(it.timestamp, it.humedadAire.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        EncabezadoPantalla(
            titulo = stringResource(R.string.graficas_titulo)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RangoGrafico.entries.forEach { opcion ->
                Pildora(
                    texto = stringResource(opcion.etiqueta),
                    seleccionada = opcion == rango,
                    alHacerClic = { viewModel.seleccionarRango(opcion) }
                )
            }
        }

        if (errorLecturas != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.graficas_error_permiso, errorLecturas ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = com.collinao.invernadero.ui.theme.Critico
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.graficas_total, totalGuardadas),
                style = MaterialTheme.typography.labelSmall,
                color = TintaSuave
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Aparece(indice = 1) {
            TarjetaGrafica(
                clave = rango,
                titulo = stringResource(R.string.temperatura),
                unidad = "°C",
                puntos = temperaturas,
                color = Acento,
                limiteSuperior = umbrales.temperaturaMax,
                limiteInferior = umbrales.temperaturaMin
            )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Aparece(indice = 2) {
            TarjetaGrafica(
                clave = rango,
                titulo = stringResource(R.string.humedad),
                unidad = "%",
                puntos = humedades,
                color = Color(0xFF5B6CF0),
                limiteSuperior = umbrales.humedadAireMax,
                limiteInferior = umbrales.humedadAireMin
            )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.graficas_nota),
                style = MaterialTheme.typography.labelSmall,
                color = TintaSuave
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TarjetaGrafica(
    clave: Any,
    titulo: String,
    unidad: String,
    puntos: List<PuntoGrafico>,
    color: Color,
    limiteSuperior: Float,
    limiteInferior: Float
) {
    Tarjeta(modifier = Modifier.fillMaxWidth()) {
        Text(text = titulo, style = MaterialTheme.typography.titleMedium)

        if (puntos.isNotEmpty()) {
            val valores = puntos.map { it.valor }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Dato(stringResource(R.string.grafica_actual), "%.1f %s".format(valores.last(), unidad))
                Dato(stringResource(R.string.grafica_min), "%.1f".format(valores.min()))
                Dato(stringResource(R.string.grafica_max), "%.1f".format(valores.max()))
                Dato(stringResource(R.string.grafica_prom), "%.1f".format(valores.average()))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        LineChart(
            puntos = puntos,
            color = color,
            unidad = unidad,
            limiteSuperior = limiteSuperior,
            limiteInferior = limiteInferior,
            claveAnimacion = clave
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(text = etiqueta, style = MaterialTheme.typography.labelSmall, color = TintaSuave)
        Text(text = valor, style = MaterialTheme.typography.titleSmall)
    }
}
