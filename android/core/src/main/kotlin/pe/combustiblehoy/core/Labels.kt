package pe.combustiblehoy.core

/**
 * Textos para mostrar en pantalla. Osinergmin publica nombres y direcciones en MAYÚSCULAS,
 * que ocupan ~20 % más de ancho y se leen peor en pantallas chicas.
 */
object Labels {
    /** Siglas y abreviaturas que se mantienen en mayúsculas. */
    private val upper = setOf(
        "SAC", "S.A.C.", "S.A.C", "SA", "S.A.", "S.A", "SRL", "S.R.L.", "S.R.L", "EIRL", "E.I.R.L.", "E.I.R.L",
        "SAA", "S.A.A.", "SCRL", "S.C.R.L.", "GLP", "GNV", "GNL", "EESS", "EE.SS.", "S/N", "SN",
        "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII",
        "MZ", "MZ.", "LT", "LT.", "AA.HH.", "AAHH", "AA.HH", "PJ", "PJ.", "ASOC.", "URB.",
    ).let { set -> set.mapTo(HashSet()) { it.trimEnd('.') } }

    /** Conectores que van en minúscula salvo al inicio. */
    private val lower = setOf("de", "del", "la", "las", "el", "los", "y", "e", "en", "con", "al", "por")

    /**
     * "MULTISERVIS LAS LOMAS E.I.R.L" → "Multiservis las Lomas E.I.R.L".
     * Texto que ya viene con minúsculas se respeta tal cual.
     */
    fun pretty(text: String): String {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty() || trimmed.any { it.isLowerCase() }) return trimmed
        var first = true
        return trimmed.split(' ').joinToString(" ") { raw ->
            val word = prettyWord(raw, first)
            if (raw.any { it.isLetter() }) first = false
            word
        }
    }

    private fun prettyWord(raw: String, first: Boolean): String {
        val core = raw.trim(',', ';', ':', '(', ')', '"', '-')
        if (core.isEmpty()) return raw
        val key = core.trimEnd('.')
        return when {
            key in upper -> raw
            // Letras sueltas ("MZ. A", "SECTOR B") se quedan en mayúscula.
            core.length == 1 && core.lowercase() !in lower -> raw
            core.any { it.isDigit() } -> raw
            !first && core.lowercase() in lower -> raw.lowercase()
            else -> raw.lowercase().split('-').joinToString("-") { part ->
                // "(LOS" → "(Los": capitaliza la primera letra, no el primer carácter.
                val i = part.indexOfFirst { it.isLetter() }
                if (i < 0) part else part.substring(0, i) + part[i].titlecase() + part.substring(i + 1)
            }
        }
    }

    /** Dirección y distrito legibles: "Av. Arica 123, Breña". */
    fun place(address: String, district: String): String =
        listOf(pretty(address), pretty(district)).filter { it.isNotBlank() }.joinToString(", ")
}
