package com.collinao.invernadero.ui.screens.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.CriticoOscuro
import com.collinao.invernadero.ui.theme.CriticoSuave
import com.collinao.invernadero.ui.theme.TintaSuave

@Composable
fun LoginScreen(
    navController: NavHostController,
    onLoginSuccess: () -> Unit = {},
    viewModel: LoginViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Marca()
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.login_titulo), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.login_subtitulo),
            style = MaterialTheme.typography.bodyMedium,
            color = TintaSuave,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

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

        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            if (viewModel.bloqueada) {
                Tarjeta(modifier = Modifier.fillMaxWidth(), color = CriticoSuave) {
                    Text(
                        text = viewModel.errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CriticoOscuro
                    )
                }
            } else {
                Text(
                    text = viewModel.errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        BotonPrincipal(
            texto = stringResource(R.string.login_boton),
            alHacerClic = {
                viewModel.login {
                    onLoginSuccess()
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            },
            habilitado = !viewModel.bloqueada,
            cargando = viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.bloqueada) {
            BotonSecundario(
                texto = stringResource(R.string.recuperar_contrasena),
                alHacerClic = { navController.navigate(Routes.FORGOT_PASSWORD) }
            )
        } else {
            BotonTexto(
                texto = stringResource(R.string.login_olvide),
                alHacerClic = { navController.navigate(Routes.FORGOT_PASSWORD) }
            )
        }

        BotonTexto(
            texto = stringResource(R.string.login_sin_cuenta),
            alHacerClic = { navController.navigate(Routes.REGISTER) }
        )
    }
}
