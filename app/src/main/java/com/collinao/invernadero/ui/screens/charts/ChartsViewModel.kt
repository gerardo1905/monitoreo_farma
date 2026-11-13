package com.collinao.invernadero.ui.screens.charts

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collinao.invernadero.R
import com.collinao.invernadero.data.model.SensorData
import com.collinao.invernadero.data.model.Thresholds
import com.collinao.invernadero.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Ventana de tiempo que muestra la gráfica. */
enum class RangoGrafico(@StringRes val etiqueta: Int, val milisegundos: Long) {
    UNA_HORA(R.string.rango_1h, 3_600_000L),
    SEIS_HORAS(R.string.rango_6h, 21_600_000L),
    UN_DIA(R.string.rango_24h, 86_400_000L)
}

class ChartsViewModel(
    private val deviceRepository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    private val inicio = SharingStarted.WhileSubscribed(5000)

    private val _rango = MutableStateFlow(RangoGrafico.UNA_HORA)
    val rango: StateFlow<RangoGrafico> = _rango

    fun seleccionarRango(valor: RangoGrafico) {
        _rango.value = valor
    }

    val umbrales: StateFlow<Thresholds> = deviceRepository.observarThresholds()
        .catch { }
        .stateIn(viewModelScope, inicio, Thresholds())

    /** true si Firebase rechazó leer el historial (normalmente: reglas sin publicar). */
    private val _errorLecturas = MutableStateFlow<String?>(null)
    val errorLecturas: StateFlow<String?> = _errorLecturas

    /** Total de lecturas guardadas en las últimas 24 h (sin filtrar por rango). */
    private val _totalGuardadas = MutableStateFlow(0)
    val totalGuardadas: StateFlow<Int> = _totalGuardadas

    /** Lecturas dentro de la ventana elegida (la hora de referencia es la del servidor). */
    val lecturas: StateFlow<List<SensorData>> =
        combine(
            deviceRepository.observarLecturas()
                .onEach { _errorLecturas.value = null }
                .catch { e ->
                    android.util.Log.e("Graficas", "Error al leer lecturas", e)
                    _errorLecturas.value = "${e.javaClass.simpleName}: ${e.message}"
                    emit(emptyList())
                },
            _rango,
            deviceRepository.observarOffsetServidor().catch { emit(0L) }
        ) { todas, rango, offset ->
            _totalGuardadas.value = todas.size
            val desde = System.currentTimeMillis() + offset - rango.milisegundos
            todas.filter { it.timestamp >= desde }
        }.stateIn(viewModelScope, inicio, emptyList())
}
