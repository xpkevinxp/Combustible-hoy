package pe.combustiblehoy.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import pe.combustiblehoy.R
import pe.combustiblehoy.app
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Labels
import pe.combustiblehoy.data.FavoriteEntity
import pe.combustiblehoy.data.delta
import pe.combustiblehoy.data.price
import pe.combustiblehoy.ui.MainActivity
import pe.combustiblehoy.ui.theme.BrandDark
import pe.combustiblehoy.ui.theme.BrandLight
import pe.combustiblehoy.work.SyncWorker
import java.util.Locale
import kotlin.math.abs

private data class WidgetRow(val id: String, val name: String, val district: String, val price: Double?, val delta: Double?)

private data class WidgetState(val fuel: Fuel, val rows: List<WidgetRow>, val date: String) {
    val best: WidgetRow? get() = rows.firstOrNull()?.takeIf { it.price != null }
}

/** Colores de la app también en el widget (claro/oscuro según el sistema). */
private val WidgetColors = ColorProviders(light = BrandLight, dark = BrandDark)

private val UP = DayNightColor(day = Color(0xFFB3261E), night = Color(0xFFFFB4AB))
private val DOWN = DayNightColor(day = Color(0xFF1B6C2F), night = Color(0xFF9AD7A0))
private val DOWN_BG = DayNightColor(day = Color(0xFFC6F0C8), night = Color(0xFF0B5321))
private val ON_DOWN_BG = DayNightColor(day = Color(0xFF002109), night = Color(0xFFC6F0C8))
private val UP_BG = DayNightColor(day = Color(0xFFFFDAD6), night = Color(0xFF93000A))
private val ON_UP_BG = DayNightColor(day = Color(0xFF410002), night = Color(0xFFFFDAD6))

/**
 * Widget "Mis grifos". Se adapta al tamaño:
 *  - franja (1 fila de alto): el más barato en una línea;
 *  - 2×2: tarjeta con el precio más bajo en grande;
 *  - 3×2 / 4×2: lista compacta con el más barato resaltado;
 *  - grande: lista desplazable con distrito y variación.
 */
class FavoritesWidget : GlanceAppWidget() {

    companion object {
        private val STRIP = DpSize(110.dp, 56.dp)
        private val WIDE_STRIP = DpSize(220.dp, 56.dp)
        private val SQUARE = DpSize(110.dp, 110.dp)
        private val NARROW_TALL = DpSize(110.dp, 200.dp)
        private val WIDE = DpSize(180.dp, 110.dp)
        private val WIDE_TALL = DpSize(180.dp, 180.dp)
        private val LARGE = DpSize(260.dp, 260.dp)

        suspend fun refreshAll(context: Context) = FavoritesWidget().updateAll(context)
    }

    override val sizeMode = SizeMode.Responsive(setOf(STRIP, WIDE_STRIP, SQUARE, NARROW_TALL, WIDE, WIDE_TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.app.settings.current()
        val favorites = context.app.repository.favoritesSnapshot()
        val state = WidgetState(
            fuel = settings.fuel,
            rows = favorites.map { it.toRow(settings.fuel) }
                .sortedWith(compareBy<WidgetRow> { it.price == null }.thenBy { it.price ?: 0.0 }),
            date = settings.lastDataDate,
        )
        provideContent { GlanceTheme(colors = WidgetColors) { Content(state) } }
    }

    private fun FavoriteEntity.toRow(fuel: Fuel) =
        WidgetRow(id, Labels.pretty(name), Labels.pretty(district), price(fuel), delta(fuel))

    @Composable
    private fun Content(state: WidgetState) {
        val size = LocalSize.current
        val strip = size.height < 100.dp
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetBackground()
                .padding(horizontal = if (strip) 12.dp else 16.dp, vertical = if (strip) 8.dp else 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            when {
                state.rows.isEmpty() -> Empty(compact = strip || size.width < 170.dp)
                strip -> Strip(state)
                size.width < 170.dp -> Hero(state, showOthers = size.height >= 190.dp)
                size.height < 170.dp -> ListLayout(state, scrollable = false)
                else -> ListLayout(state, scrollable = true)
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Layouts
    // -----------------------------------------------------------------------------------------

    /** 1 fila de alto: combustible · grifo más barato · precio con flecha de cambio. */
    @Composable
    private fun Strip(state: WidgetState) {
        val row = state.best ?: state.rows.first()
        Row(GlanceModifier.fillMaxSize().clickable(openStation(row.id)), verticalAlignment = Alignment.CenterVertically) {
            FuelBadge(state.fuel, short = true)
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(row.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text(
                    if (state.rows.size > 1) "Más barato hoy" else row.district,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Price(row.price, big = 20.sp, color = GlanceTheme.colors.primary)
                ChangeText(row.delta)
            }
        }
    }

    /** 2×2: una sola cifra protagonista, el cambio debajo y el nombre del grifo. */
    @Composable
    private fun Hero(state: WidgetState, showOthers: Boolean) {
        val best = state.best ?: state.rows.first()
        Column(GlanceModifier.fillMaxSize().clickable(openStation(best.id))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FuelBadge(state.fuel, short = true)
                Spacer(GlanceModifier.defaultWeight())
                RefreshButton()
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                if (state.rows.size > 1) "Más barato hoy" else "Precio hoy",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Price(best.price, big = 28.sp, color = GlanceTheme.colors.primary)
            ChangeChip(best.price, best.delta, withBefore = false)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                best.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = if (showOthers) 1 else 2,
            )
            if (showOthers) {
                // Columna propia: Glance admite como máximo 10 hijos por contenedor.
                Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                    state.rows.drop(1).take(3).forEach { CompactRow(it, highlight = false, small = true) }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Footer(state, showSource = false)
        }
    }

    @Composable
    private fun ListLayout(state: WidgetState, scrollable: Boolean) {
        val bestId = state.best?.id?.takeIf { state.rows.size > 1 }
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FuelBadge(state.fuel, short = false)
                Spacer(GlanceModifier.defaultWeight())
                RefreshButton()
            }
            Spacer(GlanceModifier.height(8.dp))
            if (scrollable) {
                val best = state.best
                if (best != null) {
                    BestCard(best, state)
                    Spacer(GlanceModifier.height(8.dp))
                }
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(state.rows.filter { it.id != best?.id }, itemId = { it.id.hashCode().toLong() }) { row ->
                        Column {
                            FullRow(row)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                Footer(state, showSource = true)
            } else {
                // 2 filas caben siempre en 110 dp de alto; si sobra espacio, la fecha va abajo.
                state.rows.take(2).forEach { CompactRow(it, highlight = it.id == bestId, small = false) }
                Spacer(GlanceModifier.defaultWeight())
                Footer(state, showSource = false, more = (state.rows.size - 2).coerceAtLeast(0))
            }
        }
    }

    /** Tarjeta protagonista del widget grande: precio, cambio y cuánto se ahorra frente al más caro. */
    @Composable
    private fun BestCard(best: WidgetRow, state: WidgetState) {
        val fg = GlanceTheme.colors.onPrimaryContainer
        val maxPrice = state.rows.mapNotNull { it.price }.maxOrNull()
        val saving = if (best.price != null && maxPrice != null) maxPrice - best.price else 0.0
        Column(
            GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(openStation(best.id)),
        ) {
            Text(
                if (state.rows.size > 1) "MÁS BARATO HOY" else "PRECIO HOY",
                style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Price(best.price, big = 26.sp, color = fg)
                Spacer(GlanceModifier.defaultWeight())
                ChangeChip(best.price, best.delta, withBefore = true)
            }
            Text(best.name, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            if (saving >= 0.01) {
                Text(
                    "Ahorras S/ ${fmt(saving)} por ${state.fuel.unit} vs el más caro",
                    style = TextStyle(color = fg, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun Empty(compact: Boolean) {
        Column(
            GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                Box(
                    GlanceModifier.size(48.dp).cornerRadius(24.dp).background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_fuel),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        modifier = GlanceModifier.size(24.dp),
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            Text(
                "Elige tus grifos",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                if (compact) "Toca para agregar" else "Toca para buscar y marcar tus favoritos ☆",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, textAlign = androidx.glance.text.TextAlign.Center),
                maxLines = 2,
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Piezas
    // -----------------------------------------------------------------------------------------

    /** Fila de 1 línea: nombre + precio (+ flecha de cambio). El más barato va sobre fondo de color. */
    @Composable
    private fun CompactRow(row: WidgetRow, highlight: Boolean, small: Boolean) {
        Row(
            GlanceModifier.fillMaxWidth()
                .then(if (highlight) GlanceModifier.cornerRadius(12.dp).background(GlanceTheme.colors.primaryContainer) else GlanceModifier)
                .padding(horizontal = if (highlight) 8.dp else 0.dp, vertical = if (small) 2.dp else 4.dp)
                .clickable(openStation(row.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (highlight) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
            Text(
                row.name,
                style = TextStyle(color = fg, fontSize = if (small) 11.sp else 13.sp, fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            if (!small) {
                ChangeText(row.delta)
                Spacer(GlanceModifier.width(4.dp))
            }
            Price(row.price, big = if (small) 12.sp else 15.sp, color = fg, symbol = !small)
        }
    }

    /** Fila de 2 líneas: nombre/distrito y precio anterior a la izquierda; precio y cambio a la derecha. */
    @Composable
    private fun FullRow(row: WidgetRow) {
        Row(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(12.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(openStation(row.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(row.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                val prev = before(row.price, row.delta)
                val sub = listOfNotNull(row.district.ifBlank { null }, prev?.let { "antes S/ ${fmt(it)}" }).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Price(row.price, big = 16.sp, color = GlanceTheme.colors.onSurface)
                ChangeText(row.delta)
            }
        }
    }

    @Composable
    private fun Price(price: Double?, big: androidx.compose.ui.unit.TextUnit, color: ColorProvider, symbol: Boolean = true) {
        if (price == null) {
            Text("—", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = big, fontWeight = FontWeight.Bold))
            return
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (symbol) {
                Text("S/", style = TextStyle(color = color, fontSize = (big.value * 0.55f).sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(fmt(price), style = TextStyle(color = color, fontSize = big, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    /** Texto corto de cambio, coloreado: "▼0.30". */
    @Composable
    private fun ChangeText(delta: Double?) {
        if (delta == null || abs(delta) < 0.005) return
        val down = delta < 0
        Text(
            (if (down) "▼" else "▲") + fmt(abs(delta)),
            style = TextStyle(color = if (down) DOWN else UP, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }

    /** Pastilla de cambio sobre fondo suave: "▼ 0.30 · antes 16.19". */
    @Composable
    private fun ChangeChip(price: Double?, delta: Double?, withBefore: Boolean) {
        if (delta == null || abs(delta) < 0.005) return
        val down = delta < 0
        val text = buildString {
            append(if (down) "▼ " else "▲ ").append(fmt(abs(delta)))
            if (withBefore) before(price, delta)?.let { append(" · antes ").append(fmt(it)) }
        }
        Box(
            GlanceModifier.cornerRadius(12.dp).background(if (down) DOWN_BG else UP_BG).padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(text, style = TextStyle(color = if (down) ON_DOWN_BG else ON_UP_BG, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }

    @Composable
    private fun FuelBadge(fuel: Fuel, short: Boolean) {
        Row(
            GlanceModifier.cornerRadius(12.dp).background(GlanceTheme.colors.primary).padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_fuel),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(14.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                if (short) fuel.code else fuel.label,
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }

    /** Botón circular de 32 dp con fondo: objetivo táctil claro. */
    @Composable
    private fun RefreshButton() {
        Box(
            GlanceModifier.size(32.dp).cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<RefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Actualizar precios",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(18.dp),
            )
        }
    }

    @Composable
    private fun Footer(state: WidgetState, showSource: Boolean, more: Int = 0) {
        if (state.date.isBlank() && more == 0) return
        val stale = Dates.isStale(state.date)
        val parts = listOfNotNull(
            if (more > 0) "+$more más" else null,
            state.date.takeIf { it.isNotBlank() }?.let {
                (if (stale) "⚠ " else "Actualizado ") + Dates.pretty(it) + (if (showSource) " · Osinergmin" else "")
            },
        )
        Text(
            parts.joinToString(" · "),
            style = TextStyle(color = if (stale) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1,
        )
    }

    /** Esquinas del sistema en Android 12+; antes, un drawable redondeado con los colores de marca. */
    @Composable
    private fun GlanceModifier.widgetBackground(): GlanceModifier =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            background(GlanceTheme.colors.background).cornerRadius(android.R.dimen.system_app_widget_background_radius)
        } else {
            background(ImageProvider(R.drawable.widget_background))
        }
}

private val StationKey = ActionParameters.Key<String>(MainActivity.EXTRA_STATION_ID)

/** Abre la app directo en el detalle del grifo tocado. */
private fun openStation(id: String) = actionStartActivity<MainActivity>(actionParametersOf(StationKey to id))

private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

private fun before(price: Double?, delta: Double?): Double? =
    if (price == null || delta == null || abs(delta) < 0.005) null else price - delta

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.runNow(context, force = false)
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
