package id.co.jari.ocr.confidence

import id.co.jari.ocr.core.DateParser
import id.co.jari.ocr.core.ImageQuality
import id.co.jari.ocr.model.ConfidenceTier
import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.KtpModel
import id.co.jari.ocr.model.StnkModel

object ConfidenceCalculator {

    const val WEIGHT_OPTICAL = 0.50f
    const val WEIGHT_FORMAT = 0.35f
    const val WEIGHT_IMAGE = 0.15f

    const val HIGH_THRESHOLD = 0.85f
    const val MEDIUM_THRESHOLD = 0.60f

    private val PLATE_REGEX = Regex("""^[A-Z]{1,2}\s?[0-9]{1,4}\s?[A-Z]{1,3}$""")

    fun opticalConfidence(text: ExtractedText): Float {
        val confidences = text.lines.flatMap { it.elements }.map { it.confidence }
        if (confidences.isEmpty()) {
            val lineConfidence = text.lines.map { it.confidence }
            if (lineConfidence.isEmpty()) return 0f
            return lineConfidence.average().toFloat().coerceIn(0f, 1f)
        }
        return confidences.average().toFloat().coerceIn(0f, 1f)
    }

    fun ktpFormatConfidence(ktp: KtpModel): Float {
        val checks = listOf(
            ktp.nik.length == 16 && ktp.nik.all { it.isDigit() },
            ktp.rt != null && ktp.rw != null &&
                ktp.rt.length == 3 && ktp.rw.length == 3 &&
                ktp.rt.all { it.isDigit() } && ktp.rw.all { it.isDigit() },
            ktp.tanggalLahir?.let { DateParser.isValidIso(it) } == true,
            ktp.nama.isNotBlank() && ktp.nama.length >= 3
        )
        return checks.count { it }.toFloat() / checks.size
    }

    fun stnkFormatConfidence(stnk: StnkModel): Float {
        val checks = stnkFormatChecks(stnk)
        return checks.count { it.second }.toFloat() / checks.size
    }

    fun stnkFormatChecks(stnk: StnkModel): List<Pair<String, Boolean>> = listOf(
        "NRKB bentuk plat valid" to isValidPlate(stnk.nrkb),
        "Rangka 17 karakter" to (stnk.nomorRangka?.length == 17),
        "Mesin terisi (>=5)" to (!stnk.nomorMesin.isNullOrBlank() && stnk.nomorMesin.length >= 5),
        "Alamat terisi" to !stnk.alamatJalan.isNullOrBlank(),
        "Berlaku s/d valid" to (stnk.berlakuSampai?.let { DateParser.isValidIso(it) } == true),
        "Nama berbentuk nama" to isValidName(stnk.namaPemilik),
        "Merek berbentuk merek" to isValidMerek(stnk.merek),
        "Tipe berisi kode kendaraan" to isValidTipe(stnk.tipe),
        "Jenis kendaraan dikenali" to isValidJenis(stnk.jenis),
        "Tahun pembuatan masuk akal" to isValidTahun(stnk.tahunPembuatan)
    )

    private val NAME_BLOCKERS = listOf(
        "NOMOR", "REGISTRASI", "NRKB", "KEPOLISIAN", "INDONESIA", "REPUBLIK",
        "PEMILIK", "ALAMAT", "MEREK", "MERK", "TYPE", "TIPE", "JENIS", "MODEL",
        "TAHUN", "RANGKA", "MESIN", "WARNA", "BERLAKU", "SURAT", "TANDA", "VEHICLE",
        "CERTIFICATE", "NATIONAL", "POLICE", "METRO", "JAYA", "BERMOTOR", "KENDARAAN"
    )

    private fun isValidName(value: String): Boolean {
        val v = value.trim()
        if (v.length < 3 || v.length > 60) return false
        if (v.count { it.isLetter() } < 2) return false
        if (v.count { it.isDigit() } >= v.length / 2) return false
        val upper = v.uppercase()
        val firstWord = upper.split(Regex("""[^A-Z]+""")).firstOrNull { it.isNotEmpty() } ?: ""
        if (firstWord in NAME_BLOCKERS) return false
        return true
    }

    private fun isValidMerek(value: String?): Boolean {
        val v = value?.trim() ?: return false
        if (v.length < 2 || v.length > 30) return false
        val upper = v.uppercase()
        if (upper.all { it.isLetter() || it == ' ' || it == '-' || it == '.' }) {
            val firstWord = upper.split(Regex("""[^A-Z]+""")).firstOrNull { it.isNotEmpty() } ?: ""
            return firstWord !in NAME_BLOCKERS
        }
        return false
    }

    private fun isValidTipe(value: String?): Boolean {
        val v = value?.trim() ?: return false
        if (v.length < 3 || v.length > 40) return false
        if (!v.any { it.isLetter() }) return false
        return v.any { it.isDigit() } || v.count { it.isLetter() } >= 5
    }

    private fun isValidJenis(value: String?): Boolean {
        val v = value?.uppercase() ?: return false
        val known = listOf(
            "SEPEDA MOTOR", "SEPEDA", "MOBIL", "TRUCK", "BUS", "PICK UP",
            "PICKUP", "SEDAN", "MINIBUS", "MINIVAN", "MINI VAN", "JEEP", "SPORT"
        )
        return known.any { v.contains(it) }
    }

    private fun isValidTahun(value: String?): Boolean {
        val year = value?.trim()?.toIntOrNull() ?: return false
        return year in 1990..2030
    }

    fun isValidPlate(nrkb: String): Boolean =
        PLATE_REGEX.matches(nrkb.trim().uppercase())

    fun imageQualityScore(quality: ImageQuality): Float {
        val sharpness = (quality.laplacianVariance / 150f).coerceIn(0f, 1f)
        val glareFree = (1f - (quality.glareRatio / 0.5f)).coerceIn(0f, 1f)
        return (sharpness * 0.6f + glareFree * 0.4f).coerceIn(0f, 1f)
    }

    fun composite(optical: Float, format: Float, image: Float): Float =
        (WEIGHT_OPTICAL * optical + WEIGHT_FORMAT * format + WEIGHT_IMAGE * image)
            .coerceIn(0f, 1f)

    fun tier(score: Float): ConfidenceTier = when {
        score >= HIGH_THRESHOLD -> ConfidenceTier.HIGH
        score >= MEDIUM_THRESHOLD -> ConfidenceTier.MEDIUM
        else -> ConfidenceTier.LOW
    }
}
