package pe.combustiblehoy.widget

import android.content.Context
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import pe.combustiblehoy.R
import pe.combustiblehoy.app
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.PriceAlerts
import pe.combustiblehoy.data.FavoriteEntity
import pe.combustiblehoy.data.delta
import pe.combustiblehoy.data.price
import pe.combustiblehoy.ui.MainActivity
import pe.combustiblehoy.work.SyncWorker
import kotlin.math.abs

private data class WidgetRow(val name: String, val price: Double?, val delta: Double?)

private data class WidgetState(val fuel: Fuel, val rows: List<WidgetRow>, val date: String)

class FavoritesWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val TALL = DpSize(250.dp, 220.dp)

        suspend fun refreshAll(context: Context) = FavoritesWidget().updateAll(context)

        private val UP = ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF9A9A))
        private val DOWN = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFFA5D6A7))
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.app.settings.current()
        val favorites = context.app.repository.favoritesSnapshot()
        val state = WidgetState(
            fuel = settings.fuel,
            rows = favorites.map { it.toRow(settings.fuel) }
                .sortedWith(compareBy<WidgetRow> { it.price == null }.thenBy { it.price ?: 0.0 }),
            date = settings.lastDataDate,
        )
        provideContent { GlanceTheme { Content(state) } }
    }

    private fun FavoriteEntity.toRow(fuel: Fuel) = WidgetRow(name, price(fuel), delta(fuel))

    @Composable
    private fun Content(state: WidgetState) {
        val size = LocalSize.current
        val maxRows = when {
            size.height >= TALL.height -> 6
            size.width >= WIDE.width -> 3
            else -> 2
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.fuel.label,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Actualizar",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Spacer(GlanceModifier.height(6.dp))

            if (state.rows.isEmpty()) {
                Text(
                    text = "Agrega grifos favoritos desde la app ⭐",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            } else {
                state.rows.take(maxRows).forEach { row -> PriceRow(row, compact = size.width < WIDE.width) }
            }

            if (state.date.isNotBlank()) {
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = "Osinergmin · ${Dates.pretty(state.date)}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun PriceRow(row: WidgetRow, compact: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.name,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (compact) 11.sp else 13.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(6.dp))
            val d = row.delta
            if (!compact && d != null && abs(d) >= 0.005) {
                Text(
                    text = (if (d < 0) "▼" else "▲") + String.format(java.util.Locale.US, "%.2f", abs(d)),
                    style = TextStyle(color = if (d < 0) DOWN else UP, fontSize = 11.sp),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                text = row.price?.let { PriceAlerts.money(it) } ?: "—",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = if (compact) 12.sp else 15.sp),
            )
        }
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
