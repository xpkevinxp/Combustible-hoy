package pe.combustiblehoy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import pe.combustiblehoy.core.DataJson
import pe.combustiblehoy.core.FavoriteSnapshot
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Station

/** Grifo favorito con el último precio conocido (permite widget offline y detectar cambios). */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val district: String,
    val department: String,
    val lat: Double?,
    val lon: Double?,
    val pricesJson: String,
    val deltasJson: String = "",
    val updated: String = "",
    val addedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(st: Station) = FavoriteEntity(
            id = st.id,
            name = st.name,
            address = st.address,
            district = st.district,
            department = st.department,
            lat = st.lat,
            lon = st.lon,
            pricesJson = DataJson.encodePrices(st.prices),
            deltasJson = DataJson.encodePrices(st.deltas),
            updated = st.updated,
        )
    }
}

// Extensiones (fuera de la entidad para que Room no intente mapearlas como columnas).
fun FavoriteEntity.prices(): Map<String, Double> = DataJson.decodePrices(pricesJson)
fun FavoriteEntity.price(fuel: Fuel): Double? = prices()[fuel.code]
fun FavoriteEntity.delta(fuel: Fuel): Double? = DataJson.decodePrices(deltasJson)[fuel.code]
fun FavoriteEntity.snapshot() = FavoriteSnapshot(id, name, prices())

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT id FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE favorites SET pricesJson = :prices, deltasJson = :deltas, updated = :date WHERE id = :id")
    suspend fun updatePrices(id: String, prices: String, deltas: String, date: String)
}

@Database(entities = [FavoriteEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "combustible.db").build()
    }
}
