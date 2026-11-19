package pe.combustiblehoy.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    @Test
    fun prettifiesUppercaseNames() {
        assertEquals("Multiservis las Lomas E.I.R.L", Labels.pretty("MULTISERVIS LAS LOMAS E.I.R.L"))
        assertEquals("Grifo San Martín SAC", Labels.pretty("GRIFO SAN MARTÍN SAC"))
        assertEquals("La Victoria", Labels.pretty("LA VICTORIA"))
        assertEquals("Villa María del Triunfo", Labels.pretty("VILLA MARÍA DEL TRIUNFO"))
        assertEquals("Av. 200 Esquina con Calle S/N, MZ. A, Lote 21", Labels.pretty("AV. 200 ESQUINA CON CALLE S/N, MZ. A, LOTE 21"))
        assertEquals("Estación (los Olivos) GLP", Labels.pretty("ESTACIÓN (LOS OLIVOS) GLP"))
        assertEquals("Km 12.5 Carretera Central", Labels.pretty("KM 12.5 CARRETERA CENTRAL"))
    }

    @Test
    fun keepsMixedCaseAndBlank() {
        assertEquals("Repsol Primax", Labels.pretty("Repsol Primax"))
        assertEquals("", Labels.pretty("   "))
        assertEquals("Av. Arica 123, Breña", Labels.place("AV. ARICA 123", "BREÑA"))
        assertEquals("Breña", Labels.place("", "BREÑA"))
    }
}
