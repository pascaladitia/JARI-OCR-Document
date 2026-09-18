package id.co.jari.ocr.core

object TextNormalizer {

    private val NOISE = Regex("""[•●◦▪◦–—"“”‘’]""")

    fun normalize(raw: String): String = raw
        .replace('\u00A0', ' ')
        .replace(NOISE, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun uppercase(raw: String): String = normalize(raw).uppercase()

    fun compactUpper(raw: String): String = normalize(raw).uppercase().replace(" ", "")
}
