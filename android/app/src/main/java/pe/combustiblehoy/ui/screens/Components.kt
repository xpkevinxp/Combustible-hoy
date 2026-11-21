package pe.combustiblehoy.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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

/** Carga de referencia para traducir el ahorro por unidad a soles concretos. */
const val TANK = 10

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

/** Precio del registro anterior, si hubo cambio. */
fun previousPrice(price: Double?, delta: Double?): Double? =
    if (price == null || delta == null || abs(delta) < 0.005) null else price - delta

/**
 * Cambio de precio completo: pastilla ▼/▲ + precio anterior tachado.
 * Responde "¿cuánto bajó y desde cuánto?" sin abrir nada.
 */
@Composable
fun PriceChange(price: Double?, delta: Double?, modifier: Modifier = Modifier, horizontal: Boolean = false) {
    val before = previousPrice(price, delta) ?: return
    val content: @Composable () -> Unit = {
        DeltaPill(delta)
        Text(
            "antes S/ ${amount(before)}",
            style = MaterialTheme.typography.labelSmall.merge(PriceStyle).copy(
                fontWeight = FontWeight.Normal,
                textDecoration = TextDecoration.LineThrough,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
    if (horizontal) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
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
    onClick: () -> Unit = {},
    vsAverage: Double? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (best) colors.surfaceContainerLowest else colors.surfaceContainerLow),
        border = if (best) BorderStroke(1.5.dp, LocalPriceColors.current.down) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (best) 2.dp else 0.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 4.dp)) {
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
                    PriceChange(price, delta)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    distanceKm?.let { MetaLabel(Icons.Outlined.NearMe, distanceLabel(it, approximateDistance)) }
                    // Posición frente a la zona: ayuda a decidir sin comparar números de memoria.
                    vsAverage?.takeIf { abs(it) >= 0.05 }?.let { diff ->
                        val pc = LocalPriceColors.current
                        MetaLabel(
                            if (diff < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                            "S/ ${amount(abs(diff))} " + if (diff < 0) "bajo el promedio" else "sobre el promedio",
                            if (diff < 0) pc.down else colors.onSurfaceVariant,
                        )
                    }
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
                FavoriteButton(isFavorite, onToggleFavorite)
            }
        }
    }
}

/** Estrella con rebote y vibración corta: confirma la acción sin necesidad de leer nada. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    // Solo anima tras un toque del usuario, no al componer la lista.
    var pressedOnce by remember { mutableStateOf(false) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isFavorite) {
        if (pressedOnce) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }
    IconButton(
        onClick = {
            pressedOnce = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        modifier = modifier,
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Carga y resumen
// ---------------------------------------------------------------------------------------------

/** Silueta de tarjeta mientras cargan los datos: el layout no salta cuando llegan. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp).graphicsLayer { this.alpha = alpha }) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(18.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.width(76.dp).height(28.dp).clip(MaterialTheme.shapes.small).background(block))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(140.dp).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(block))
        }
    }
}

/** Tres cifras clave de la zona, escaneables de un vistazo. */
@Composable
fun StatsStrip(count: Int, cheapest: Double?, average: Double?, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Más barato", cheapest?.let { "S/ ${amount(it)}" } ?: "—", Modifier.weight(1f), highlight = true)
        StatTile("Promedio", average?.let { "S/ ${amount(it)}" } ?: "—", Modifier.weight(1f))
        StatTile("Grifos", "$count", Modifier.weight(0.8f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val c = LocalPriceColors.current
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) c.downContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (highlight) c.onDownContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.merge(PriceStyle), maxLines = 1)
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
    onClick: () -> Unit = {},
    delta: Double? = null,
) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(24.dp)) {
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
            previousPrice(price, delta)?.let { before ->
                val down = delta!! < 0
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        append(if (down) "▼ Bajó S/ ${amount(abs(delta))}" else "▲ Subió S/ ${amount(abs(delta))}")
                        append("  ·  antes ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append("S/ ${amount(before)}") }
                    },
                    style = MaterialTheme.typography.labelLarge.merge(PriceStyle),
                    color = colors.onPrimaryContainer.copy(alpha = if (down) 1f else 0.8f),
                )
            }
            Spacer(Modifier.height(12.dp))
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
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, Modifier.padding(top = 1.dp).size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    "S/ ${amount(saving)} menos por ${fuel.unit} que el más caro",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // Una cifra concreta motiva más que un céntimo por unidad.
                                Text(
                                    "≈ S/ ${amount(saving * TANK)} de ahorro en $TANK ${fuel.unit}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
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

// ---------------------------------------------------------------------------------------------
// Primeros pasos y buenas noticias
// ---------------------------------------------------------------------------------------------

/**
 * Pantalla de inicio sin favoritos: en vez de un mensaje vacío, los 3 pasos del valor de la app
 * y un único botón principal abajo, en la zona del pulgar.
 */
@Composable
fun GettingStarted(onSearch: () -> Unit, onAddWidget: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("¡Bienvenido! 👋", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ahorra en cada carga en 3 pasos. Te toma menos de un minuto.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        GuideStep(1, Icons.Outlined.NearMe, "Busca grifos cerca de ti", "Ordenados del más barato al más caro.")
        GuideStep(2, Icons.Outlined.StarOutline, "Toca ☆ en los que usas", "Los verás aquí, comparados entre sí.")
        GuideStep(3, Icons.Outlined.Widgets, "Pon el widget en tu inicio", "El precio de hoy sin abrir la app.", onAddWidget)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSearch, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Outlined.NearMe, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buscar grifos cercanos", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun GuideStep(number: Int, icon: ImageVector, title: String, body: String, action: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = colors.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("$number. $title", style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (action != null) {
            TextButton(onClick = action) { Text("Agregar") }
        }
    }
}

/** "Bajó en 2 de tus grifos": la buena noticia es lo primero que se ve al abrir la app. */
@Composable
fun DropsBanner(drops: Int, bestDrop: Double, stationName: String, modifier: Modifier = Modifier) {
    val c = LocalPriceColors.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = c.downContainer,
        contentColor = c.onDownContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (drops == 1) "¡Bajó el precio en 1 de tus grifos!" else "¡Bajó el precio en $drops de tus grifos!",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Hasta S/ ${amount(bestDrop)} menos en ${Labels.pretty(stationName)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
