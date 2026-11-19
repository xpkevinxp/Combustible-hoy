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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Labels
import pe.combustiblehoy.data.FavoriteEntity
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.data.delta
import pe.combustiblehoy.data.price
import pe.combustiblehoy.ui.MainViewModel
import pe.combustiblehoy.widget.FavoritesWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: MainViewModel, settings: UserSettings, onAddFavorites: () -> Unit) {
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
                item(key = "fuel") { FuelSelector(fuel, vm::setFuel, Modifier.padding(vertical = 8.dp)) }

                if (sorted.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.StarOutline,
                            title = "Guarda tus grifos",
                            body = "Busca en Cercanos y toca la estrella de los grifos donde cargas. " +
                                "Verás aquí cuál está más barato hoy, y también en el widget de tu pantalla de inicio.",
                            action = { Button(onClick = onAddFavorites) { Text("Buscar grifos cercanos") } },
                        )
                    }
                    return@LazyColumn
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
                        onToggleFavorite = { vm.removeFavorite(fav.id) },
                        onDirections = { openDirections(context, fav.lat, fav.lon, fav.name, fav.address) },
                        best = sorted.size > 1 && fav.id == best?.id,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
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
