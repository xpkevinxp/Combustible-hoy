package pe.combustiblehoy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import pe.combustiblehoy.BuildConfig
import pe.combustiblehoy.core.DataJson
import pe.combustiblehoy.core.Meta
import pe.combustiblehoy.core.PriceAlerts
import pe.combustiblehoy.core.PriceChange
import pe.combustiblehoy.core.Station
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SyncResult(val dataDate: String, val changes: List<PriceChange>, val updated: Boolean)

/**
 * Fuente única de datos: descarga los JSON publicados por el pipeline (GitHub Pages),
 * los guarda en disco para uso offline y mantiene actualizados los favoritos.
 */
class PriceRepository(
    context: Context,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val baseUrl: String = BuildConfig.DATA_BASE_URL,
) {
    private val cacheDir = File(context.filesDir, "data").apply { mkdirs() }
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val favorites: Flow<List<FavoriteEntity>> = db.favorites().observeAll()
    val favoriteIds: Flow<List<String>> = db.favorites().observeIds()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} en $path")
            resp.body?.string() ?: throw IOException("Respuesta vacía en $path")
        }
    }

    private fun cached(name: String): File = File(cacheDir, name)

    private suspend fun getAndCache(path: String, name: String): String {
        val text = get(path)
        withContext(Dispatchers.IO) { cached(name).writeText(text) }
        return text
    }

    private suspend fun readCached(name: String): String? = withContext(Dispatchers.IO) {
        cached(name).takeIf { it.exists() }?.readText()
    }

    suspend fun meta(refresh: Boolean = false): Meta? {
        if (!refresh) readCached("meta.json")?.let { return runCatching { DataJson.meta(it) }.getOrNull() }
        return runCatching { DataJson.meta(getAndCache("latest/meta.json", "meta.json")) }
            .getOrElse { readCached("meta.json")?.let(DataJson::meta) }
    }

    /** Establecimientos de un departamento: primero caché si es del día de datos vigente, si no red. */
    suspend fun stations(department: String, refresh: Boolean = false): List<Station> {
        val name = "dept-$department.json"
        val metaDate = meta()?.date
        if (!refresh) {
            readCached(name)?.let { text ->
                val file = runCatching { DataJson.department(text) }.getOrNull()
                if (file != null && (metaDate == null || file.date == metaDate)) return file.stations
            }
        }
        return runCatching { DataJson.department(getAndCache("latest/dept/$department.json", name)).stations }
            .getOrElse { e -> readCached(name)?.let { DataJson.department(it).stations } ?: throw e }
    }

    suspend fun favoritesSnapshot(): List<FavoriteEntity> = db.favorites().all()

    suspend fun addFavorite(st: Station) = db.favorites().upsert(FavoriteEntity.from(st))
    suspend fun removeFavorite(id: String) = db.favorites().delete(id)

    /**
     * Sincronización diaria (WorkManager o manual): si hay datos nuevos actualiza favoritos
     * y devuelve los cambios de precio relevantes para notificar.
     */
    suspend fun sync(force: Boolean = false): SyncResult {
        val s = settings.current()
        val meta = meta(refresh = true) ?: throw IOException("Sin datos disponibles")
        if (!force && meta.date == s.lastDataDate) return SyncResult(meta.date, emptyList(), updated = false)

        val prices = DataJson.prices(getAndCache("latest/prices.json", "prices.json"))
        // Refresca el departamento seleccionado para que la lista "Cercanos" esté al día.
        runCatching { stations(s.department, refresh = true) }

        val favorites = db.favorites().all()
        val changes = PriceAlerts.detect(
            favorites = favorites.map { it.snapshot() },
            latest = prices.p,
            fuel = s.fuel,
            threshold = s.alertThreshold,
            onlyDrops = s.onlyDrops,
        )
        for (fav in favorites) {
            val newPrices = prices.p[fav.id] ?: continue
            val old = fav.prices()
            val stationDate = prices.u[fav.id] ?: fav.updated
            if (newPrices == old) { // sin cambios: se conserva la última variación conocida
                if (stationDate != fav.updated) db.favorites().updatePrices(fav.id, fav.pricesJson, fav.deltasJson, stationDate)
                continue
            }
            val deltas = newPrices.filter { (k, v) -> old[k] != null && kotlin.math.abs(v - old[k]!!) >= 0.005 }
                .mapValues { (k, v) -> v - old[k]!! }
            db.favorites().updatePrices(fav.id, DataJson.encodePrices(newPrices), DataJson.encodePrices(deltas), stationDate)
        }
        settings.markSynced(meta.date)
        return SyncResult(meta.date, changes, updated = true)
    }
}
