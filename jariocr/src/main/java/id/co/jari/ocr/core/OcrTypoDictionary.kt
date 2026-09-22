package id.co.jari.ocr.core

object OcrTypoDictionary {

    private val letterToDigit: Map<Char, Char> = HashMap<Char, Char>().apply {
        put('O', '0'); put('o', '0'); put('D', '0'); put('Q', '0')
        put('I', '1'); put('l', '1'); put('i', '1'); put('|', '1'); put('!', '1')
        put('Z', '2'); put('z', '2')
        put('E', '3')
        put('A', '4'); put('h', '4')
        put('S', '5'); put('s', '5')
        put('b', '6'); put('G', '6')
        put('T', '7'); put('J', '7')
        put('B', '8')
        put('g', '9'); put('q', '9')
    }

    fun digitOf(c: Char): Char? =
        letterToDigit[c] ?: c.takeIf { it.isDigit() }

    fun isNumericToken(raw: String): Boolean =
        raw.isNotEmpty() && raw.all { digitOf(it) != null }

    fun toStrictNumeric(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (c in raw) {
            val digit = digitOf(c)
            if (digit != null) {
                sb.append(digit)
            }
        }
        return sb.toString()
    }

    fun toNumericPreservingGaps(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val digit = digitOf(c)
            if (digit != null) {
                sb.append(digit)
                i++
                continue
            }
            if (c.isLetter()) {
                i++
                continue
            }
            when {
                sb.isEmpty() || sb.last().isWhitespace() -> i++
                c.isWhitespace() -> {
                    while (i < raw.length && raw[i].isWhitespace()) {
                        sb.append(raw[i])
                        i++
                    }
                }
                else -> {
                    sb.append(' ')
                    i++
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun toStrictNumericOrNull(raw: String, expectedLength: Int = -1): String? {
        val converted = toStrictNumeric(raw)
        if (converted.isEmpty()) return null
        if (expectedLength > 0 && converted.length != expectedLength) return null
        return converted
    }
}
