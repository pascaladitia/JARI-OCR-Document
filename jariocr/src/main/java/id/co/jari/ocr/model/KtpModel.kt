package id.co.jari.ocr.model

data class KtpModel(
    val nik: String = "",
    val nama: String = "",
    val tempatLahir: String? = null,
    val tanggalLahir: String? = null,
    val jenisKelamin: String? = null,
    val golDarah: String? = null,
    val alamat: String? = null,
    val rt: String? = null,
    val rw: String? = null,
    val kelDesa: String? = null,
    val kecamatan: String? = null,
    val agama: String? = null,
    val statusPerkawinan: String? = null,
    val pekerjaan: String? = null,
    val kewarganegaraan: String? = "WNI",
    val kotaPembuatan: String? = null,
    val tanggalPembuatan: String? = null,
    val berlakuHingga: String = "SEUMUR HIDUP",
    val confidenceScore: Float = 0f,
    val confidenceTier: ConfidenceTier = ConfidenceTier.LOW,
    val scanMeta: OcrScanMeta? = null
) {
    val isValidNik: Boolean
        get() = nik.length == 16 && nik.all { it.isDigit() }
}
