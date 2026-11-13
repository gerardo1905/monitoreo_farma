package com.collinao.invernadero.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.*
import com.collinao.invernadero.ui.navigation.Routes
import com.collinao.invernadero.ui.theme.TintaSuave

@Composable
fun AdminScreen(
    navController: NavHostController,
    viewModel: AdminViewModel = viewModel()
) {
    val esAdmin by viewModel.esAdmin.collectAsStateWithLifecycle()
    val usuarios by viewModel.usuarios.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        EncabezadoPantalla(
            titulo = stringResource(R.string.admin_titulo),
            alVolver = { navController.popBackStack() }
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (esAdmin) {
            null -> Text(stringResource(R.string.verificando_permisos), color = TintaSuave)

            false -> Text(
                text = stringResource(R.string.admin_restringido),
                color = MaterialTheme.colorScheme.error
            )

            true -> {
                BotonPrincipal(
                    texto = stringResource(R.string.admin_ver_registros),
                    alHacerClic = { navController.navigate(Routes.HISTORY) }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.admin_usuarios, usuarios.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = TintaSuave
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(usuarios, key = { it.first }) { (uid, perfil) ->
                        val esUnoMismo = uid == viewModel.miUid
                        Tarjeta(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Text(
                                text = perfil.email + if (esUnoMismo) " " + stringResource(R.string.admin_tu) else "",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            FilaInterruptor(
                                texto = stringResource(R.string.admin_administrador),
                                marcado = perfil.rol == "admin",
                                habilitado = !esUnoMismo,
                                alCambiar = { viewModel.cambiarRol(uid, perfil.email, it) }
                            )
                            FilaInterruptor(
                                texto = stringResource(R.string.admin_cuenta_activa),
                                marcado = perfil.activo,
                                habilitado = !esUnoMismo,
                                alCambiar = { viewModel.cambiarEstado(uid, perfil.email, it) }
                            )
                        }
                    }
                }
            }
        }

        if (esAdmin != true) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FilaInterruptor(texto: String, marcado: Boolean, habilitado: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, style = MaterialTheme.typography.bodyLarge)
        InterruptorApp(marcado = marcado, alCambiar = alCambiar, habilitado = habilitado)
    }
}
