package id.co.jari.ocr

import id.co.jari.ocr.confidence.ConfidenceCalculator
import id.co.jari.ocr.core.ImageQuality
import id.co.jari.ocr.model.ConfidenceTier
import id.co.jari.ocr.model.KtpModel
import id.co.jari.ocr.model.StnkModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceCalculatorTest {

    @Test
    fun `composite follows brief weights`() {

        assertEquals(0.75f, ConfidenceCalculator.composite(1f, 0.5f, 0.5f), 0.0001f)
    }

    @Test
    fun `tier thresholds`() {
        assertEquals(ConfidenceTier.HIGH, ConfidenceCalculator.tier(0.85f))
        assertEquals(ConfidenceTier.MEDIUM, ConfidenceCalculator.tier(0.60f))
        assertEquals(ConfidenceTier.LOW, ConfidenceCalculator.tier(0.59f))
    }

    @Test
    fun `plate validation`() {
        assertTrue(ConfidenceCalculator.isValidPlate("B 1234 ABC"))
        assertTrue(ConfidenceCalculator.isValidPlate("AB 1 C"))
        assertFalse(ConfidenceCalculator.isValidPlate("ABC 1234"))
        assertFalse(ConfidenceCalculator.isValidPlate("1234"))
    }

    @Test
    fun `ktp format confidence rewards complete fields`() {
        val complete = KtpModel(
            nik = "3171010812880011",
            nama = "HENDRO WICAKSONO",
            rt = "005",
            rw = "002",
            tanggalLahir = "1988-08-12"
        )
        assertEquals(1f, ConfidenceCalculator.ktpFormatConfidence(complete), 0.0001f)

        val empty = KtpModel()
        assertEquals(0f, ConfidenceCalculator.ktpFormatConfidence(empty), 0.0001f)
    }

    @Test
    fun `stnk format confidence`() {
        val complete = StnkModel(
            nrkb = "B 1234 ABC",
            namaPemilik = "HENDRO WICAKSONO",
            alamatJalan = "JL CEMPAKA PUTIH",
            merek = "HONDA",
            tipe = "NF100 SLD",
            jenis = "SEPEDA MOTOR",
            tahunPembuatan = "2023",
            nomorRangka = "MH1JB8118KK123456",
            nomorMesin = "JB81E1123456",
            berlakuSampai = "2028-09-17"
        )
        assertEquals(1f, ConfidenceCalculator.stnkFormatConfidence(complete), 0.0001f)
    }

    @Test
    fun `image quality score`() {
        assertEquals(1f, ConfidenceCalculator.imageQualityScore(ImageQuality(150f, 0f)), 0.0001f)
        assertEquals(0f, ConfidenceCalculator.imageQualityScore(ImageQuality(0f, 0.5f)), 0.0001f)
    }
}
