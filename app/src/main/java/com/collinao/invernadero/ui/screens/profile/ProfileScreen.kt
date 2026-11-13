package com.collinao.invernadero.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.InvernaderoApp
import com.collinao.invernadero.R
import com.collinao.invernadero.security.PasswordPolicy
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavHostController,
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val app = LocalContext.current.applicationContext as InvernaderoApp
    val scope = rememberCoroutineScope()
    var cerrando by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            EncabezadoPantalla(
                titulo = stringResource(R.string.perfil_titulo)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Avatar con la inicial, correo y estados ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Tinta),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.email.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(viewModel.email, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EtiquetaEstado(
                        texto = stringResource(if (viewModel.esAdmin) R.string.rol_admin else R.string.rol_usuario),
                        colorPunto = Acento
                    )
                    EtiquetaEstado(
                        texto = stringResource(
                            if (viewModel.correoVerificado) R.string.correo_verificado else R.string.correo_sin_verificar
                        ),
                        colorPunto = if (viewModel.correoVerificado) Correcto else Advertencia
                    )
                }
            }

            if (!viewModel.correoVerificado) {
                BotonTexto(
                    texto = stringResource(R.string.reenviar_verificacion),
                    alHacerClic = { viewModel.reenviarVerificacion() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            if (viewModel.mensaje != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Tarjeta(modifier = Modifier.fillMaxWidth(), color = CorrectoSuave) {
                    Text(viewModel.mensaje ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            BotonSecundario(
                texto = stringResource(R.string.cambiar_contrasena),
                alHacerClic = { viewModel.abrirCambioPassword() }
            )

            if (viewModel.esAdmin) {
                Spacer(modifier = Modifier.height(12.dp))
                BotonSecundario(
                    texto = stringResource(R.string.panel_admin),
                    alHacerClic = { navController.navigate(Routes.ADMIN_PANEL) }
                )
            }
        }

        BotonSecundario(
            texto = stringResource(R.string.cerrar_sesion),
            colorTexto = Critico,
            habilitado = !cerrando,
            alHacerClic = {
                if (cerrando) return@BotonSecundario
                cerrando = true
                scope.launch {
                    // Deja registro, apaga simulador y alarma, y recién entonces cierra la sesión
                    app.cerrarSesion()
                    onLogout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (viewModel.mostrarCambioPassword) {
        DialogCambioPassword(viewModel)
    }
}

@Composable
private fun DialogCambioPassword(viewModel: ProfileViewModel) {
    val requisitos = PasswordPolicy.requisitos(viewModel.passNueva, viewModel.email)

    AlertDialog(
        onDismissRequest = { if (!viewModel.cambiandoPassword) viewModel.cerrarCambioPassword() },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        title = { Text(stringResource(R.string.cambiar_contrasena)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CampoTexto(
                    valor = viewModel.passActual,
                    alCambiar = viewModel::onPassActualChange,
                    etiqueta = stringResource(R.string.contrasena_actual),
                    esContrasena = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                CampoTexto(
                    valor = viewModel.passNueva,
                    alCambiar = viewModel::onPassNuevaChange,
                    etiqueta = stringResource(R.string.contrasena_nueva),
                    esContrasena = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                requisitos.forEach { req ->
                    Text(
                        text = (if (req.cumplido) "✓  " else "○  ") +
                                stringResource(req.textoId, *req.argumentos.toTypedArray()),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (req.cumplido) Correcto else TintaSuave
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                CampoTexto(
                    valor = viewModel.passConfirmar,
                    alCambiar = viewModel::onPassConfirmarChange,
                    etiqueta = stringResource(R.string.contrasena_nueva_confirmar),
                    esContrasena = true
                )
                if (viewModel.errorPassword != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.errorPassword ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.cambiarPassword() },
                enabled = !viewModel.cambiandoPassword
            ) { Text(stringResource(R.string.guardar), color = Tinta) }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.cerrarCambioPassword() },
                enabled = !viewModel.cambiandoPassword
            ) { Text(stringResource(R.string.cancelar), color = TintaSuave) }
        }
    )
}
