package pe.combustiblehoy.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pe.combustiblehoy.app
import pe.combustiblehoy.core.Department
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Geo
import pe.combustiblehoy.core.LatLon
import pe.combustiblehoy.core.Station
import pe.combustiblehoy.data.FavoriteEntity
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.widget.FavoritesWidget
import pe.combustiblehoy.work.SyncWorker

data class StationsState(
    val loading: Boolean = false,
    val error: String? = null,
    val stations: List<Station> = emptyList(),
    val dataDate: String = "",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    private val repo = ctx.app.repository
    private val store = ctx.app.settings

    val settings: StateFlow<UserSettings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites: StateFlow<List<FavoriteEntity>> = repo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favoriteIds: StateFlow<Set<String>> = repo.favoriteIds.map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _departments = MutableStateFlow<List<Department>>(emptyList())
    val departments: StateFlow<List<Department>> = _departments.asStateFlow()

    private val _stations = MutableStateFlow(StationsState())
    val stations: StateFlow<StationsState> = _stations.asStateFlow()

    private val _location = MutableStateFlow<LatLon?>(null)
    val location: StateFlow<LatLon?> = _location.asStateFlow()

    init {
        viewModelScope.launch {
            _departments.value = repo.meta()?.departments.orEmpty()
            _departments.value = repo.meta(refresh = true)?.departments ?: _departments.value
        }
    }

    fun loadStations(refresh: Boolean = false) = viewModelScope.launch {
        val dept = store.current().department
        _stations.update { it.copy(loading = true, error = null) }
        runCatching { repo.stations(dept, refresh) }
            .onSuccess { list -> _stations.value = StationsState(stations = list, dataDate = repo.meta()?.date.orEmpty()) }
            .onFailure { e ->
                _stations.update { it.copy(loading = false, error = "No se pudieron cargar los precios (${e.message}).") }
            }
    }

    fun refreshAll() {
        loadStations(refresh = true)
        SyncWorker.runNow(ctx, force = true)
    }

    /** Requiere permiso de ubicación concedido (lo pide la UI). */
    @SuppressLint("MissingPermission")
    fun locate(autoSelectDepartment: Boolean) = viewModelScope.launch {
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val loc = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                ?: client.lastLocation.await()
        }.getOrNull() ?: return@launch
        val here = LatLon(loc.latitude, loc.longitude)
        _location.value = here
        if (autoSelectDepartment) {
            val available = _departments.value.map { it.slug }.ifEmpty { Geo.departmentCenters.keys.toList() }
            val dept = Geo.nearestDepartment(here, available)
            if (dept != null && dept != store.current().department) {
                store.setDepartment(dept)
                loadStations()
            }
        }
    }

    fun toggleFavorite(st: Station) = viewModelScope.launch {
        if (st.id in favoriteIds.value) repo.removeFavorite(st.id) else repo.addFavorite(st)
        FavoritesWidget.refreshAll(ctx)
    }

    fun removeFavorite(id: String) = viewModelScope.launch {
        repo.removeFavorite(id)
        FavoritesWidget.refreshAll(ctx)
    }

    fun completeOnboarding(fuel: Fuel, department: String) = viewModelScope.launch {
        store.completeOnboarding(fuel, department)
        loadStations()
    }

    fun setFuel(fuel: Fuel) = viewModelScope.launch {
        store.setFuel(fuel)
        FavoritesWidget.refreshAll(ctx)
    }

    fun setDepartment(slug: String) = viewModelScope.launch {
        store.setDepartment(slug)
        loadStations()
    }

    fun setRadius(km: Int) = viewModelScope.launch { store.setRadius(km) }
    fun setAlertsEnabled(on: Boolean) = viewModelScope.launch { store.setAlertsEnabled(on) }
    fun setAlertThreshold(v: Double) = viewModelScope.launch { store.setAlertThreshold(v) }
    fun setOnlyDrops(on: Boolean) = viewModelScope.launch { store.setOnlyDrops(on) }
}
