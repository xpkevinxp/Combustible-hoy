package pe.combustiblehoy.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.data.delta
import pe.combustiblehoy.data.price
import pe.combustiblehoy.ui.MainViewModel

@Composable
fun FavoritesScreen(vm: MainViewModel, settings: UserSettings, onAddFavorites: () -> Unit) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val sorted = favorites.sortedWith(compareBy<pe.combustiblehoy.data.FavoriteEntity>({ it.price(settings.fuel) == null }, { it.price(settings.fuel) ?: 0.0 }))
    val cheapest = sorted.firstOrNull()?.price(settings.fuel)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mis grifos", style = MaterialTheme.typography.titleLarge)
                if (settings.lastDataDate.isNotBlank()) {
                    Text(
                        "Osinergmin · actualizado ${Dates.pretty(settings.lastDataDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar") }
        }
        FuelChips(settings.fuel, vm::setFuel, Modifier.padding(vertical = 8.dp))

        if (sorted.isEmpty()) {
            EmptyMessage("Aún no tienes grifos favoritos. Búscalos en \"Cercanos\" y toca la ⭐. Luego agrega el widget \"Mis grifos\" a tu pantalla de inicio.") {
                Button(onClick = onAddFavorites) { Text("Buscar grifos") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.id }) { fav ->
                    StationCard(
                        name = fav.name,
                        subtitle = listOf(fav.address, fav.district.lowercase().replaceFirstChar { it.titlecase() })
                            .filter { it.isNotBlank() }.joinToString(", "),
                        price = fav.price(settings.fuel),
                        delta = fav.delta(settings.fuel),
                        distanceKm = null,
                        updated = fav.updated,
                        isFavorite = true,
                        onToggleFavorite = { vm.removeFavorite(fav.id) },
                        onDirections = { openDirections(context, fav.lat, fav.lon, fav.name, fav.address) },
                        highlight = sorted.size > 1 && fav.price(settings.fuel) == cheapest,
                    )
                }
            }
        }
    }
}
