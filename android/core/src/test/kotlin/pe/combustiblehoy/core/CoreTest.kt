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
        assertEquals("2026-11-19", meta.date)
        assertEquals(listOf("junin", "lima"), meta.departments.map { it.slug })

        val lima = DataJson.department(resource("lima.json"))
        val st = lima.stations.single()
        assertEquals("12345", st.id)
        assertEquals(15.39, st.price(Fuel.G90)!!, 1e-9)
        assertNull(st.price(Fuel.G95))
        assertEquals(LatLon(-12.0912, -77.0251), st.location)

        val prices = DataJson.prices(resource("prices.json"))
        assertEquals(1.85, prices.p["67890"]!!["GNV"]!!, 1e-9)
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
        Station(id = id, name = "S$id", lat = lat, lon = lon, prices = price?.let { mapOf("G90" to it) } ?: emptyMap())

    @Test
    fun rankingByPriceWithinRadius() {
        val origin = LatLon(-12.0912, -77.0251)
        val stations = listOf(
            st("near-expensive", 16.0, -12.0920, -77.0260),
            st("near-cheap", 15.0, -12.0950, -77.0300),
            st("far-cheapest", 14.0, -13.0, -76.0),
            st("no-fuel", null, -12.0912, -77.0251),
        )
        val r = Ranking.nearby(stations, Fuel.G90, origin, radiusKm = 3.0)
        assertEquals(listOf("near-cheap", "near-expensive"), r.map { it.station.id })

        val byDist = Ranking.nearby(stations, Fuel.G90, origin, radiusKm = 3.0, sort = SortMode.DISTANCE)
        assertEquals("near-expensive", byDist.first().station.id)

        val noGps = Ranking.nearby(stations, Fuel.G90, origin = null)
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
            FavoriteSnapshot("a", "A", mapOf("G90" to 15.50)),
            FavoriteSnapshot("b", "B", mapOf("G90" to 15.50)),
            FavoriteSnapshot("c", "C", mapOf("G90" to 15.50)),
            FavoriteSnapshot("d", "D", mapOf("DB5" to 16.0)),
        )
        val latest = mapOf(
            "a" to mapOf("G90" to 15.30),
            "b" to mapOf("G90" to 15.45),
            "c" to mapOf("G90" to 15.70),
            "d" to mapOf("DB5" to 15.0),
        )
        val changes = PriceAlerts.detect(favs, latest, Fuel.G90, threshold = 0.10)
        assertEquals(listOf("a", "c"), changes.map { it.stationId })
        assertTrue(changes.first().isDrop)
        assertEquals(listOf("a"), PriceAlerts.detect(favs, latest, Fuel.G90, onlyDrops = true).map { it.stationId })
        assertEquals("Gasohol 90 bajó S/ 0.20 → S/ 15.30", PriceAlerts.describe(changes.first()))
    }

    @Test
    fun favoritePricesRoundTrip() {
        val m = mapOf("G90" to 15.39, "GLP" to 7.99)
        assertEquals(m, DataJson.decodePrices(DataJson.encodePrices(m)))
        assertEquals(emptyMap<String, Double>(), DataJson.decodePrices("not json"))
    }
}
