package id.co.jari.ocr.parser

import id.co.jari.ocr.core.OcrTypoDictionary
import id.co.jari.ocr.model.OcrLine
import java.util.concurrent.ConcurrentHashMap

object ParserSupport {

    const val LABEL_SEPARATORS = ":.\\-; /"

    private val regexCache = ConcurrentHashMap<String, Regex>()

    fun topOf(line: OcrLine): Int = line.boundingBox?.top ?: Int.MIN_VALUE

    fun bottomOf(line: OcrLine): Int = line.boundingBox?.bottom ?: Int.MAX_VALUE

    fun labelRegex(label: String): Regex = regexCache.getOrPut(label) {
        val parts = label.uppercase()
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { part -> part.map { expandChar(it) }.joinToString("") }
        val body = if (parts.isEmpty()) {
            Regex.escape(label.uppercase())
        } else {
            parts.joinToString("[^A-Z0-9]*")
        }
        Regex("(?<![A-Z0-9])$body(?![A-Z0-9])", RegexOption.IGNORE_CASE)
    }

    private fun expandChar(c: Char): String = when (c) {
        'I' -> "[I1l]"
        'O' -> "[O0]"
        else -> Regex.escape(c.toString())
    }

    fun containsLabel(lineText: String, labels: List<String>): Boolean =
        labels.any { labelRegex(it).containsMatchIn(lineText) }

    fun valueAfterLabel(
        lineText: String,
        labels: List<String>,
        stopLabels: List<String> = emptyList()
    ): String? {
        for (label in labels) {
            val match = labelRegex(label).find(lineText) ?: continue
            var rest = lineText.substring(match.range.last + 1)
                .trim()
                .trimStart(*LABEL_SEPARATORS.toCharArray())
            if (rest.isBlank()) return null
            for (stop in stopLabels) {
                val stopIdx = rest.uppercase().indexOf(stop.uppercase())
                if (stopIdx > 0) {
                    rest = rest.substring(0, stopIdx).trim()
                }
            }
            return rest.trimEnd(':', ';', ',', '.', '-', ' ').trim().takeIf { it.isNotEmpty() }
        }
        return null
    }

    fun containsAny(text: String, keywords: List<String>, ignoreCase: Boolean = true): Boolean {
        val haystack = if (ignoreCase) text.uppercase() else text
        return keywords.any { haystack.contains(if (ignoreCase) it.uppercase() else it) }
    }

    fun pad3(raw: String?): String? {
        if (raw == null) return null
        val digits = OcrTypoDictionary.toStrictNumeric(raw)
        if (digits.isEmpty()) return null
        return digits.padStart(3, '0').takeIf { it.length == 3 && it.all(Char::isDigit) }
    }

    fun firstLineContaining(lines: List<OcrLine>, keywords: List<String>): OcrLine? =
        lines.firstOrNull { containsLabel(it.safeText, keywords) }

    fun tokensOf(vararg labels: String): List<String> =
        labels.flatMap { label ->
            label.split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        }

    fun tokensOf(labels: List<String>): List<String> = tokensOf(*labels.toTypedArray())

    fun findLabelLineIndex(
        lines: List<OcrLine>,
        strictLabels: List<String>,
        fuzzyTokens: List<String>,
        minTop: Int = Int.MIN_VALUE,
        excludeLabels: List<String> = emptyList(),
        used: Set<Int> = emptySet()
    ): Int {
        for (i in lines.indices) {
            val line = lines[i]
            if (i in used) continue
            if (line.safeText.isBlank()) continue
            if (topOf(line) < minTop) continue
            if (line.safeText.length > 40) continue
            if (excludeLabels.any { containsLabel(line.safeText, listOf(it)) }) continue
            if (containsLabel(line.safeText, strictLabels)) return i
        }
        var best = -1
        var bestScore = 0.0
        for (i in lines.indices) {
            val line = lines[i]
            if (i in used) continue
            if (line.safeText.isBlank()) continue
            if (topOf(line) < minTop) continue
            if (line.safeText.length > 60) continue
            if (excludeLabels.any { containsLabel(line.safeText, listOf(it)) }) continue
            val score = fuzzyLabelScore(line.safeText, fuzzyTokens)
            if (score > bestScore && score >= 0.5) {
                best = i
                bestScore = score
            }
        }
        return best
    }

    fun fuzzyLabelScore(text: String, tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        val words = text.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        var matched = 0
        for (token in tokens) {
            val t = token.uppercase()
            val hit = if (t.length <= 2) {
                words.any { it.contains(t) }
            } else {
                words.any { wordMatchesToken(it, token) }
            }
            if (hit) matched++
        }
        return matched.toDouble() / tokens.size
    }

    fun wordMatchesToken(word: String, token: String): Boolean {
        val w = word.uppercase().replace(Regex("""[^A-Z0-9]+"""), "")
        if (w.isEmpty()) return false
        val t = token.uppercase().trim()
        if (t.isEmpty()) return false
        if (kotlin.math.abs(w.length - t.length) > tolerance(t)) return false
        return distance(t, w) <= tolerance(t)
    }

    fun valueAfterFuzzyTokens(lineText: String, tokens: List<String>): String? {
        var bestEnd = -1
        for (token in tokens) {
            val end = fuzzyTokenEnd(lineText, token)
            if (end > bestEnd) bestEnd = end
        }
        if (bestEnd < 0) return null
        val rest = lineText.substring(bestEnd)
            .trim()
            .trimStart(*LABEL_SEPARATORS.toCharArray())
        if (rest.isBlank()) return null
        return rest.trimEnd(':', ';', ',', '.', '-', ' ').trim().takeIf { it.isNotEmpty() }
    }

    fun valueAfterLabelPrefix(lineText: String, tokens: List<String>): String? {
        val source = lineText.uppercase()
        var cursor = 0
        while (cursor < source.length) {
            val m = WORD_REGEX.find(source, cursor) ?: return null
            val word = lineText.substring(m.range.first, m.range.last + 1)
            if (tokens.none { wordMatchesToken(word, it) }) {
                val value = lineText.substring(m.range.first)
                    .trimStart(*LABEL_SEPARATORS.toCharArray())
                    .replaceFirst(Regex("""^[:;\-,.\s]"""), "")
                    .trim()
                return value.takeIf { it.isNotEmpty() }
            }
            cursor = m.range.last + 1
        }
        return null
    }

    private val WORD_REGEX = Regex("""[A-Z0-9]+""")

    fun fuzzyTokenEnd(text: String, token: String): Int {
        val t = token.uppercase().trim()
        if (t.isEmpty()) return -1
        val source = text.uppercase()
        if (source.length < t.length - tolerance(t)) return -1
        val th = tolerance(t)
        val minW = (t.length - 1).coerceAtLeast(2)
        val maxW = (t.length + 2).coerceAtMost(source.length)
        var best = -1
        for (w in minW..maxW) {
            for (i in 0..source.length - w) {
                if (!source[i].isLetterOrDigit()) continue
                val sub = source.substring(i, i + w)
                if (distance(t, sub) <= th) {
                    val end = i + w
                    if (end > best) best = end
                }
            }
        }
        return best
    }

    fun rightNeighborValue(
        lines: List<OcrLine>,
        anchor: OcrLine,
        excludeTokens: List<String> = emptyList()
    ): String? {
        val box = anchor.boundingBox ?: return null
        val toleranceY = (box.height * 1.3).toInt().coerceAtLeast(30)
        var best: OcrLine? = null
        var bestDy = Int.MAX_VALUE
        var bestDx = Int.MAX_VALUE
        for (line in lines) {
            if (line === anchor) continue
            val b = line.boundingBox ?: continue
            if (b.left < box.right) continue
            val dy = kotlin.math.abs(b.centerY - box.centerY)
            if (dy > toleranceY) continue
            val text = line.safeText.trim().trimStart(':', ';', '.', '-', ' ')
            if (text.isBlank()) continue
            if (excludeTokens.isNotEmpty() && fuzzyLabelScore(text, excludeTokens) >= 0.5) continue
            val dx = (b.left - box.right).coerceAtLeast(0)
            if (dy < bestDy || (dy == bestDy && dx < bestDx)) {
                bestDy = dy
                bestDx = dx
                best = line
            }
        }
        return best?.safeText?.trim()?.trimStart(':', ';', '.', '-', ' ')
            ?.takeIf { it.isNotBlank() }
    }

    private fun tolerance(token: String): Int = when {
        token.length <= 5 -> 1
        token.length <= 8 -> 2
        else -> 3
    }

    private fun confClass(c: Char): Char {
        val u = c.uppercaseChar()
        return when (u) {
            in "I1LlVJT" -> 'T'
            in "O0DQ" -> 'O'
            in "MN" -> 'M'
            in "BH" -> 'B'
            in "PR" -> 'P'
            in "EF" -> 'E'
            else -> u
        }
    }

    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        val la = a.length
        val lb = b.length
        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)
        for (i in 1..la) {
            curr[0] = i
            for (j in 1..lb) {
                val sub = if (confClass(a[i - 1]) == confClass(b[j - 1])) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + sub
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[lb]
    }
}

object SpatialAnchorMatcher {

    fun find(
        lines: List<OcrLine>,
        labels: List<String>,
        excludeKeywords: List<String> = emptyList()
    ): String? {
        val anchor = lines.firstOrNull { line ->
            ParserSupport.containsLabel(line.safeText, labels) &&
                (excludeKeywords.isEmpty() || !ParserSupport.containsLabel(line.safeText, excludeKeywords))
        } ?: return null

        val inline = ParserSupport.valueAfterLabel(anchor.safeText, labels)
        if (!inline.isNullOrBlank() && isValueLike(inline)) {
            return inline
        }

        val box = anchor.boundingBox ?: return inline?.takeIf { it.isNotBlank() && isValueLike(it) }
        val toleranceY = (box.height * 1.3).toInt().coerceAtLeast(30)

        var best: String? = null
        var bestDy = Int.MAX_VALUE
        var bestDx = Int.MAX_VALUE
        for (line in lines) {
            if (line === anchor) continue
            val b = line.boundingBox ?: continue
            if (b.left < box.right) continue
            val dy = kotlin.math.abs(b.centerY - box.centerY)
            if (dy > toleranceY) continue
            val text = line.safeText.trim().trimStart(':', ';', '.', '-', ' ')
            if (!isValueLike(text)) continue
            val dx = (b.left - box.right).coerceAtLeast(0)
            if (dy < bestDy || (dy == bestDy && dx < bestDx)) {
                bestDy = dy
                bestDx = dx
                best = text
            }
        }

        return best ?: inline?.takeIf { it.isNotBlank() && isValueLike(it) }
    }

    private val LABEL_TOKENS = listOf(
        "NIK/VIN", "NIK", "VIN", "RANGKA", "MESIN", "NOMOR", "BPKB", "NRKB",
        "REGISTRASI", "PEMILIK", "ALAMAT", "TYPE", "TIPE", "MERK", "MEREK",
        "TAHUN", "WARNA", "BAHAN", "SILINDER", "PENDAFTARAN", "URUT"
    )

    private fun isLabelLike(value: String): Boolean {
        val upper = value.uppercase().trim()
        if (upper.contains('/')) return true
        return LABEL_TOKENS.any { upper == it || upper.startsWith("$it ") || upper.endsWith(" $it") }
    }

    private fun isValueLike(value: String): Boolean =
        value.isNotBlank() && value.length <= 80 && !isLabelLike(value)
}

