package pe.combustiblehoy.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Labels

/** Snackbar compartido por todas las pestañas (vive en el Scaffold raíz, sobre la barra de navegación). */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Lo que muestra el detalle; sirve tanto para un grifo cercano como para un favorito guardado. */
data class StationInfo(
    val id: String,
    val name: String,
    val address: String,
    val district: String,
    val lat: Double?,
    val lon: Double?,
    val prices: Map<String, Double>,
    val deltas: Map<String, Double>,
    val updated: String,
    val distanceKm: Double? = null,
    val approximateDistance: Boolean = true,
)

/**
 * Detalle del grifo: todos los combustibles que vende (el seleccionado resaltado) y las acciones
 * principales. "Cómo llegar" es la acción primaria: es lo que se hace después de elegir.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationSheet(
    info: StationInfo,
    selectedFuel: Fuel,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fuels = Fuel.entries.filter { info.prices[it.code] != null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
        ) {
            Text(Labels.pretty(info.name), style = MaterialTheme.typography.headlineSmall)
            val place = Labels.place(info.address, info.district)
            if (place.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(place, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                info.distanceKm?.let { SheetMeta(Icons.Outlined.NearMe, distanceLabel(it, info.approximateDistance)) }
                val age = Dates.age(info.updated)
                if (age.isNotBlank()) {
                    val stale = (Dates.daysAgo(info.updated) ?: 0L) > 7L
                    SheetMeta(
                        if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                        "Precio declarado $age",
                        stale,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Precios", style = MaterialTheme.typography.titleSmall, color = colors.primary)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.clip(MaterialTheme.shapes.large).background(colors.surfaceContainerLowest)) {
                fuels.forEachIndexed { i, f ->
                    FuelPriceRow(f, info.prices[f.code], info.deltas[f.code], highlighted = f == selectedFuel)
                    if (i < fuels.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = colors.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openDirections(context, info.lat, info.lon, info.name, info.address) },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cómo llegar", style = MaterialTheme.typography.titleSmall)
                }
                OutlinedButton(onClick = onToggleFavorite, modifier = Modifier.height(52.dp)) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = if (isFavorite) colors.tertiary else colors.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFavorite) "Guardado" else "Guardar")
                }
                FilledTonalIconButton(
                    onClick = { shareStation(context, info, selectedFuel) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Compartir precio")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Precio declarado ante Osinergmin. Verifícalo en el surtidor.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FuelPriceRow(fuel: Fuel, price: Double?, delta: Double?, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) colors.primaryContainer else colors.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                fuel.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
            )
            if (highlighted) {
                Text("Tu combustible", style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
            }
            PriceChange(price, delta, Modifier.padding(top = 4.dp), horizontal = true)
        }
        Spacer(Modifier.width(8.dp))
        PriceText(
            price,
            style = MaterialTheme.typography.titleLarge,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurface,
        )
        Text(
            " /${fuel.unit}",
            style = MaterialTheme.typography.labelMedium,
            color = (if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant).copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SheetMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, warning: Boolean = false) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private fun shareStation(context: Context, info: StationInfo, fuel: Fuel) {
    val price = info.prices[fuel.code]
    val text = buildString {
        append("⛽ ").append(Labels.pretty(info.name)).append('\n')
        if (price != null) append(fuel.label).append(": S/ ").append(amount(price)).append(" /").append(fuel.unit).append('\n')
        Labels.place(info.address, info.district).takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
        if (info.lat != null && info.lon != null) append("https://maps.google.com/?q=${info.lat},${info.lon}\n")
        append("Precio Osinergmin vía Combustible Hoy")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Compartir precio").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
