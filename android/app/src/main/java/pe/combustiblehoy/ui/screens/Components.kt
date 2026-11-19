package pe.combustiblehoy.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Department
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Geo
import pe.combustiblehoy.core.Labels
import pe.combustiblehoy.ui.theme.LocalPriceColors
import pe.combustiblehoy.ui.theme.PriceStyle
import java.util.Locale
import kotlin.math.abs

// ---------------------------------------------------------------------------------------------
// Formato
// ---------------------------------------------------------------------------------------------

fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)

fun distanceLabel(km: Double, approximate: Boolean): String {
    val text = if (km < 1) "${(km * 1000).toInt() / 10 * 10} m" else String.format(Locale.US, "%.1f km", km)
    return if (approximate) "≈ $text" else text
}

/** Nombre legible del departamento a partir del slug, usando meta o el slug mismo. */
fun departmentName(slug: String, departments: List<Department>): String =
    departments.firstOrNull { it.slug == slug }?.name?.let(Labels::pretty)
        ?: slug.split('-').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

// ---------------------------------------------------------------------------------------------
// Barra superior
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** "Osinergmin · 18/11 13:36" para el subtítulo de la barra superior. */
fun dataSubtitle(date: String): String? = date.takeIf { it.isNotBlank() }?.let { "Osinergmin · ${Dates.pretty(it)}" }

// ---------------------------------------------------------------------------------------------
// Filtros
// ---------------------------------------------------------------------------------------------

/** Chips de combustible en una fila desplazable (se ve el borde del siguiente chip: invita a deslizar). */
@Composable
fun FuelSelector(
    selected: Fuel,
    onSelect: (Fuel) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    // Arranca mostrando el seleccionado aunque esté al final de la lista.
    val state = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (Fuel.entries.indexOf(selected) - 1).coerceAtLeast(0),
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Fuel.entries, key = { it.code }) { f ->
            FuelChip(f, f == selected) { onSelect(f) }
        }
    }
}

/** Variante que envuelve en varias líneas (onboarding, ajustes): todo visible sin desplazar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuelGrid(selected: Fuel, onSelect: (Fuel) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Fuel.entries.forEach { f -> FuelChip(f, f == selected) { onSelect(f) } }
    }
}

@Composable
private fun FuelChip(fuel: Fuel, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(fuel.label, maxLines = 1) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

/** Chip con menú desplegable, para filtros de un solo valor (departamento, radio, orden). */
@Composable
fun <T> DropdownChip(
    label: String,
    icon: ImageVector?,
    options: List<T>,
    optionLabel: (T) -> String,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = if (icon != null) {
                { Icon(icon, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = "Seleccionado") }
                    } else {
                        null
                    },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
fun DepartmentPicker(
    selected: String,
    departments: List<Department>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = departments.map { it.slug }.ifEmpty { Geo.departmentCenters.keys.sorted() }
    DropdownChip(
        label = departmentName(selected, departments),
        icon = Icons.Filled.Place,
        options = options,
        optionLabel = { departmentName(it, departments) },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// Precio
// ---------------------------------------------------------------------------------------------

/** "S/ 19.89" con el símbolo más pequeño: el número es lo que se compara de un vistazo. */
@Composable
fun PriceText(
    price: Double?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (price == null) {
        Text("—", modifier, style = style.merge(PriceStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = style.fontSize * 0.55f)) { append("S/ ") }
            append(amount(price))
        },
        modifier.semantics { contentDescription = "${amount(price)} soles" },
        style = style.merge(PriceStyle),
        color = color,
        maxLines = 1,
    )
}

/** Variación desde el registro anterior. Flecha + color + texto: no depende solo del color. */
@Composable
fun DeltaPill(delta: Double?, modifier: Modifier = Modifier) {
    if (delta == null || abs(delta) < 0.005) return
    val down = delta < 0
    val c = LocalPriceColors.current
    Surface(
        modifier = modifier.semantics {
            contentDescription = (if (down) "Bajó " else "Subió ") + "${amount(abs(delta))} soles"
        },
        shape = CircleShape,
        color = if (down) c.downContainer else c.upContainer,
        contentColor = if (down) c.onDownContainer else c.onUpContainer,
    ) {
        Text(
            (if (down) "▼ " else "▲ ") + amount(abs(delta)),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium.merge(PriceStyle),
        )
    }
}

@Composable
private fun BestBadge() {
    val c = LocalPriceColors.current
    Surface(shape = CircleShape, color = c.downContainer, contentColor = c.onDownContainer) {
        Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Más barato", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MetaLabel(icon: ImageVector, text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(16.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = null), color = color, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Tarjeta de grifo
// ---------------------------------------------------------------------------------------------

/**
 * Nombre y dirección ocupan todo el ancho menos el precio (hasta 2 líneas cada uno);
 * las acciones van en una fila propia para no robarle espacio al texto en pantallas de 360 dp.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationCard(
    name: String,
    place: String,
    price: Double?,
    delta: Double?,
    distanceKm: Double?,
    updated: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDirections: (() -> Unit)?,
    modifier: Modifier = Modifier,
    best: Boolean = false,
    approximateDistance: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = if (best) colors.surfaceContainerLowest else colors.surfaceContainerLow),
        border = if (best) BorderStroke(1.5.dp, LocalPriceColors.current.down) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (best) 2.dp else 0.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    if (best) {
                        BestBadge()
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        Labels.pretty(name),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (place.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            place,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(Modifier.padding(end = 12.dp), horizontalAlignment = Alignment.End) {
                    PriceText(price, color = if (best) LocalPriceColors.current.down else colors.onSurface)
                    Spacer(Modifier.height(4.dp))
                    DeltaPill(delta)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    distanceKm?.let { MetaLabel(Icons.Outlined.NearMe, distanceLabel(it, approximateDistance)) }
                    val age = Dates.age(updated)
                    if (age.isNotBlank()) {
                        val stale = (Dates.daysAgo(updated) ?: 0L) > 7L
                        MetaLabel(
                            if (stale) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                            "Precio de $age",
                            if (stale) colors.error else colors.onSurfaceVariant,
                        )
                    }
                }
                if (onDirections != null) {
                    IconButton(onClick = onDirections) {
                        Icon(Icons.Filled.Directions, contentDescription = "Cómo llegar a ${Labels.pretty(name)}", tint = colors.primary)
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (isFavorite) colors.tertiary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Resumen "mejor precio"
// ---------------------------------------------------------------------------------------------

/**
 * Tarjeta destacada con la respuesta a "¿dónde cargo hoy?": el precio más bajo y cuánto se ahorra
 * frente al más caro. Es lo primero que se lee en la pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestPriceHero(
    fuel: Fuel,
    name: String,
    place: String,
    price: Double,
    maxPrice: Double?,
    caption: String,
    modifier: Modifier = Modifier,
    onDirections: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                caption.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                PriceText(price, style = MaterialTheme.typography.displaySmall, color = colors.onPrimaryContainer)
                Spacer(Modifier.width(6.dp))
                Text(
                    "/${fuel.unit}",
                    Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(Labels.pretty(name), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (place.isNotBlank()) {
                Text(
                    place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val saving = maxPrice?.minus(price)?.takeIf { it >= 0.01 }
            if (saving != null || onDirections != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (saving != null) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "S/ ${amount(saving)} menos por ${fuel.unit} que el más caro",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (onDirections != null) {
                        Surface(
                            onClick = onDirections,
                            shape = CircleShape,
                            color = colors.primary,
                            contentColor = colors.onPrimary,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Directions, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ir", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Avisos y estados vacíos
// ---------------------------------------------------------------------------------------------

enum class BannerTone { INFO, WARNING }

@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.INFO,
    icon: ImageVector = if (tone == BannerTone.WARNING) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        BannerTone.INFO -> colors.secondaryContainer to colors.onSecondaryContainer
        BannerTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = bg,
        contentColor = fg,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Spacer(Modifier.width(4.dp))
                action()
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

fun openDirections(context: Context, lat: Double?, lon: Double?, label: String, address: String) {
    val uri = if (lat != null && lon != null) {
        Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    } else {
        Uri.parse("geo:0,0?q=${Uri.encode("$label $address")}")
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
