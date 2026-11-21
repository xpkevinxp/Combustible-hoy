package pe.combustiblehoy.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pe.combustiblehoy.core.Labels
import pe.combustiblehoy.core.Station
import pe.combustiblehoy.core.Ranking
import pe.combustiblehoy.core.SortMode
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.ui.MainViewModel

private val RADII = listOf(2, 5, 10, 25)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(vm: MainViewModel, settings: UserSettings, onOpenFavorites: () -> Unit = {}) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
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
    val askLocation: () -> Unit = {
        permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    LaunchedEffect(settings.department) { vm.loadStations() }
    LaunchedEffect(Unit) { if (hasLocationPermission && location == null) vm.locate(autoSelectDepartment = true) }

    val fuel = settings.fuel
    val filtered = remember(state.stations, query) { Ranking.search(state.stations, query) }
    val ranked = remember(filtered, fuel, location, settings.radiusKm, sort) {
        Ranking.nearby(filtered, fuel, location, settings.radiusKm.toDouble(), sort, limit = 100)
    }
    val cheapest = ranked.minOfOrNull { it.price }
    val average = ranked.map { it.price }.takeIf { it.isNotEmpty() }?.average()

    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    // Guardar o quitar siempre da respuesta visible; quitar se puede deshacer.
    val toggleFavorite: (Station) -> Unit = { st ->
        val wasFavorite = st.id in favoriteIds
        vm.toggleFavorite(st)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                if (wasFavorite) "Quitaste ${Labels.pretty(st.name)}" else "Guardado en Mis grifos",
                actionLabel = if (wasFavorite) "Deshacer" else "Ver",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (wasFavorite) vm.toggleFavorite(st) else onOpenFavorites()
            }
        }
    }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            ScreenTopBar("Grifos cercanos", dataSubtitle(state.dataDate), scroll) {
                IconButton(onClick = askLocation) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Usar mi ubicación")
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.stations.isNotEmpty(),
            onRefresh = { vm.refreshAll() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "search") {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Grifo, distrito o dirección", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda") } }
                        } else {
                            null
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                item(key = "fuel") { FuelSelector(fuel, vm::setFuel, Modifier.padding(top = 8.dp)) }
                item(key = "filters") {
                    // Fila desplazable: con letra grande o pantallas de 320 dp nada se corta.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { DepartmentPicker(settings.department, departments, vm::setDepartment) }
                        if (location != null) {
                            item {
                                DropdownChip(
                                    label = "${settings.radiusKm} km",
                                    icon = Icons.Outlined.Radar,
                                    options = RADII,
                                    optionLabel = { "Hasta $it km" },
                                    selected = settings.radiusKm,
                                    onSelect = vm::setRadius,
                                )
                            }
                            item {
                                DropdownChip(
                                    label = if (sort == SortMode.PRICE) "Más barato" else "Más cerca",
                                    icon = Icons.AutoMirrored.Filled.Sort,
                                    options = listOf(SortMode.PRICE, SortMode.DISTANCE),
                                    optionLabel = { if (it == SortMode.PRICE) "Más barato primero" else "Más cerca primero" },
                                    selected = sort,
                                    onSelect = { sort = it },
                                )
                            }
                        }
                    }
                }
                if (location == null) {
                    item(key = "location") {
                        Banner(
                            "Activa tu ubicación para ver la distancia y los grifos más cerca de ti.",
                            icon = Icons.Outlined.LocationOff,
                            action = { TextButton(onClick = askLocation) { Text("Activar") } },
                        )
                    }
                }
                if (favoriteIds.isEmpty() && ranked.isNotEmpty()) {
                    item(key = "tip") {
                        Banner(
                            "Toca ☆ en los grifos donde cargas para compararlos en Mis grifos y en el widget.",
                            icon = Icons.Outlined.StarOutline,
                        )
                    }
                }
                if (state.loading && state.stations.isEmpty()) {
                    items(5, key = { "skeleton$it" }) { SkeletonCard() }
                }

                when {
                    state.error != null && state.stations.isEmpty() -> item(key = "error") {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "No pudimos cargar los precios",
                            body = "Revisa tu conexión a internet e inténtalo otra vez.",
                            action = { Button(onClick = { vm.loadStations(refresh = true) }) { Text("Reintentar") } },
                        )
                    }
                    !state.loading && ranked.isEmpty() -> item(key = "empty") {
                        val nextRadius = RADII.firstOrNull { it > settings.radiusKm }
                        val action: (@Composable () -> Unit)? = when {
                            query.isNotBlank() -> {
                                { TextButton(onClick = { query = "" }) { Text("Borrar búsqueda") } }
                            }
                            location != null && nextRadius != null -> {
                                { TextButton(onClick = { vm.setRadius(nextRadius) }) { Text("Ampliar a $nextRadius km") } }
                            }
                            else -> null
                        }
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "Sin resultados",
                            body = when {
                                query.isNotBlank() -> "Ningún grifo con ${fuel.label} coincide con “$query”."
                                location != null -> "No hay grifos con ${fuel.label} a menos de ${settings.radiusKm} km. Prueba un radio mayor."
                                else -> "No hay grifos con ${fuel.label} en este departamento."
                            },
                            action = action,
                        )
                    }
                    else -> {
                        item(key = "summary") {
                            StatsStrip(
                                count = ranked.size,
                                cheapest = cheapest,
                                average = average?.takeIf { ranked.size > 2 },
                            )
                        }
                        items(ranked, key = { it.station.id }) { r ->
                            val st = r.station
                            StationCard(
                                name = st.name,
                                place = Labels.place(st.address, st.district),
                                price = r.price,
                                delta = st.delta(fuel),
                                distanceKm = r.distanceKm,
                                updated = st.updated,
                                approximateDistance = st.isLocationApproximate,
                                isFavorite = st.id in favoriteIds,
                                onToggleFavorite = { toggleFavorite(st) },
                                onDirections = { openDirections(context, st.lat, st.lon, st.name, st.address) },
                                best = ranked.size > 1 && r.price == cheapest,
                                modifier = Modifier.animateItem(),
                                onClick = { detailId = st.id },
                                vsAverage = average?.takeIf { ranked.size > 2 }?.let { r.price - it },
                            )
                        }
                    }
                }
            }
        }
    }

    ranked.firstOrNull { it.station.id == detailId }?.let { r ->
        val st = r.station
        StationSheet(
            info = StationInfo(
                id = st.id,
                name = st.name,
                address = st.address,
                district = st.district,
                lat = st.lat,
                lon = st.lon,
                prices = st.prices,
                deltas = st.deltas,
                updated = st.updated,
                distanceKm = r.distanceKm,
                approximateDistance = st.isLocationApproximate,
            ),
            selectedFuel = fuel,
            isFavorite = st.id in favoriteIds,
            onToggleFavorite = { toggleFavorite(st) },
            onDismiss = { detailId = null },
        )
    }
}
