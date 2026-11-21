package pe.combustiblehoy.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.combustiblehoy.core.DataJson
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Labels
import pe.combustiblehoy.data.FavoriteEntity
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.data.delta
import pe.combustiblehoy.data.price
import pe.combustiblehoy.data.prices
import pe.combustiblehoy.ui.MainViewModel
import pe.combustiblehoy.widget.FavoritesWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    vm: MainViewModel,
    settings: UserSettings,
    onAddFavorites: () -> Unit,
    openStationId: String? = null,
    onStationOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val fuel = settings.fuel
    val sorted = remember(favorites, fuel) {
        favorites.sortedWith(compareBy<FavoriteEntity>({ it.price(fuel) == null }, { it.price(fuel) ?: 0.0 }))
    }
    val best = sorted.firstOrNull()?.takeIf { it.price(fuel) != null }
    val maxPrice = sorted.mapNotNull { it.price(fuel) }.maxOrNull()

    // El refresco real lo hace WorkManager; el indicador se apaga al llegar datos nuevos o tras unos segundos.
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(6_000); refreshing = false } }
    LaunchedEffect(settings.lastSyncAt) { refreshing = false }
    val refresh: () -> Unit = { refreshing = true; vm.refreshAll() }

    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    // Quitar es inmediato (sin diálogos), pero reversible: patrón "deshacer" en vez de confirmar.
    val remove: (FavoriteEntity) -> Unit = { fav ->
        vm.removeFavorite(fav.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                "Quitaste ${Labels.pretty(fav.name)}",
                actionLabel = "Deshacer",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) vm.restoreFavorite(fav)
        }
    }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    // Llegada desde el widget: abre el detalle del grifo tocado.
    LaunchedEffect(openStationId) {
        if (openStationId != null) { detailId = openStationId; onStationOpened() }
    }
    val drops = remember(favorites, fuel) {
        favorites.mapNotNull { f -> f.delta(fuel)?.takeIf { it <= -0.005 }?.let { f to -it } }
    }

    var hasWidget by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        hasWidget = widgetCount(context) > 0
        onPauseOrDispose { }
    }

    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            ScreenTopBar("Mis grifos", dataSubtitle(settings.lastDataDate), scroll) {
                IconButton(onClick = refresh) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar precios") }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (Dates.isStale(settings.lastDataDate)) {
                    item(key = "stale") {
                        Banner(
                            "Los datos son del ${Dates.pretty(settings.lastDataDate)}. Revisa tu conexión.",
                            tone = BannerTone.WARNING,
                            action = { TextButton(onClick = refresh) { Text("Actualizar") } },
                        )
                    }
                }
                if (sorted.isNotEmpty()) {
                    item(key = "fuel") { FuelSelector(fuel, vm::setFuel, Modifier.padding(vertical = 8.dp)) }
                }

                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        GettingStarted(
                            onSearch = onAddFavorites,
                            onAddWidget = if (!hasWidget && canPinWidget(context)) {
                                { requestPinWidget(context) }
                            } else {
                                null
                            },
                        )
                    }
                    return@LazyColumn
                }

                drops.maxByOrNull { it.second }?.let { (fav, amountDown) ->
                    item(key = "drops") { DropsBanner(drops.size, amountDown, fav.name) }
                }

                best?.let { b ->
                    item(key = "hero") {
                        BestPriceHero(
                            fuel = fuel,
                            name = b.name,
                            place = Labels.place(b.address, b.district),
                            price = b.price(fuel)!!,
                            maxPrice = maxPrice.takeIf { sorted.size > 1 },
                            caption = if (sorted.size > 1) "Mejor precio en tus grifos" else "Precio hoy",
                            onDirections = { openDirections(context, b.lat, b.lon, b.name, b.address) },
                            onClick = { detailId = b.id },
                            delta = b.delta(fuel),
                        )
                    }
                }
                if (!hasWidget) {
                    item(key = "widget") {
                        Banner(
                            "Mira estos precios sin abrir la app con el widget “Mis grifos”.",
                            icon = Icons.Outlined.Widgets,
                            action = if (canPinWidget(context)) {
                                { TextButton(onClick = { requestPinWidget(context) }) { Text("Agregar") } }
                            } else {
                                null
                            },
                        )
                    }
                }
                item(key = "label") { SectionLabel("Tus grifos · ${fuel.label}") }
                items(sorted, key = { it.id }) { fav ->
                    StationCard(
                        name = fav.name,
                        place = Labels.place(fav.address, fav.district),
                        price = fav.price(fuel),
                        delta = fav.delta(fuel),
                        distanceKm = null,
                        updated = fav.updated,
                        isFavorite = true,
                        onToggleFavorite = { remove(fav) },
                        onDirections = { openDirections(context, fav.lat, fav.lon, fav.name, fav.address) },
                        best = sorted.size > 1 && fav.id == best?.id,
                        modifier = Modifier.animateItem(),
                        onClick = { detailId = fav.id },
                    )
                }
            }
        }
    }

    favorites.firstOrNull { it.id == detailId }?.let { fav ->
        StationSheet(
            info = StationInfo(
                id = fav.id,
                name = fav.name,
                address = fav.address,
                district = fav.district,
                lat = fav.lat,
                lon = fav.lon,
                prices = fav.prices(),
                deltas = DataJson.decodePrices(fav.deltasJson),
                updated = fav.updated,
            ),
            selectedFuel = fuel,
            isFavorite = true,
            onToggleFavorite = { detailId = null; remove(fav) },
            onDismiss = { detailId = null },
        )
    }
}

private fun widgetCount(context: Context): Int = runCatching {
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FavoritesWidgetReceiver::class.java)).size
}.getOrDefault(1)

private fun canPinWidget(context: Context): Boolean =
    runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

/** Abre el diálogo del launcher para colocar el widget directamente (Android 8+). */
private fun requestPinWidget(context: Context) {
    runCatching {
        AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, FavoritesWidgetReceiver::class.java), null, null)
    }
}
