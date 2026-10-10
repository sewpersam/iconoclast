package dev.icondesigner.buildlogic.arcticons

/**
 * Scans SVG numbers: optional sign, digits, fraction, exponent. Separators are whitespace
 * and commas, and may be absent, as in `1-2` or `.5.5`.
 */
class NumberScanner(private val s: String, var pos: Int = 0) {

    fun skipSeparators() {
        while (pos < s.length && (s[pos].isWhitespace() || s[pos] == ',')) pos++
    }

    fun atEnd(): Boolean {
        skipSeparators()
        return pos >= s.length
    }

    fun peek(): Char? {
        skipSeparators()
        return if (pos < s.length) s[pos] else null
    }

    /** Next number, or null if the next token is not a number. */
    fun number(): Double? {
        skipSeparators()
        val start = pos
        var i = pos
        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
        var digits = 0
        while (i < s.length && s[i].isDigit()) { i++; digits++ }
        if (i < s.length && s[i] == '.') {
            i++
            while (i < s.length && s[i].isDigit()) { i++; digits++ }
        }
        if (digits == 0) return null
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            var j = i + 1
            if (j < s.length && (s[j] == '+' || s[j] == '-')) j++
            if (j < s.length && s[j].isDigit()) {
                while (j < s.length && s[j].isDigit()) j++
                i = j
            }
        }
        pos = i
        return s.substring(start, i).toDouble()
    }

    /** An arc flag: a single 0 or 1, which may run straight into the next token. */
    fun flag(): Boolean? {
        skipSeparators()
        if (pos >= s.length) return null
        return when (s[pos]) {
            '0' -> { pos++; false }
            '1' -> { pos++; true }
            else -> null
        }
    }

    /** Every number in the string, or null if anything else is found. */
    fun all(): List<Double>? {
        val out = ArrayList<Double>()
        while (!atEnd()) out += number() ?: return null
        return out
    }
}
