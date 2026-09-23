package id.co.jari.ocr.parser

import android.util.Log
import id.co.jari.ocr.core.DateParser
import id.co.jari.ocr.core.OcrTypoDictionary
import id.co.jari.ocr.core.TextNormalizer
import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.OcrLine
import id.co.jari.ocr.model.StnkModel

object StnkOcrParser {

    private val STRICT_PLATE = Regex("""([A-Z]{1,2})\s*(\d{1,4})\s*([A-Z]+)(?![A-Z0-9])""")

    private val LOOSE_PLATE = Regex("""([A-Z0-9]{1,3})\s*([A-Z0-9]{3,5})\s*([A-Z0-9]{1,3})""")

    private val DIGIT_TO_LETTER = mapOf(
        '0' to 'O', '1' to 'I', '2' to 'Z', '5' to 'S', '6' to 'G', '8' to 'B'
    )

    private val PLATE_SHAPE = Regex("""^[A-Z]{1,2} [0-9]{1,4} [A-Z]{1,3}$""")

    private val YEAR_PATTERN = Regex("""(?<!\d)([0-9OIZSBGq]{4})(?!\d)""")

    private val BRANDS = listOf(
        "SUZUKI", "YAMAHA", "HONDA", "KAWASAKI", "TVS", "BAJAJ", "VESPA", "BMW", "KTM",
        "BENELLI", "DUCATI", "PIAGGIO", "KYMCO", "APRILIA", "ROYAL ENFIELD", "HARLEY",
        "TRIUMPH", "TOYOTA", "DAIHATSU", "MITSUBISHI", "NISSAN", "ISUZU", "MAZDA",
        "HYUNDAI", "KIA", "CHEVROLET", "FORD", "PEUGEOT", "RENAULT", "AUDI", "MERCEDES",
        "VOLKSWAGEN", "VOLVO", "LEXUS", "DATSUN", "WULING", "CHERY", "GWM", "MG",
        "JAGUAR", "MINI", "SSANGYONG", "MAHINDRA", "LAND ROVER", "SKODA", "DFSK"
    ).sortedByDescending { it.replace(" ", "").length }

    private val KNOWN_LABELS = listOf(
        "NOMOR REGISTRASI", "NRKB", "NAMA PEMILIK", "ALAMAT", "MEREK", "MERK",
        "TYPE", "TIPE DAGANG", "TIPE", "JENIS", "MODEL", "TAHUN PEMBUATAN",
        "ISI SILINDER", "NO. RANGKA", "NO RANGKA", "NOMOR RANGKA", "NIK/VIN", "RANGKA/VIN", "NIK", "VIN",
        "NO. MESIN", "NO MESIN", "NOMOR MESIN", "WARNA TNKB", "WARNA KB",
        "WARNA", "BAHAN BAKAR", "BERLAKU SAMPAI", "MASA BERLAKU", "NO. BPKB", "NO BPKB",
        "NO. PENDAFTARAN", "NO PENDAFTARAN", "TH REGISTRASI", "TAHUN REGISTRASI"
    )

    private val ALL_TOKENS = ParserSupport.tokensOf(KNOWN_LABELS)

    fun parse(text: ExtractedText): StnkModel {
        val lines = text.lines.sortedBy { ParserSupport.topOf(it) }

        Log.d("Tag Extract STNK", lines.toString())

        val address = extractAddress(lines)

        val model = StnkModel(
            nrkb = extractNrkb(lines),
            namaPemilik = anchor(lines, listOf("NAMA PEMILIK", "NAMA")) ?: "",
            alamatJalan = address.jalan,
            rt = address.rt,
            rw = address.rw,
            kelDesa = address.kelDesa,
            kota = address.kota,
            alamatLengkap = address.fullAddress,
            merek = splitBrand(anchor(lines, listOf("MEREK", "MERK"), exclude = listOf("TIPE DAGANG"))),
            tipe = stripBrand(anchor(lines, listOf("TYPE", "TIPE"), exclude = listOf("DAGANG", "VARIAN"))),
            tipeDagang = anchor(lines, listOf("TIPE DAGANG", "VARIAN", "TIPE KENDARAAN")),
            jenis = anchor(lines, listOf("JENIS KENDARAAN", "JENIS BM", "JENIS")),
            model = anchor(lines, listOf("MODEL")),
            tahunPembuatan = extractYear(
                anchor(
                    lines,
                    listOf("TAHUN PEMBUATAN", "TH PEMBUATAN", "THN PEMBUATAN", "TAHUN"),
                    exclude = listOf("REGISTRASI")
                )
            ),
            isiSilinder = anchor(lines, listOf("ISI SILINDER", "SILINDER", "DAYA LISTRIK")),
            nomorRangka = cleanRangka(anchor(lines, listOf("NO. RANGKA", "NO RANGKA", "NOMOR RANGKA", "RANGKA"))),
            nomorMesin = cleanMesin(anchor(lines, listOf("NO. MESIN", "NO MESIN", "NOMOR MESIN", "MESIN"))),
            warna = anchor(lines, listOf("WARNA KB", "WARNA KENDARAAN", "WARNA"), exclude = listOf("TNKB", "T.N.K.B")),
            bahanBakar = anchor(lines, listOf("BAHAN BAKAR", "BAHAN BAKAR / BBM")),
            warnaTnkb = anchor(lines, listOf("WARNA TNKB", "WARNA T.N.K.B")),
            tahunRegistrasi = extractYear(anchor(lines, listOf("TH REGISTRASI", "TH. REGISTRASI", "TAHUN REGISTRASI"))),
            nomorBpkb = anchor(lines, listOf("NO. BPKB", "NO BPKB", "BPKB")),
            nomorPendaftaran = anchor(lines, listOf("NO. PENDAFTARAN", "NO PENDAFTARAN", "NO. URUT", "PENDAFTARAN")),
            berlakuSampai = extractBerlakuSampai(lines)
        )
        Log.d("Parse STNK", buildList {
            add("nrkb=${model.nrkb}")
            add("nama=${model.namaPemilik}")
            add("alamat=${model.alamatLengkap}")
            add("merk=${model.merek}")
            add("tipe=${model.tipe}")
            add("jenis=${model.jenis}")
            add("model=${model.model}")
            add("tahun=${model.tahunPembuatan}")
            add("rangka=${model.nomorRangka}")
            add("mesin=${model.nomorMesin}")
            add("warna=${model.warna}")
            add("berlaku=${model.berlakuSampai}")
        }.filter { it.substringAfter('=').isNotBlank() }.joinToString(" | "))
        return model
    }

    private fun extractNrkb(lines: List<OcrLine>): String {
        val index = ParserSupport.findLabelLineIndex(
            lines,
            listOf("NRKB", "NOMOR REGISTRASI", "NO REGISTRASI"),
            listOf("NRKB", "NOMOR", "REGISTRASI")
        )
        if (index >= 0) {
            val lineText = lines[index].safeText
            var value = ParserSupport.valueAfterLabel(
                lineText, listOf("NRKB", "NOMOR REGISTRASI", "NO REGISTRASI")
            )
            if (value.isNullOrBlank()) {
                value = ParserSupport.valueAfterFuzzyTokens(
                    lineText, listOf("NRKB", "NOMOR", "REGISTRASI")
                )
            }
            extractPlate(value)?.let { return it }
        }
        bestPlateAcrossLines(lines)?.plate?.let { return it }
        return ""
    }

    private fun extractPlate(source: String?): String? {
        if (source.isNullOrBlank()) return null
        return extractPlateInLine(source, 0)?.plate
    }

    private data class PlateCandidate(
        val plate: String,
        val corrections: Int,
        val wholeLine: Boolean,
        val index: Int
    )

    private fun bestPlateAcrossLines(lines: List<OcrLine>): PlateCandidate? =
        lines.mapIndexed { i, line ->
            extractPlateInLine(line.safeText, i)
        }.filterNotNull().minWithOrNull(
            compareBy({ it.corrections }, { !it.wholeLine }, { it.plate.length }, { it.index })
        )

    private fun extractPlateInLine(source: String, index: Int): PlateCandidate? {
        val upper = source.uppercase().trim()

        val strict = STRICT_PLATE.find(upper)
        if (strict != null && strict.groupValues[3].length > 3) {
            val letters1 = strict.groupValues[1]
            val digits = strict.groupValues[2]
            val suffix = strict.groupValues[3].take(2)
            val plate = "$letters1 $digits $suffix"
            return PlateCandidate(plate, 0, upper == plate, index)
        }

        normalizePlate(upper)?.let {
            return PlateCandidate(it.first, it.second, upper == it.first, index)
        }

        strict?.let { m ->
            val letters1 = m.groupValues[1]
            val digits = m.groupValues[2]
            val letterRun = m.groupValues[3]
            val suffix = if (letterRun.length <= 3) letterRun else letterRun.take(2)
            if (letters1.isEmpty() || digits.isEmpty() || suffix.isEmpty()) return null
            val plate = "$letters1 $digits $suffix"
            return PlateCandidate(plate, 0, upper == plate, index)
        }
        return null
    }

    private fun normalizePlate(upper: String): Pair<String, Int>? {
        val match = LOOSE_PLATE.find(upper) ?: return null
        val prefix = match.groupValues[1]
        val middle = match.groupValues[2]
        val suffix = match.groupValues[3]

        var corrections = 0
        val pfx = prefix.map { c -> DIGIT_TO_LETTER[c]?.also { corrections++ } ?: c }.joinToString("")
        val mid = middle.mapNotNull { c ->
            val d = OcrTypoDictionary.digitOf(c)
            if (d != null && d != c) corrections++
            d
        }.joinToString("")
        val sfx = suffix.map { c -> DIGIT_TO_LETTER[c]?.also { corrections++ } ?: c }.joinToString("")

        if (pfx.length !in 1..2 || pfx.any { !it.isLetter() }) return null
        if (mid.isEmpty() || mid.length > 4 || mid.any { !it.isDigit() }) return null
        if (sfx.isEmpty() || sfx.length > 3 || sfx.any { !it.isLetter() }) return null

        val plate = "$pfx $mid $sfx"
        return plate.takeIf { PLATE_SHAPE.matches(it) }?.let { it to corrections }
    }

    private data class AddressParts(
        val jalan: String? = null,
        val rt: String? = null,
        val rw: String? = null,
        val kelDesa: String? = null,
        val kota: String? = null,
        val fullAddress: String? = null
    )

    private fun extractAddress(lines: List<OcrLine>): AddressParts {
        val index = ParserSupport.findLabelLineIndex(
            lines, listOf("ALAMAT", "ALMT"), listOf("ALAMAT", "ALMT")
        )
        if (index < 0) return AddressParts()

        val labelLine = lines[index]
        var jalan: String? = ParserSupport.valueAfterLabel(labelLine.safeText, listOf("ALAMAT", "ALMT"))
            ?: ParserSupport.valueAfterFuzzyTokens(labelLine.safeText, listOf("ALAMAT", "ALMT"))
            ?: ParserSupport.rightNeighborValue(lines, labelLine, ALL_TOKENS)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !looksLikeCoordinate(it) }

        val afterLines = ((index + 1) until (index + 5).coerceAtMost(lines.size))
            .map { lines[it].safeText.trim() }
            .filter { it.isNotBlank() }

        val rest = mutableListOf<String>()
        var streetAssigned = jalan != null
        for (candidate in afterLines) {
            if (containsKnownLabel(candidate)) break
            val coordinate = looksLikeCoordinate(candidate)
            if (!streetAssigned && !coordinate) {
                jalan = candidate.trimEnd(',', '.', ';', ' ')
                streetAssigned = true
                continue
            }
            if (coordinate || rest.isEmpty()) rest.add(candidate)
            if (rest.size >= 2) break
        }

        val regionLine = rest.joinToString(" ").trim()
        val (rt, rw) = parseRtRw(regionLine)
        val kelDesa = parseKel(regionLine)
        val kota = parseKota(regionLine, kelDesa)

        val fullAddress = buildList {
            jalan?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (rt != null && rw != null) add("RT $rt/RW $rw")
            kelDesa?.takeIf { it.isNotBlank() }?.let { add("KEL. $it") }
            kota?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(", ").ifBlank { null }

        return AddressParts(
            jalan = jalan?.trim()?.takeIf { it.isNotBlank() },
            rt = rt,
            rw = rw,
            kelDesa = kelDesa,
            kota = kota,
            fullAddress = fullAddress
        )
    }

    private fun containsKnownLabel(text: String): Boolean {
        val upper = text.uppercase()
        return KNOWN_LABELS.any { upper.contains(it.uppercase()) }
    }

    private fun looksLikeCoordinate(text: String): Boolean {
        val upper = text.uppercase()
        return text.contains(Regex("""\bRT\s*[.:]?\s*\d""")) ||
            text.contains(Regex("""\bRW\s*[.:]?\s*\d""")) ||
            upper.contains("KEL.") ||
            upper.contains("KELURAHAN") ||
            upper.contains("DESA ") ||
            upper.contains("KEC.") ||
            upper.contains("KECAMATAN")
    }

    private fun parseRtRw(line: String): Pair<String?, String?> {
        val upper = line.uppercase()
        val rtMatch = Regex("""RT[\s.:\-]*([0-9OIlD]{1,3})""").find(upper)
        val rwMatch = Regex("""RW[\s.:\-]*([0-9OIlD]{1,3})""").find(upper)
        return ParserSupport.pad3(rtMatch?.groupValues?.get(1)) to
            ParserSupport.pad3(rwMatch?.groupValues?.get(1))
    }

    private fun parseKel(line: String): String? {
        if (line.isBlank()) return null
        val match = Regex(
            """(?i)(?:KEL|DESA|KELURAHAN)\s*[.:]?\s*([A-Z][A-Z0-9\s.,\-]*?)(?=\s*(?:KEC|JAKARTA|KOTA\b|KAB|DAERAH|DKI|$))"""
        ).find(line) ?: return null
        val kel = match.groupValues[1].trim().trimEnd('.', ',', ';', '-').trim()
        return TextNormalizer.uppercase(kel).takeIf { it.length in 2..50 }
    }

    private fun parseKota(line: String, kelDesa: String?): String? {
        if (line.isBlank()) return null
        val upper = line.uppercase()

        val markers = listOf(
            "JAKARTA", "KOTA", "KAB.", "KABUPATEN", "BANDUNG", "SURABAYA", "MEDAN",
            "SEMARANG", "PALEMBANG", "MAKASSAR", "TANGERANG", "DEPOK", "BEKASI",
            "BOGOR", "YOGYAKARTA", "DENPASAR", "PEKANBARU", "BATAM", "PADANG",
            "MALANG", "SOLO", "SURAKARTA"
        )
        var start = -1
        for (marker in markers) {
            val i = upper.indexOf(marker)
            if (i >= 0 && (start == -1 || i < start)) start = i
        }
        if (start >= 0) {
            val city = line.substring(start).trim().trimEnd('.', ';', ',', '-').trim()
            return TextNormalizer.uppercase(city).takeIf { it.isNotBlank() }
        }

        if (!kelDesa.isNullOrBlank()) {
            val idx = upper.indexOf(kelDesa.uppercase())
            if (idx >= 0) {
                val restText = line.substring(idx + kelDesa.length).trim().trimEnd('.', ',', ';', '-').trim()
                if (restText.length in 2..60 && restText.all { it.isLetter() || it == ' ' || it == '.' }) {
                    return TextNormalizer.uppercase(restText)
                }
            }
        }
        return null
    }

    private fun extractBerlakuSampai(lines: List<OcrLine>): String? {
        val labelLine = ParserSupport.findLabelLineIndex(
            lines,
            listOf("BERLAKU SAMPAI", "BERLAKU S/D", "MASA BERLAKU", "JATUH TEMPO", "BERLAKU"),
            listOf("BERLAKU", "SAMPAI", "TEMPO", "MASA")
        )
        if (labelLine >= 0) {
            val lineText = lines[labelLine].safeText
            val value = ParserSupport.valueAfterLabel(
                lineText,
                listOf("BERLAKU SAMPAI", "BERLAKU S/D", "MASA BERLAKU", "JATUH TEMPO", "BERLAKU")
            ) ?: ParserSupport.valueAfterFuzzyTokens(
                lineText, listOf("BERLAKU", "SAMPAI", "TEMPO", "JATUH", "MASA")
            ) ?: ParserSupport.rightNeighborValue(lines, lines[labelLine], ALL_TOKENS)
            DateParser.extractIso(value ?: lineText)?.let { return it }
        }

        for (line in lines.reversed().take(8)) {
            DateParser.extractIso(line.safeText)?.let { return it }
        }
        return null
    }

    private fun anchor(lines: List<OcrLine>, labels: List<String>, exclude: List<String> = emptyList()): String? {
        val tokens = ParserSupport.tokensOf(labels)
        val index = ParserSupport.findLabelLineIndex(lines, labels, tokens, excludeLabels = exclude)
        if (index < 0) return null
        val line = lines[index]
        val inline = ParserSupport.valueAfterLabel(line.safeText, labels)
            ?.takeIf { !looksLikeLabel(it) }
        val value = inline
            ?: ParserSupport.valueAfterFuzzyTokens(line.safeText, tokens)
                ?.takeIf { !looksLikeLabel(it) }
            ?: ParserSupport.valueAfterLabelPrefix(line.safeText, ALL_TOKENS)
                ?.takeIf { !looksLikeLabel(it) }
            ?: ParserSupport.rightNeighborValue(lines, line, ALL_TOKENS)
            ?: return null
        return cutAtNextLabel(value, labels)
    }

    private fun looksLikeLabel(value: String): Boolean {
        val trimmed = value.trim().trimStart(':', ';', ' ', '/', '-')
        if (trimmed.split(Regex("""\s+""")).size > 6) return false
        val firstWord = trimmed.split(Regex("""[^A-Z0-9]+""")).firstOrNull { it.isNotEmpty() } ?: return true
        if (firstWord.length >= 7) return false
        return isLabelToken(firstWord)
    }

    private fun isLabelToken(s: String): Boolean =
        ALL_TOKENS.any { ParserSupport.wordMatchesToken(s, it) }

    private fun compact(raw: String): String = raw.uppercase().replace(Regex("""\s+"""), "")

    private fun splitBrand(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val up = compact(raw)
        BRANDS.firstOrNull { up.startsWith(compact(it)) }?.let { return it }
        val firstWord = raw.uppercase().split(Regex("""[^A-Z]+""")).firstOrNull { it.isNotEmpty() }
        if (firstWord != null && firstWord.length >= 4) {
            BRANDS.firstOrNull { ParserSupport.wordMatchesToken(firstWord, compact(it)) }?.let { return it }
        }
        return raw.trim()
    }

    private fun stripBrand(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val up = compact(raw)
        for (brand in BRANDS) {
            val cb = compact(brand)
            if (up.startsWith(cb)) {
                val rest = raw.removePrefix(prefixInRaw(raw, cb)).trim()
                return rest.takeIf { it.isNotEmpty() }
            }
        }
        return raw.trim()
    }

    private fun prefixInRaw(raw: String, compactPrefix: String): String {
        val sb = StringBuilder()
        var nonSpace = 0
        for (c in raw) {
            if (nonSpace == compactPrefix.length) break
            sb.append(c)
            if (!c.isWhitespace()) nonSpace++
        }
        return sb.toString()
    }

    private fun extractYear(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = YEAR_PATTERN.find(raw) ?: return null
        val digits = match.groupValues[1]
            .mapNotNull { OcrTypoDictionary.digitOf(it) }
            .joinToString("")
        if (digits.length != 4) return null
        val year = digits.toIntOrNull() ?: return null
        return if (year in 1900..2100) year.toString() else null
    }

    private fun cutAtNextLabel(value: String, selfLabels: List<String>): String {
        var minIndex = value.length
        val upper = value.uppercase()
        for (label in KNOWN_LABELS) {
            if (selfLabels.any { it.equals(label, ignoreCase = true) }) continue
            val match = ParserSupport.labelRegex(label).find(upper) ?: continue
            val start = match.range.first
            if (start in 1 until minIndex) minIndex = start
        }
        return value.substring(0, minIndex).trim().trimEnd(':', ';', '.', '-', ',', ' ').trim()
    }

    private fun cleanRangka(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var value = raw.uppercase()
            .replace(Regex("""\s+"""), "")
            .replace('O', '0')
            .replace('Q', '0')
        if (value.length == 17) {
            value = value.replace('I', '1')
        }
        return value.takeIf { it.length >= 5 }
    }

    private fun cleanMesin(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.uppercase()
            .replace(Regex("""\s+"""), "")
            .takeIf { it.length >= 5 }
    }
}