package id.co.jari.ocr.core

object DateParser {

    data class ParsedDate(val day: Int, val month: Int, val year: Int)

    private val DATE_PATTERN = Regex(
        """([0-9OIlD]{1,2})\s*[-/. ]\s*([0-9OIlD]{1,2})\s*[-/. ]\s*([0-9OIlD]{2,4})""",
        RegexOption.IGNORE_CASE
    )

    fun find(text: String): MatchResult? = DATE_PATTERN.find(text)

    fun extractDate(text: String): ParsedDate? {
        val m = find(text) ?: return null
        return parseGroups(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    fun parseGroups(dayRaw: String, monthRaw: String, yearRaw: String): ParsedDate? {
        val day = OcrTypoDictionary.toStrictNumeric(dayRaw).toIntOrNull() ?: return null
        val month = OcrTypoDictionary.toStrictNumeric(monthRaw).toIntOrNull() ?: return null
        var year = OcrTypoDictionary.toStrictNumeric(yearRaw).toIntOrNull() ?: return null
        if (year < 100) year += if (year > 40) 1900 else 2000
        return if (isValid(day, month, year)) ParsedDate(day, month, year) else null
    }

    fun isValid(day: Int, month: Int, year: Int): Boolean {
        if (month < 1 || month > 12 || day < 1 || year < 1900 || year > 2100) return false
        val daysInMonth = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 0
        }
        return day <= daysInMonth
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun toIso(date: ParsedDate): String = "%04d-%02d-%02d".format(date.year, date.month, date.day)

    fun toIsoIfValid(day: Int, month: Int, year: Int): String? =
        if (isValid(day, month, year)) "%04d-%02d-%02d".format(year, month, day) else null

    fun extractIso(text: String): String? = extractDate(text)?.let { toIso(it) }

    fun isValidIso(iso: String): Boolean {
        val m = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(iso) ?: return false
        return try {
            isValid(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        } catch (e: NumberFormatException) {
            false
        }
    }
}

