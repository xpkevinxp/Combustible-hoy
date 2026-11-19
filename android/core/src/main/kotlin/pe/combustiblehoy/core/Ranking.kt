package pe.combustiblehoy.core

data class RankedStation(val station: Station, val price: Double, val distanceKm: Double?)

enum class SortMode { PRICE, DISTANCE }

object Ranking {
    /**
     * Establecimientos que venden [fuel], opcionalmente filtrados por radio alrededor de [origin].
     * Sin coordenadas del usuario (o de la estación) se ordena solo por precio.
     */
    fun nearby(
        stations: List<Station>,
        fuel: Fuel,
        origin: LatLon?,
        radiusKm: Double = 5.0,
        sort: SortMode = SortMode.PRICE,
        limit: Int = 50,
    ): List<RankedStation> {
        val ranked = stations.mapNotNull { st ->
            val price = st.price(fuel) ?: return@mapNotNull null
            val dist = if (origin != null) st.location?.let { Geo.distanceKm(origin, it) } else null
            RankedStation(st, price, dist)
        }
        val withinRadius = if (origin != null && ranked.any { it.distanceKm != null }) {
            ranked.filter { it.distanceKm != null && it.distanceKm <= radiusKm }
        } else ranked
        val comparator = when (sort) {
            SortMode.PRICE -> compareBy<RankedStation> { it.price }.thenBy { it.distanceKm ?: Double.MAX_VALUE }
            SortMode.DISTANCE -> compareBy<RankedStation> { it.distanceKm ?: Double.MAX_VALUE }.thenBy { it.price }
        }
        return withinRadius.sortedWith(comparator).take(limit)
    }

    fun search(stations: List<Station>, query: String): List<Station> {
        val q = normalize(query)
        if (q.isBlank()) return stations
        return stations.filter { st ->
            listOf(st.name, st.company, st.address, st.district, st.province).any { normalize(it).contains(q) }
        }
    }

    private fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
}
