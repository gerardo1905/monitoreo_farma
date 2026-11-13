package com.collinao.invernadero.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.security.PasswordPolicy
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.*

@Composable
fun RegisterScreen(
    navController: NavHostController,
    onRegisterSuccess: () -> Unit = {},
    viewModel: RegisterViewModel = viewModel()
) {
    val correo = viewModel.email.trim().lowercase()
    val requisitos = PasswordPolicy.requisitos(viewModel.password, correo)
    val nivel = PasswordPolicy.fortaleza(viewModel.password, correo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        EncabezadoPantalla(
            titulo = stringResource(R.string.registro_titulo),
            alVolver = { navController.popBackStack() },
            subtitulo = stringResource(R.string.registro_subtitulo)
        )

        Spacer(modifier = Modifier.height(24.dp))

        CampoTexto(
            valor = viewModel.email,
            alCambiar = viewModel::onEmailChange,
            etiqueta = stringResource(R.string.correo),
            teclado = KeyboardType.Email
        )
        Spacer(modifier = Modifier.height(12.dp))
        CampoTexto(
            valor = viewModel.password,
            alCambiar = viewModel::onPasswordChange,
            etiqueta = stringResource(R.string.contrasena),
            esContrasena = true
        )

        if (viewModel.password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val colorNivel = when (nivel) {
                1 -> Critico
                2 -> Advertencia
                else -> Correcto
            }
            LinearProgressIndicator(
                progress = { nivel / 4f },
                color = colorNivel,
                trackColor = Linea,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.registro_seguridad, stringResource(PasswordPolicy.etiquetaFortaleza(nivel))),
                style = MaterialTheme.typography.labelMedium,
                color = TintaSuave
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Tarjeta(modifier = Modifier.fillMaxWidth()) {
            requisitos.forEach { req ->
                Text(
                    text = (if (req.cumplido) "✓  " else "○  ") +
                            stringResource(req.textoId, *req.argumentos.toTypedArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (req.cumplido) Correcto else TintaSuave,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CampoTexto(
            valor = viewModel.confirmPassword,
            alCambiar = viewModel::onConfirmPasswordChange,
            etiqueta = stringResource(R.string.contrasena_confirmar),
            esContrasena = true
        )

        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        BotonPrincipal(
            texto = stringResource(R.string.registro_boton),
            alHacerClic = {
                viewModel.register {
                    onRegisterSuccess()
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            },
            cargando = viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))
        BotonTexto(
            texto = stringResource(R.string.registro_ya_tengo),
            alHacerClic = { navController.popBackStack() },
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
        )
    }
}
