package com.collinao.invernadero.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.theme.*

@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = viewModel()
) {
    val editable = viewModel.esAdmin

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            EncabezadoPantalla(
                titulo = stringResource(R.string.ajustes_titulo),
                subtitulo = if (!editable) stringResource(R.string.ajustes_solo_admin) else null
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Rol que detectó la app: ayuda a entender por qué los campos están (o no) habilitados
            EtiquetaEstado(
                texto = stringResource(R.string.ajustes_rol_detectado, viewModel.rolDetectado ?: "…"),
                colorPunto = if (editable) Correcto else Advertencia
            )

            // Error al guardar en Firebase (por ejemplo, permiso denegado por las reglas)
            AnimatedVisibility(
                visible = viewModel.errorGuardado != null,
                enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Tarjeta(modifier = Modifier.fillMaxWidth(), color = CriticoSuave) {
                        Text(
                            text = viewModel.errorGuardado ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CriticoOscuro
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 1. Automatización ---
            Aparece(indice = 0) {
                Tarjeta(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ajustes_automatizacion), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.ajustes_automatizacion_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = TintaSuave
                            )
                        }
                        InterruptorApp(
                            marcado = viewModel.automatizacionActiva,
                            alCambiar = { viewModel.onAutomatizacionChange(it) },
                            habilitado = editable
                        )
                    }
                }
            }

            // --- 1b. Modo de cada equipo ---
            Spacer(modifier = Modifier.height(24.dp))
            Aparece(indice = 1) {
                Column {
                    SeccionTitulo(R.string.ajustes_equipos_titulo, R.string.ajustes_equipos_desc)
                    Tarjeta(modifier = Modifier.fillMaxWidth()) {
                        com.collinao.invernadero.data.model.ActuadorId.todos.forEachIndexed { i, id ->
                            if (i > 0) Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(nombreActuador(id), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(
                                            if (viewModel.automaticos[id] != false) R.string.modo_automatico
                                            else R.string.modo_manual
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TintaSuave
                                    )
                                }
                                InterruptorApp(
                                    marcado = viewModel.automaticos[id] != false,
                                    alCambiar = { viewModel.onModoChange(id, it) },
                                    habilitado = editable
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. Rango recomendado ---
            Spacer(modifier = Modifier.height(24.dp))
            Aparece(indice = 2) {
                Column {
                    SeccionTitulo(R.string.ajustes_umbrales_titulo, R.string.ajustes_umbrales_desc)
                    Tarjeta(modifier = Modifier.fillMaxWidth()) {
                        GrupoMinMax(viewModel, R.string.temperatura, "°C", Umbral.TEMP_MIN, Umbral.TEMP_MAX, editable)
                        Spacer(modifier = Modifier.height(14.dp))
                        GrupoMinMax(viewModel, R.string.humedad, "%", Umbral.HUM_MIN, Umbral.HUM_MAX, editable)
                    }
                }
            }

            // --- 3. Alarma crítica ---
            Spacer(modifier = Modifier.height(24.dp))
            Aparece(indice = 3) {
                Column {
                    SeccionTitulo(R.string.ajustes_alarma_titulo, R.string.ajustes_alarma_desc)
                    Tarjeta(modifier = Modifier.fillMaxWidth()) {
                        GrupoMinMax(viewModel, R.string.temperatura, "°C", Umbral.ALARMA_TEMP_MIN, Umbral.ALARMA_TEMP_MAX, editable)
                        Spacer(modifier = Modifier.height(14.dp))
                        GrupoMinMax(viewModel, R.string.humedad, "%", Umbral.ALARMA_HUM_MIN, Umbral.ALARMA_HUM_MAX, editable)
                    }
                }
            }

            // --- 4. Qué pasa en una emergencia (informativo) ---
            Spacer(modifier = Modifier.height(24.dp))
            Aparece(indice = 4) {
                Column {
                    SeccionTitulo(R.string.ajustes_emergencia_titulo, R.string.ajustes_emergencia_desc)
                    Tarjeta(modifier = Modifier.fillMaxWidth()) {
                        PasoEmergencia("1", stringResource(R.string.ajustes_emergencia_paso1))
                        PasoEmergencia("2", stringResource(R.string.ajustes_emergencia_paso2))
                        PasoEmergencia("3", stringResource(R.string.ajustes_emergencia_paso3))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Barra de guardado: aparece solo si hay cambios sin guardar ---
        AnimatedVisibility(
            visible = viewModel.hayCambios || viewModel.guardadoReciente,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                if (viewModel.guardadoReciente && !viewModel.hayCambios) {
                    EtiquetaEstado(
                        texto = stringResource(R.string.ajustes_guardado),
                        colorPunto = Correcto,
                        fondo = CorrectoSuave
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            BotonPrincipal(
                                texto = stringResource(R.string.ajustes_guardar),
                                alHacerClic = { viewModel.guardar() }
                            )
                        }
                        TextButton(onClick = { viewModel.descartar() }) {
                            Text(stringResource(R.string.ajustes_descartar), color = TintaSuave)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeccionTitulo(titulo: Int, descripcion: Int) {
    Text(stringResource(titulo), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(descripcion),
        style = MaterialTheme.typography.bodySmall,
        color = TintaSuave,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

/** Un grupo "Temperatura" o "Humedad": nombre arriba y dos campos (mínimo y máximo) lado a lado. */
@Composable
private fun GrupoMinMax(
    vm: SettingsViewModel,
    nombre: Int,
    unidad: String,
    minimo: Umbral,
    maximo: Umbral,
    editable: Boolean
) {
    Text(stringResource(nombre), style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CampoNumero(
            modifier = Modifier.weight(1f),
            vm = vm, umbral = minimo, etiqueta = stringResource(R.string.ajustes_minimo),
            unidad = unidad, editable = editable
        )
        CampoNumero(
            modifier = Modifier.weight(1f),
            vm = vm, umbral = maximo, etiqueta = stringResource(R.string.ajustes_maximo),
            unidad = unidad, editable = editable
        )
    }
}

@Composable
private fun CampoNumero(
    modifier: Modifier,
    vm: SettingsViewModel,
    umbral: Umbral,
    etiqueta: String,
    unidad: String,
    editable: Boolean
) {
    val error = vm.errores[umbral]
    OutlinedTextField(
        value = vm.textos[umbral] ?: "",
        onValueChange = { vm.onCambio(umbral, it) },
        label = { Text(etiqueta) },
        suffix = { Text(unidad, color = TintaSuave) },
        trailingIcon = if (umbral.esTemperatura && editable) {
            {
                // Algunos teclados numéricos no traen el signo menos: este botón lo alterna
                TextButton(onClick = {
                    val actual = vm.textos[umbral] ?: ""
                    vm.onCambio(umbral, if (actual.startsWith("-")) actual.drop(1) else "-$actual")
                }) { Text("±", color = TintaSuave) }
            }
        } else null,
        enabled = editable,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White.copy(alpha = 0.75f),
            disabledContainerColor = Color.White.copy(alpha = 0.45f),
            focusedBorderColor = Tinta,
            unfocusedBorderColor = Linea,
            disabledBorderColor = Linea,
            disabledTextColor = Tinta,
            disabledLabelColor = TintaSuave,
            focusedLabelColor = Tinta,
            cursorColor = Tinta
        ),
        modifier = modifier
    )
}

@Composable
private fun PasoEmergencia(numero: String, texto: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = numero,
            style = MaterialTheme.typography.titleSmall,
            color = Acento,
            modifier = Modifier.width(22.dp)
        )
        Text(text = texto, style = MaterialTheme.typography.bodyMedium)
    }
}
