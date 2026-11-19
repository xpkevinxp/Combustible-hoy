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
                .padding(horizontal = if (strip) 12.dp else 14.dp, vertical = if (strip) 8.dp else 12.dp)
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

    @Composable
    private fun Strip(state: WidgetState) {
        val row = state.best ?: state.rows.first()
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            FuelBadge(state.fuel, short = true)
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(row.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                if (row.district.isNotBlank()) {
                    Text(row.district, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Price(row.price, big = 18.sp, color = GlanceTheme.colors.primary)
        }
    }

    @Composable
    private fun Hero(state: WidgetState, showOthers: Boolean) {
        val best = state.best ?: state.rows.first()
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FuelBadge(state.fuel, short = true)
                Spacer(GlanceModifier.defaultWeight())
                RefreshButton()
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (state.rows.size > 1) "Más barato" else "Precio hoy",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
            Price(best.price, big = 24.sp, color = GlanceTheme.colors.primary)
            Text(
                best.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
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
            Spacer(GlanceModifier.height(6.dp))
            if (scrollable) {
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(state.rows, itemId = { it.id.hashCode().toLong() }) { row ->
                        Column {
                            FullRow(row, highlight = row.id == bestId)
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

    @Composable
    private fun Empty(compact: Boolean) {
        Column(
            GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                Box(
                    GlanceModifier.size(40.dp).cornerRadius(20.dp).background(GlanceTheme.colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_fuel),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                        modifier = GlanceModifier.size(22.dp),
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

    /** Fila de 1 línea: nombre + precio. El más barato va sobre fondo de color. */
    @Composable
    private fun CompactRow(row: WidgetRow, highlight: Boolean, small: Boolean) {
        Row(
            GlanceModifier.fillMaxWidth()
                .then(if (highlight) GlanceModifier.cornerRadius(10.dp).background(GlanceTheme.colors.primaryContainer) else GlanceModifier)
                .padding(horizontal = if (highlight) 8.dp else 0.dp, vertical = if (small) 2.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (highlight) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
            Text(
                row.name,
                style = TextStyle(color = fg, fontSize = if (small) 11.sp else 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            Price(row.price, big = if (small) 12.sp else 15.sp, color = fg, symbol = !small)
        }
    }

    /** Fila de 2 líneas: nombre/distrito a la izquierda, precio/variación a la derecha. */
    @Composable
    private fun FullRow(row: WidgetRow, highlight: Boolean) {
        val fg = if (highlight) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
        val fgVariant = if (highlight) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
        Row(
            GlanceModifier.fillMaxWidth()
                .cornerRadius(12.dp)
                .background(if (highlight) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(row.name, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                val sub = listOfNotNull(if (highlight) "Más barato" else null, row.district.ifBlank { null }).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = TextStyle(color = fgVariant, fontSize = 11.sp), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Price(row.price, big = 16.sp, color = fg)
                Delta(row.delta)
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
            Text(
                String.format(Locale.US, "%.2f", price),
                style = TextStyle(color = color, fontSize = big, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun Delta(delta: Double?) {
        if (delta == null || abs(delta) < 0.005) return
        val down = delta < 0
        Text(
            (if (down) "▼ " else "▲ ") + String.format(Locale.US, "%.2f", abs(delta)),
            style = TextStyle(color = if (down) DOWN else UP, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
    }

    @Composable
    private fun FuelBadge(fuel: Fuel, short: Boolean) {
        Row(
            GlanceModifier.cornerRadius(12.dp).background(GlanceTheme.colors.primary).padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
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

    /** Botón circular de 30 dp con fondo (antes era un ícono suelto de 20 dp, difícil de acertar). */
    @Composable
    private fun RefreshButton() {
        Box(
            GlanceModifier.size(30.dp).cornerRadius(15.dp).background(GlanceTheme.colors.secondaryContainer)
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
                (if (stale) "⚠ " else "") + (if (showSource) "Osinergmin · " else "") + Dates.pretty(it)
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

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.runNow(context, force = false)
    }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}
