package pe.combustiblehoy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.core.Ranking
import pe.combustiblehoy.core.SortMode
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.ui.MainViewModel

private val RADII = listOf(2, 5, 10, 25)

@Composable
fun NearbyScreen(vm: MainViewModel, settings: UserSettings) {
    val context = LocalContext.current
    val state by vm.stations.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val departments by vm.departments.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(SortMode.PRICE) }
    var query by rememberSaveable { mutableStateOf("") }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.locate(autoSelectDepartment = true)
    }

    LaunchedEffect(settings.department) { vm.loadStations() }
    LaunchedEffect(Unit) { if (hasLocationPermission && location == null) vm.locate(autoSelectDepartment = true) }

    val filtered = remember(state.stations, query) { Ranking.search(state.stations, query) }
    val ranked = remember(filtered, settings.fuel, location, settings.radiusKm, sort) {
        Ranking.nearby(filtered, settings.fuel, location, settings.radiusKm.toDouble(), sort, limit = 100)
    }
    val cheapest = ranked.minOfOrNull { it.price }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Grifos cercanos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Icon(Icons.Filled.MyLocation, contentDescription = "Mi ubicación") }
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            DepartmentPicker(settings.department, departments) { vm.setDepartment(it) }
            if (state.dataDate.isNotBlank()) {
                Text(
                    "  Datos del ${state.dataDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FuelChips(settings.fuel, vm::setFuel, Modifier.padding(vertical = 4.dp))
        if (location != null) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RADII.forEach { r ->
                    FilterChip(selected = settings.radiusKm == r, onClick = { vm.setRadius(r) }, label = { Text("$r km") })
                }
                TextButton(onClick = { sort = if (sort == SortMode.PRICE) SortMode.DISTANCE else SortMode.PRICE }) {
                    Text(if (sort == SortMode.PRICE) "Por precio" else "Por distancia")
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Buscar grifo, distrito o dirección") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            state.error != null && state.stations.isEmpty() ->
                EmptyMessage(state.error!!) { TextButton(onClick = { vm.loadStations(refresh = true) }) { Text("Reintentar") } }
            !state.loading && ranked.isEmpty() ->
                EmptyMessage(
                    if (location != null) "No hay grifos con ${settings.fuel.label} en ${settings.radiusKm} km. Prueba un radio mayor."
                    else "No hay grifos con ${settings.fuel.label} en este departamento.",
                )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(ranked, key = { it.station.id }) { r ->
                    val st = r.station
                    StationCard(
                        name = st.name,
                        subtitle = listOf(st.address, st.district.lowercase().replaceFirstChar { it.titlecase() })
                            .filter { it.isNotBlank() }.joinToString(", "),
                        price = r.price,
                        delta = st.delta(settings.fuel),
                        distanceKm = r.distanceKm,
                        isFavorite = st.id in favoriteIds,
                        onToggleFavorite = { vm.toggleFavorite(st) },
                        onDirections = { openDirections(context, st.lat, st.lon, st.name, st.address) },
                        highlight = r.price == cheapest,
                    )
                }
            }
        }
    }
}
