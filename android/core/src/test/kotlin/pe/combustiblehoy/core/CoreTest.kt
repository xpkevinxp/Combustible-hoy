package pe.combustiblehoy.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesPipelineOutput() {
        val meta = DataJson.meta(resource("meta.json"))
        assertEquals("2026-11-18T13:36", meta.date)
        assertEquals(listOf("callao", "cusco", "lima", "ucayali"), meta.departments.map { it.slug })
        // Todo código publicado por el pipeline existe en la app.
        assertEquals(emptySet<String>(), meta.fuels.keys - Fuel.entries.map { it.code }.toSet())

        val callao = DataJson.department(resource("callao.json"))
        val st = callao.stations.single()
        assertEquals("105356", st.id)
        assertEquals(19.89, st.price(Fuel.GHR)!!, 1e-9)
        assertNull(st.price(Fuel.GNV))
        assertEquals(LatLon(-11.8752, -77.1301), st.location)
        assertEquals(false, st.isLocationApproximate)
        assertTrue(st.activity.startsWith("ESTACI"))

        val prices = DataJson.prices(resource("prices.json"))
        assertEquals(1.74, prices.p["100155"]!!["GNV"]!!, 1e-9)
        assertEquals("2026-11-18T13:36", prices.u["105356"])
    }

    @Test
    fun distanceIsReasonable() {
        // Lima centro -> Callao ~ 8-9 km
        val d = Geo.distanceKm(Geo.departmentCenters["lima"]!!, Geo.departmentCenters["callao"]!!)
        assertTrue(d in 7.0..11.0)
        assertEquals("arequipa", Geo.nearestDepartment(LatLon(-16.4, -71.5)))
        assertEquals("lima", Geo.nearestDepartment(LatLon(-12.1, -77.03), listOf("lima", "ica")))
    }

    private fun st(id: String, price: Double?, lat: Double? = null, lon: Double? = null) =
        Station(id = id, name = "S$id", lat = lat, lon = lon, prices = price?.let { mapOf("GHR" to it) } ?: emptyMap())

    @Test
    fun rankingByPriceWithinRadius() {
        val origin = LatLon(-12.0912, -77.0251)
        val stations = listOf(
            st("near-expensive", 16.0, -12.0920, -77.0260),
            st("near-cheap", 15.0, -12.0950, -77.0300),
            st("far-cheapest", 14.0, -13.0, -76.0),
            st("no-fuel", null, -12.0912, -77.0251),
        )
        val r = Ranking.nearby(stations, Fuel.GHR, origin, radiusKm = 3.0)
        assertEquals(listOf("near-cheap", "near-expensive"), r.map { it.station.id })

        val byDist = Ranking.nearby(stations, Fuel.GHR, origin, radiusKm = 3.0, sort = SortMode.DISTANCE)
        assertEquals("near-expensive", byDist.first().station.id)

        val noGps = Ranking.nearby(stations, Fuel.GHR, origin = null)
        assertEquals("far-cheapest", noGps.first().station.id)
    }

    @Test
    fun searchIgnoresAccents() {
        val s = listOf(Station(id = "1", name = "Grifo Junín"), Station(id = "2", name = "Otro"))
        assertEquals(listOf("1"), Ranking.search(s, "junin").map { it.id })
    }

    @Test
    fun detectsPriceChangesAboveThreshold() {
        val favs = listOf(
            FavoriteSnapshot("a", "A", mapOf("GHR" to 15.50)),
            FavoriteSnapshot("b", "B", mapOf("GHR" to 15.50)),
            FavoriteSnapshot("c", "C", mapOf("GHR" to 15.50)),
            FavoriteSnapshot("d", "D", mapOf("DB5" to 16.0)),
        )
        val latest = mapOf(
            "a" to mapOf("GHR" to 15.30),
            "b" to mapOf("GHR" to 15.45),
            "c" to mapOf("GHR" to 15.70),
            "d" to mapOf("DB5" to 15.0),
        )
        val changes = PriceAlerts.detect(favs, latest, Fuel.GHR, threshold = 0.10)
        assertEquals(listOf("a", "c"), changes.map { it.stationId })
        assertTrue(changes.first().isDrop)
        assertEquals(listOf("a"), PriceAlerts.detect(favs, latest, Fuel.GHR, onlyDrops = true).map { it.stationId })
        assertEquals("Gasohol Regular bajó S/ 0.20 → S/ 15.30", PriceAlerts.describe(changes.first()))
    }

    @Test
    fun formatsDates() {
        val today = java.time.LocalDate.of(2026, 11, 19)
        assertEquals("18/11 21:55", Dates.pretty("2026-11-18T21:55", today))
        assertEquals("20/11/2025", Dates.pretty("2025-11-20T14:36", today))
        assertEquals("ayer", Dates.age("2026-11-18T21:55", today))
        assertEquals("hoy", Dates.age("2026-11-19", today))
        assertEquals("hace 3 días", Dates.age("2026-11-16T22:22", today))
        assertEquals("", Dates.age("", today))
    }

    @Test
    fun favoritePricesRoundTrip() {
        val m = mapOf("GHR" to 15.39, "GLP" to 7.99)
        assertEquals(m, DataJson.decodePrices(DataJson.encodePrices(m)))
        assertEquals(emptyMap<String, Double>(), DataJson.decodePrices("not json"))
    }
}
