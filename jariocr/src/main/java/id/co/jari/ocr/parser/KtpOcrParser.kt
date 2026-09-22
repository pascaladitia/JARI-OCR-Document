package id.co.jari.ocr.parser

import android.util.Log
import id.co.jari.ocr.core.DateParser
import id.co.jari.ocr.core.OcrTypoDictionary
import id.co.jari.ocr.core.TextNormalizer
import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.KtpModel
import id.co.jari.ocr.model.OcrLine

object KtpOcrParser {

    private val RT_RW_SLASH = Regex(
        """(?:RT|RW|RT/RW)\s*[:.\-]?\s*([0-9OIlD]{1,3})\s*/\s*([0-9OIlD]{1,3})""",
        RegexOption.IGNORE_CASE
    )
    private val RT_RW_DOT = Regex(
        """(?:RT)[.:\-]?\s*([0-9OIlD]{1,3})\s+(?:RW)[.:\-]?\s*([0-9OIlD]{1,3})""",
        RegexOption.IGNORE_CASE
    )
    private val GENDER = Regex("""(?i)(LAKI[\s-]*LAKI|PRIA|PEREMPUAN|WANITA)""")
    private val GOL_DARAH_LABEL = Regex("""(?i)GOL[\s.]*DARAH\s*[:.\-]?\s*([ABO+\-]{1,3})""")
    private val CITY_DATE_BOTTOM = Regex(
        """([A-Z][A-Z0-9\s.\-]*?)\s*,\s*([0-9OIlD]{1,2})[-/.]([0-9OIlD]{1,2})[-/.]([0-9OIlD]{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_IN_PLACE = Regex(
        """[0-9OIlD]{1,2}\s*[-/. ]\s*[0-9OIlD]{1,2}\s*[-/. ]\s*[0-9OIlD]{2,4}""",
        RegexOption.IGNORE_CASE
    )

    private val NAME_LABELS = listOf("NAMA")
    private val BIRTH_LABELS = listOf(
        "TEMPAT/TGL LAHIR", "TEMPAT/TANGGAL LAHIR", "TEMPAT TANGGAL LAHIR",
        "TEMPAT TGL LAHIR", "TGL LAHIR", "TANGGAL LAHIR", "TEMPAT LAHIR", "LAHIR"
    )
    private val GENDER_LABELS = listOf("JENIS KELAMIN", "JENISKELAMIN", "JK")
    private val BLOOD_LABELS = listOf("GOL. DARAH", "GOL DARAH", "GOLDARAH", "GOLONGAN DARAH")
    private val KEL_LABELS = listOf("KEL/DESA", "KEL. DESA", "KELURAHAN/DESA", "KELURAHAN", "KEL", "DESA")
    private val KEC_LABELS = listOf("KECAMATAN", "KEC.")
    private val PEMBUATAN_LABELS = listOf(
        "KOTA PEMBUATAN", "KAB./KOTA PEMBUATAN", "KAB/KOTA PEMBUATAN", "PEMBUATAN"
    )
    private val TANGGAL_PEMBUATAN_LABELS = listOf("TANGGAL PEMBUATAN", "TGL PEMBUATAN")

    private data class Slot(val strict: List<String>, val tokens: List<String>)

    private val SLOTS = listOf(
        Slot(listOf("NIK"), listOf("NIK")),
        Slot(listOf("NAMA"), listOf("NAMA")),
        Slot(BIRTH_LABELS, listOf("TEMPAT", "TGL", "LAHIR", "TANGGAL")),
        Slot(GENDER_LABELS, listOf("JENIS", "KELAMIN")),
        Slot(listOf("ALAMAT"), listOf("ALAMAT")),
        Slot(listOf("RT/RW", "RT / RW", "RT RW", "RT. RW", "RT"), listOf("RT", "RW")),
        Slot(KEL_LABELS, listOf("KEL", "DESA", "KELURAHAN")),
        Slot(KEC_LABELS, listOf("KECAMATAN")),
        Slot(listOf("AGAMA"), listOf("AGAMA")),
        Slot(listOf("STATUS PERKAWINAN", "PERKAWINAN", "STATUS"), listOf("STATUS", "PERKAWINAN")),
        Slot(listOf("PEKERJAAN"), listOf("PEKERJAAN")),
        Slot(
            listOf("KEWARGANEGARAAN", "WARGANEGARA"),
            listOf("KEWARGANEGARAAN", "WARGANEGARA")
        )
    )

    private val CUT_TOKENS = listOf(
        "PROVINSI", "KABUPATEN", "NIK", "NAMA", "TEMPAT", "TGL", "TANGGAL", "LAHIR",
        "JENIS", "KELAMIN", "GOL", "DARAH", "ALAMAT", "KECAMATAN", "AGAMA", "STATUS",
        "PERKAWINAN", "PEKERJAAN", "KEWARGANEGARAAN", "WARGANEGARA", "BERLAKU", "HINGGA",
        "PEMBUATAN"
    )

    fun parse(text: ExtractedText): KtpModel {
        val lines = text.lines.sortedBy { ParserSupport.topOf(it) }
        val used = HashSet<Int>()
        var lastTop = Int.MIN_VALUE

        val fields = HashMap<Slot, String?>()

        for (slot in SLOTS) {
            val idx = ParserSupport.findLabelLineIndex(
                lines, slot.strict, slot.tokens, lastTop, used = used
            )
            if (idx < 0) continue
            used.add(idx)
            val bottom = ParserSupport.bottomOf(lines[idx])
            if (bottom != Int.MAX_VALUE && bottom > lastTop) lastTop = bottom
            fields[slot] = fieldValue(lines, idx, slot.strict, slot.tokens)
        }

        Log.d("Tag Extract data", lines.toString())

        val nik = extractNik(lines)
        val birth = extractBirth(lines, fields[BIRTH_SLOT])
        val gender = extractGenderAndBlood(lines, fields[GENDER_SLOT])
        val rtRw = extractRtRw(lines, fields[RT_SLOT])
        val pembuatan = extractPembuatan(lines)

        val model = KtpModel(
            nik = nik,
            nama = fields[NAME_SLOT] ?: "",
            tempatLahir = birth.first,
            tanggalLahir = birth.second,
            jenisKelamin = gender.first,
            golDarah = gender.second,
            alamat = fields[ALAMAT_SLOT],
            rt = rtRw.first,
            rw = rtRw.second,
            kelDesa = fields[KEL_SLOT],
            kecamatan = fields[KEC_SLOT],
            agama = fields[AGAMA_SLOT],
            statusPerkawinan = fields[STATUS_SLOT],
            pekerjaan = fields[PEKERJAAN_SLOT],
            kewarganegaraan = fields[WN_SLOT] ?: "WNI",
            kotaPembuatan = pembuatan.first,
            tanggalPembuatan = pembuatan.second
        )
        Log.d("Parse KTP", buildList {
            add("nik=${model.nik}")
            add("nama=${model.nama}")
            add("tl=${model.tempatLahir}")
            add("tgl=${model.tanggalLahir}")
            add("jk=${model.jenisKelamin}")
            add("alamat=${model.alamat}")
            add("rt=${model.rt}")
            add("rw=${model.rw}")
            add("kel=${model.kelDesa}")
            add("kec=${model.kecamatan}")
            add("agama=${model.agama}")
            add("status=${model.statusPerkawinan}")
            add("kerja=${model.pekerjaan}")
        }.filter { it.substringAfter('=').isNotBlank() }.joinToString(" | "))
        return model
    }

    private fun fieldValue(
        lines: List<OcrLine>,
        index: Int,
        strictLabels: List<String>,
        tokens: List<String>
    ): String? {
        val lineText = lines[index].safeText
        var value: String? = ParserSupport.valueAfterLabel(lineText, strictLabels)
        if (value.isNullOrBlank()) {
            value = ParserSupport.valueAfterFuzzyTokens(lineText, tokens)
        }
        if (value.isNullOrBlank()) {
            value = ParserSupport.rightNeighborValue(lines, lines[index])
        }
        if (value.isNullOrBlank()) {
            val next = lines.getOrNull(index + 1)?.safeText?.trim()
            if (!next.isNullOrBlank() && !ParserSupport.containsLabel(next, CUT_TOKENS)) {
                value = next
            }
        }
        if (value.isNullOrBlank()) return null
        return process(value, tokens)
    }

    private fun process(value: String, selfTokens: List<String>): String {
        var cut = value.length
        for (token in CUT_TOKENS) {
            if (token in selfTokens) continue
            val match = ParserSupport.labelRegex(token).find(value) ?: continue
            val start = match.range.first
            if (start in 1 until cut) cut = start
        }
        return TextNormalizer.normalize(value.substring(0, cut))
            .trimEnd(':', ';', ',', '.', '-')
            .trim()
    }

    private fun extractNik(lines: List<OcrLine>): String {
        val slot = NIK_SLOT

        val index = ParserSupport.findLabelLineIndex(
            lines,
            slot.strict,
            slot.tokens,
            used = emptySet()
        )

        if (index >= 0) {
            val value = fieldValue(
                lines,
                index,
                slot.strict,
                slot.tokens
            )

            if (!value.isNullOrBlank()) {
                val candidate = nikCandidate(value)
                if (candidate != null) return candidate
            }
        }

        for (line in lines) {
            val candidate = nikCandidateFromLine(line.safeText)
            if (candidate != null) return candidate
        }

        for (line in lines) {
            val tokens = line.safeText.split(
                Regex("""[^\p{L}\p{N}]+""")
            )

            for (token in tokens) {
                if (token.isBlank()) continue

                val digits = OcrTypoDictionary.toStrictNumeric(token)

                if (digits.length == 16 && digits.all { it.isDigit() }) {
                    return digits
                }

                if (digits.length >= 4 && digits.all { it.isDigit() }) {
                    return digits
                }
            }
        }

        for (line in lines) {
            val joined = StringBuilder()

            for (token in line.safeText.split(Regex("""\s+"""))) {
                if (token.any { it.isDigit() }) {
                    joined.append(
                        OcrTypoDictionary.toStrictNumeric(token)
                    )
                }
            }

            val digits = joined.toString()

            if (digits.length >= 4) {
                if (digits.length >= 16) {
                    val candidate = digits.take(16)

                    if (candidate.all { it.isDigit() }) {
                        return candidate
                    }
                }

                return digits
            }
        }

        return ""
    }

    private fun nikCandidate(value: String): String? {
        val digits = OcrTypoDictionary.toStrictNumeric(value)
        if (digits.isEmpty()) return null
        if (digits.length == 16) return digits
        if (digits.length in 4 until 16) {
            val spaced = OcrTypoDictionary.toNumericPreservingGaps(value)
            return spaced.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun nikCandidateFromLine(lineText: String): String? {
        val sb = StringBuilder()
        var pendingGap = false
        for (token in lineText.split(Regex("""\s+"""))) {
            if (!OcrTypoDictionary.isNumericToken(token)) continue
            if (sb.isNotEmpty() && pendingGap) sb.append(' ')
            sb.append(OcrTypoDictionary.toStrictNumeric(token))
            pendingGap = true
        }
        if (sb.isEmpty()) return null
        val spaced = sb.toString()
        val collapsed = spaced.replace(" ", "")
        if (collapsed.length == 16 && collapsed.all { it.isDigit() }) return collapsed
        if (collapsed.length in 4 until 16 && spaced != collapsed) return spaced
        return null
    }

    private fun extractBirth(lines: List<OcrLine>, value: String?): Pair<String?, String?> {
        if (value.isNullOrBlank()) {
            val index = lines.indexOfFirst { ParserSupport.containsLabel(it.safeText, BIRTH_LABELS) }
            if (index >= 0) return null to null
        }
        if (value.isNullOrBlank()) return null to null

        var iso = DateParser.extractIso(value)
        if (iso == null) {
            val index = lines.indexOfFirst { ParserSupport.containsLabel(it.safeText, BIRTH_LABELS) }
            if (index >= 0) {
                val next = lines.getOrNull(index + 1)?.safeText?.trim()
                if (!next.isNullOrBlank() && !ParserSupport.containsLabel(next, CUT_TOKENS)) {
                    iso = DateParser.extractIso(next)
                }
            }
        }

        val place = DATE_IN_PLACE.replace(value, " ")
            .trim()
            .trimEnd(',', ';', '-', ' ', '.')
            .let { TextNormalizer.uppercase(it) }
            .takeIf { it.isNotBlank() }

        return place to iso
    }

    private fun extractGenderAndBlood(lines: List<OcrLine>, genderValue: String?): Pair<String?, String?> {
        var gender: String? = null
        var blood: String? = null

        if (!genderValue.isNullOrBlank()) {
            val upper = genderValue.uppercase()
            gender = when {
                upper.contains("PEREMPUAN") || upper.contains("WANITA") -> "PEREMPUAN"
                upper.contains("LAKI") || upper.contains("PRIA") -> "LAKI-LAKI"
                else -> if (GENDER.containsMatchIn(upper)) upper.trim() else null
            }
        }

        val genderIndex = lines.indexOfFirst { ParserSupport.containsLabel(it.safeText, GENDER_LABELS) }
        if (genderIndex >= 0) {
            GOL_DARAH_LABEL.find(lines[genderIndex].safeText)?.let {
                blood = normalizeBlood(it.groupValues[1])
            }
        }

        if (blood == null) {
            val index = ParserSupport.findLabelLineIndex(
                lines, BLOOD_LABELS, ParserSupport.tokensOf(BLOOD_LABELS)
            )
            if (index >= 0) {
                val lineText = lines[index].safeText
                GOL_DARAH_LABEL.find(lineText)?.let {
                    blood = normalizeBlood(it.groupValues[1])
                }
                if (blood == null) {
                    val v = ParserSupport.valueAfterFuzzyTokens(lineText, ParserSupport.tokensOf(BLOOD_LABELS))
                        ?: ParserSupport.rightNeighborValue(lines, lines[index])
                    normalizeBlood(v ?: "")?.let { blood = it }
                }
            }
        }
        return gender to blood
    }

    private fun normalizeBlood(raw: String): String? {
        val cleaned = raw.uppercase().replace(Regex("""[^ABO+\-]"""), "")
        return when {
            cleaned.isEmpty() -> "-"
            cleaned.contains("AB") -> "AB"
            cleaned.contains("A") && cleaned.contains("B") -> "AB"
            cleaned.contains("A") -> "A"
            cleaned.contains("B") -> "B"
            cleaned.contains("O") -> "O"
            cleaned == "-" -> "-"
            else -> null
        }
    }

    private fun extractRtRw(lines: List<OcrLine>, slotValue: String?): Pair<String?, String?> {
        if (!slotValue.isNullOrBlank()) {
            val parsed = parseRtRwValue(slotValue)
            if (parsed.first != null && parsed.second != null) return parsed
        }
        for (line in lines) {
            val up = line.safeText.uppercase()
            if (up.contains("RT") && up.contains("RW")) {
                val parsed = parseRtRwValue(line.safeText)
                if (parsed.first != null) return parsed
            }
        }
        if (!slotValue.isNullOrBlank()) {
            parseRtRwValue(slotValue)?.let { if (it.first != null) return it }
        }
        return null to null
    }

    private fun parseRtRwValue(value: String): Pair<String?, String?> {
        val normalized = TextNormalizer.normalize(value)
        RT_RW_SLASH.find(normalized)?.let {
            return pad3(it.groupValues[1]) to pad3(it.groupValues[2])
        }
        RT_RW_DOT.find(normalized)?.let {
            return pad3(it.groupValues[1]) to pad3(it.groupValues[2])
        }
        if (normalized.length <= 12) {
            val slash = Regex("""^([0-9OIlD]{1,3})\s*/\s*([0-9OIlD]{1,3})$""").find(normalized)
            if (slash != null) {
                return pad3(slash.groupValues[1]) to pad3(slash.groupValues[2])
            }
        }
        return null to null
    }

    private fun pad3(raw: String?): String? = ParserSupport.pad3(raw)

    private fun extractPembuatan(lines: List<OcrLine>): Pair<String?, String?> {
        val bottom = lines.takeLast(12)
        var kota: String? = null
        var tanggal: String? = null

        val kotaIndex = bottom.indexOfFirst { ParserSupport.containsLabel(it.safeText, PEMBUATAN_LABELS) }
        if (kotaIndex >= 0) {
            val v = ParserSupport.valueAfterLabel(bottom[kotaIndex].safeText, PEMBUATAN_LABELS)
            if (!v.isNullOrBlank() && v.length in 2..60 && !v.contains(Regex("""\d"""))) {
                kota = TextNormalizer.uppercase(v).trimEnd(',', '-', '.', ';', ':')
            }
        }

        val tglIndex = bottom.indexOfFirst { ParserSupport.containsLabel(it.safeText, TANGGAL_PEMBUATAN_LABELS) }
        if (tglIndex >= 0) {
            var v = ParserSupport.valueAfterLabel(bottom[tglIndex].safeText, TANGGAL_PEMBUATAN_LABELS)
            if (v.isNullOrBlank()) v = bottom.getOrNull(tglIndex + 1)?.safeText?.trim()
            tanggal = v?.let { DateParser.extractIso(it) }
        }

        if (kota == null || tanggal == null) {
            for (line in bottom) {
                val m = CITY_DATE_BOTTOM.find(line.safeText) ?: continue
                val date = DateParser.parseGroups(
                    m.groupValues[2], m.groupValues[3], m.groupValues[4]
                )?.let { DateParser.toIso(it) } ?: continue
                if (tanggal == null) tanggal = date
                val cityRaw = m.groupValues[1].trim().trimEnd(',', '.', ' ')
                val cityIsLabel = ParserSupport.containsLabel(
                    cityRaw,
                    listOf("TANGGAL", "TGL", "PEMBUATAN", "LAHIR", "BERLAKU", "JATUH", "TEMPO")
                )
                if (kota == null && !cityIsLabel && cityRaw.length in 2..60 &&
                    cityRaw.count { it.isLetter() } >= 2 && cityRaw.count { it.isDigit() } == 0
                ) {
                    kota = TextNormalizer.uppercase(cityRaw)
                }
                if (kota != null) break
            }
        }
        return kota to tanggal
    }

    private fun slot(vararg strict: String, tok: List<String>) = Slot(strict.toList(), tok)

    private val NIK_SLOT = slot("NIK", tok = listOf("NIK"))
    private val NAME_SLOT = slot("NAMA", tok = listOf("NAMA"))
    private val BIRTH_SLOT = slot(*BIRTH_LABELS.toTypedArray(), tok = listOf("TEMPAT", "TGL", "LAHIR", "TANGGAL"))
    private val GENDER_SLOT = slot(*GENDER_LABELS.toTypedArray(), tok = listOf("JENIS", "KELAMIN"))
    private val ALAMAT_SLOT = slot("ALAMAT", tok = listOf("ALAMAT"))
    private val RT_SLOT = slot(
        "RT/RW", "RT / RW", "RT RW", "RT. RW", "RT", tok = listOf("RT", "RW")
    )
    private val KEL_SLOT = slot(*KEL_LABELS.toTypedArray(), tok = listOf("KEL", "DESA", "KELURAHAN"))
    private val KEC_SLOT = slot("KECAMATAN", "KEC.", tok = listOf("KECAMATAN"))
    private val AGAMA_SLOT = slot("AGAMA", tok = listOf("AGAMA"))
    private val STATUS_SLOT = slot(
        "STATUS PERKAWINAN", "PERKAWINAN", "STATUS", tok = listOf("STATUS", "PERKAWINAN")
    )
    private val PEKERJAAN_SLOT = slot("PEKERJAAN", tok = listOf("PEKERJAAN"))
    private val WN_SLOT = slot(
        "KEWARGANEGARAAN", "WARGANEGARA", tok = listOf("KEWARGANEGARAAN", "WARGANEGARA")
    )
}