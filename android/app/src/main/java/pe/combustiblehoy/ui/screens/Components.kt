package pe.combustiblehoy.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.combustiblehoy.core.Department
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Geo
import pe.combustiblehoy.core.PriceAlerts
import pe.combustiblehoy.ui.theme.PriceDown
import pe.combustiblehoy.ui.theme.PriceUp
import java.util.Locale
import kotlin.math.abs

@Composable
fun FuelChips(selected: Fuel, onSelect: (Fuel) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Fuel.entries.forEach { f ->
            FilterChip(selected = f == selected, onClick = { onSelect(f) }, label = { Text(f.label) })
        }
    }
}

/** Nombre legible del departamento a partir del slug, usando meta o el slug mismo. */
fun departmentName(slug: String, departments: List<Department>): String =
    departments.firstOrNull { it.slug == slug }?.name?.lowercase()?.replaceFirstChar { it.titlecase() }
        ?: slug.replace('-', ' ').replaceFirstChar { it.titlecase() }

@Composable
fun DepartmentPicker(selected: String, departments: List<Department>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options = departments.map { it.slug }.ifEmpty { Geo.departmentCenters.keys.sorted() }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(departmentName(selected, departments))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { slug ->
                DropdownMenuItem(
                    text = { Text(departmentName(slug, departments)) },
                    onClick = { open = false; onSelect(slug) },
                )
            }
        }
    }
}

@Composable
fun DeltaText(delta: Double?) {
    if (delta == null || abs(delta) < 0.005) return
    val down = delta < 0
    Text(
        text = (if (down) "▼ " else "▲ ") + String.format(Locale.US, "%.2f", abs(delta)),
        color = if (down) PriceDown else PriceUp,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun StationCard(
    name: String,
    subtitle: String,
    price: Double?,
    delta: Double?,
    distanceKm: Double?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDirections: (() -> Unit)?,
    highlight: Boolean = false,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(subtitle.ifBlank { null }, distanceKm?.let { String.format(Locale.US, "%.1f km", it) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price?.let { PriceAlerts.money(it) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlight) PriceDown else MaterialTheme.colorScheme.onSurface,
                )
                DeltaText(delta)
            }
            if (onDirections != null) {
                IconButton(onClick = onDirections) { Icon(Icons.Filled.Directions, contentDescription = "Cómo llegar") }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun openDirections(context: Context, lat: Double?, lon: Double?, label: String, address: String) {
    val uri = if (lat != null && lon != null) {
        Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode("$label $address")}")
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.padding(8.dp))
            action()
        }
    }
}
