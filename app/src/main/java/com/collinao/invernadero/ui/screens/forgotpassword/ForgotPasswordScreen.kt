package com.collinao.invernadero.ui.screens.forgotpassword

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.theme.CorrectoSuave
import com.collinao.invernadero.ui.theme.TintaSuave

@Composable
fun ForgotPasswordScreen(
    navController: NavHostController,
    viewModel: ForgotPasswordViewModel = viewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        EncabezadoPantalla(
            titulo = stringResource(R.string.recuperar_titulo),
            alVolver = { navController.popBackStack() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recuperar_descripcion),
            style = MaterialTheme.typography.bodyMedium,
            color = TintaSuave
        )

        Spacer(modifier = Modifier.height(28.dp))

        CampoTexto(
            valor = viewModel.email,
            alCambiar = viewModel::onEmailChange,
            etiqueta = stringResource(R.string.correo),
            teclado = KeyboardType.Email
        )

        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = viewModel.errorMessage ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (viewModel.successMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Tarjeta(modifier = Modifier.fillMaxWidth(), color = CorrectoSuave) {
                Text(
                    text = viewModel.successMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        BotonPrincipal(
            texto = stringResource(R.string.recuperar_boton),
            alHacerClic = { viewModel.sendResetEmail() },
            cargando = viewModel.isLoading
        )
    }
}
