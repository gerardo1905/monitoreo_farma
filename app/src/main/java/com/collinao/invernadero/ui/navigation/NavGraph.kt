package com.collinao.invernadero.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.collinao.invernadero.R
import com.collinao.invernadero.ui.components.BarraNavegacion
import com.collinao.invernadero.ui.components.FondoApp
import com.collinao.invernadero.ui.components.Icono
import com.collinao.invernadero.ui.components.Pestana
import com.collinao.invernadero.ui.screens.admin.AdminScreen
import com.collinao.invernadero.ui.screens.charts.ChartsScreen
import com.collinao.invernadero.ui.screens.dashboard.DashboardScreen
import com.collinao.invernadero.ui.screens.forgotpassword.ForgotPasswordScreen
import com.collinao.invernadero.ui.screens.history.HistoryScreen
import com.collinao.invernadero.ui.screens.login.LoginScreen
import com.collinao.invernadero.ui.screens.profile.ProfileScreen
import com.collinao.invernadero.ui.screens.register.RegisterScreen
import com.collinao.invernadero.ui.screens.settings.SettingsScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private val rutasPublicas = setOf(Routes.LOGIN, Routes.REGISTER, Routes.FORGOT_PASSWORD)

/** Pantallas donde se muestra la barra inferior (el panel de administración cuelga de Perfil). */
private val rutasConBarra = setOf(
    Routes.DASHBOARD, Routes.CHARTS, Routes.HISTORY, Routes.SETTINGS, Routes.PROFILE, Routes.ADMIN_PANEL
)

private const val DURACION = 320

/** Cambia de pestaña conservando el estado de cada una y sin apilar pantallas repetidas. */
private fun NavHostController.irAPestana(ruta: String) {
    navigate(ruta) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun NavGraph(
    startDestination: String,
    onSesionIniciada: () -> Unit = {},
    onSesionCerrada: () -> Unit = {},
    sessionViewModel: SessionViewModel = viewModel()
) {
    val navController = rememberNavController()
    val cuentaActiva by sessionViewModel.cuentaActiva.collectAsStateWithLifecycle()
    val sesionIniciada by sessionViewModel.sesionIniciada.collectAsStateWithLifecycle()
    val esAdmin by sessionViewModel.esAdmin.collectAsStateWithLifecycle()
    val entrada by navController.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route

    // Cuenta deshabilitada por un administrador mientras tenía la sesión abierta
    LaunchedEffect(cuentaActiva) {
        if (!cuentaActiva) {
            Firebase.auth.signOut()
            onSesionCerrada()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // La sesión se cerró por cualquier otro motivo (inactividad, etc.): volver al login
    LaunchedEffect(sesionIniciada) {
        if (!sesionIniciada) {
            val ruta = navController.currentBackStackEntry?.destination?.route
            if (ruta != null && ruta !in rutasPublicas) {
                onSesionCerrada()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val pestanas = buildList {
        add(Pestana(Routes.DASHBOARD, Icono.INICIO, stringResource(R.string.nav_inicio)))
        add(Pestana(Routes.CHARTS, Icono.GRAFICAS, stringResource(R.string.nav_graficas)))
        // Los registros de auditoría son solo para administradores
        if (esAdmin) add(Pestana(Routes.HISTORY, Icono.REGISTROS, stringResource(R.string.nav_registros)))
        // Los ajustes (rangos, automatización y modo de equipos) son solo para administradores
        if (esAdmin) add(Pestana(Routes.SETTINGS, Icono.AJUSTES, stringResource(R.string.nav_ajustes)))
        add(Pestana(Routes.PROFILE, Icono.PERFIL, stringResource(R.string.nav_perfil)))
    }
    // El panel de administración se considera parte de la pestaña Perfil
    val pestanaActiva = if (rutaActual == Routes.ADMIN_PANEL) Routes.PROFILE else rutaActual

    FondoApp {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { fadeIn(tween(DURACION)) + slideInHorizontally(tween(DURACION)) { it / 14 } },
                    exitTransition = { fadeOut(tween(DURACION / 2)) },
                    popEnterTransition = { fadeIn(tween(DURACION)) + slideInHorizontally(tween(DURACION)) { -it / 14 } },
                    popExitTransition = { fadeOut(tween(DURACION / 2)) + slideOutHorizontally(tween(DURACION)) { it / 14 } }
                ) {
                    composable(Routes.LOGIN) { LoginScreen(navController, onLoginSuccess = onSesionIniciada) }
                    composable(Routes.REGISTER) { RegisterScreen(navController, onRegisterSuccess = onSesionIniciada) }
                    composable(Routes.FORGOT_PASSWORD) { ForgotPasswordScreen(navController) }
                    composable(Routes.DASHBOARD) { DashboardScreen(navController) }
                    composable(Routes.SETTINGS) { SettingsScreen(navController) }
                    composable(Routes.HISTORY) { HistoryScreen(navController) }
                    composable(Routes.CHARTS) { ChartsScreen(navController) }
                    composable(Routes.PROFILE) { ProfileScreen(navController, onLogout = onSesionCerrada) }
                    composable(Routes.ADMIN_PANEL) { AdminScreen(navController) }
                }
            }

            AnimatedVisibility(
                visible = rutaActual in rutasConBarra,
                enter = slideInVertically(tween(DURACION)) { it } + fadeIn(tween(DURACION)),
                exit = slideOutVertically(tween(DURACION)) { it } + fadeOut(tween(DURACION / 2))
            ) {
                BarraNavegacion(
                    pestanas = pestanas,
                    rutaActual = pestanaActiva,
                    alSeleccionar = { navController.irAPestana(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
