package id.co.jari.ocr

import id.co.jari.ocr.confidence.ConfidenceCalculator
import id.co.jari.ocr.core.ImageQuality
import id.co.jari.ocr.parser.StnkOcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StnkAccuracyTest {

    @Test
    fun `clear full stnk fills every field and scores above 80%`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("SURAT TANDA NOMOR KENDARAAN BERMOTOR", b(95, 30, 380, 55)),
            TestFixtures.line("NOMOR REGISTRASI", b(30, 90, 130, 103)),
            TestFixtures.line("B 1234 ABC", b(150, 90, 220, 103)),
            TestFixtures.line("NAMA PEMILIK", b(30, 105, 120, 116)),
            TestFixtures.line("DRS. AHMAD WALUYO", b(150, 105, 240, 116)),
            TestFixtures.line("ALAMAT", b(30, 121, 90, 132)),
            TestFixtures.line("JL. MELATI RAYA NO. 25", b(150, 121, 270, 132)),
            TestFixtures.line("RT 002/RW 001, KEL. KEBON BARU, DKI JAKARTA", b(150, 133, 330, 143)),
            TestFixtures.line("MERK/ TYPE", b(30, 145, 120, 158)),
            TestFixtures.line("HONDA VARIO 125", b(150, 145, 250, 160)),
            TestFixtures.line("JENIS / MODEL", b(30, 162, 120, 176)),
            TestFixtures.line("SEPEDA MOTOR RODA 2", b(150, 162, 260, 176)),
            TestFixtures.line("TAHUN PEMBUATAN", b(30, 178, 130, 191)),
            TestFixtures.line("2021", b(150, 178, 185, 191)),
            TestFixtures.line("ISI SILINDER", b(30, 193, 120, 206)),
            TestFixtures.line("125 CC", b(150, 193, 200, 206)),
            TestFixtures.line("NOMOR RANGKA", b(30, 208, 130, 221)),
            TestFixtures.line("MH1JM812XPKK12345", b(150, 208, 300, 221)),
            TestFixtures.line("NOMOR MESIN", b(30, 223, 120, 236)),
            TestFixtures.line("JM81E1123456", b(150, 223, 260, 236)),
            TestFixtures.line("WARNA", b(30, 238, 80, 251)),
            TestFixtures.line("HITAM", b(150, 238, 195, 251)),
            TestFixtures.line("BAHAN BAKAR", b(30, 253, 110, 266)),
            TestFixtures.line("BENSIN", b(150, 253, 205, 266)),
            TestFixtures.line("WARNA TNKB", b(30, 268, 120, 281)),
            TestFixtures.line("HITAM", b(150, 268, 200, 281)),
            TestFixtures.line("NOMOR BPKB", b(30, 283, 120, 296)),
            TestFixtures.line("B-07431234", b(150, 283, 240, 296)),
            TestFixtures.line("NOMOR PENDAFTARAN", b(30, 298, 140, 311)),
            TestFixtures.line("A-12345/BP/2024", b(150, 298, 260, 311)),
            TestFixtures.line("BERLAKU SAMPAI", b(30, 313, 130, 326)),
            TestFixtures.line("16-09-2027", b(150, 313, 230, 326))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("B 1234 ABC", stnk.nrkb)
        assertEquals("DRS. AHMAD WALUYO", stnk.namaPemilik)
        assertEquals("JL. MELATI RAYA NO. 25", stnk.alamatJalan)
        assertEquals("002", stnk.rt)
        assertEquals("001", stnk.rw)
        assertEquals("KEBON BARU", stnk.kelDesa)
        assertEquals("JAKARTA", stnk.kota)
        assertEquals("HONDA", stnk.merek)
        assertEquals("VARIO 125", stnk.tipe)
        assertEquals("SEPEDA MOTOR RODA 2", stnk.jenis)
        assertEquals("SEPEDA MOTOR RODA 2", stnk.model)
        assertEquals("2021", stnk.tahunPembuatan)
        assertEquals("125 CC", stnk.isiSilinder)
        assertEquals("MH1JM812XPKK12345", stnk.nomorRangka)
        assertEquals("JM81E1123456", stnk.nomorMesin)
        assertEquals("HITAM", stnk.warna)
        assertEquals("BENSIN", stnk.bahanBakar)
        assertEquals("HITAM", stnk.warnaTnkb)
        assertEquals("B-07431234", stnk.nomorBpkb)
        assertEquals("A-12345/BP/2024", stnk.nomorPendaftaran)
        assertEquals("2027-09-16", stnk.berlakuSampai)
        assertNull(stnk.tahunRegistrasi)

        val format = ConfidenceCalculator.stnkFormatConfidence(stnk)
        assertEquals(1f, format, 0.0001f)

        val composite = ConfidenceCalculator.composite(0.95f, format, ConfidenceCalculator.imageQualityScore(ImageQuality(150f, 0f)))
        assertTrue("harus di atas 80: $composite", composite >= 0.80f)
    }

    @Test
    fun `polri stnk does not mistake owner nik for frame number`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NAMA PEMILIK", b(32, 101, 100, 111)),
            TestFixtures.line("DRS, IOKATIUS MARWoTO", b(141, 99, 271, 115)),
            TestFixtures.line("ALAMAT", b(32, 121, 90, 132)),
            TestFixtures.line("JL. GATOT SUBROTO 25", b(150, 121, 280, 132)),
            TestFixtures.line("NOMOR RANGKA", b(30, 208, 130, 221)),
            TestFixtures.line("18JKABC1234XYZ567", b(150, 208, 300, 221)),
            TestFixtures.line("o 157107220855096", b(338, 83, 476, 100))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("18JKABC1234XYZ567", stnk.nomorRangka)
        assertNotNull(stnk.nomorRangka)
        assertTrue(stnk.nomorRangka!!.length == 17)
    }

    @Test
    fun `stnk with expected values lands each field in its own slot and passes all checks`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI", b(30, 90, 140, 102)),
            TestFixtures.line("B 5237 BLZ", b(160, 90, 260, 102)),
            TestFixtures.line("NAMA PEMILIK", b(30, 108, 120, 120)),
            TestFixtures.line("MUHAMAD FADLY", b(160, 108, 280, 120)),
            TestFixtures.line("ALAMAT", b(30, 126, 90, 138)),
            TestFixtures.line("RUSUN KEBERSIHAN/A/5/13", b(160, 126, 340, 138)),
            TestFixtures.line("MERK", b(30, 144, 80, 156)),
            TestFixtures.line("HONDA", b(160, 144, 220, 156)),
            TestFixtures.line("TYPE", b(30, 162, 90, 174)),
            TestFixtures.line("XIHO2N32L1 AT", b(160, 162, 280, 174)),
            TestFixtures.line("JENIS", b(30, 180, 90, 192)),
            TestFixtures.line("SEPEDA MOTOR", b(160, 180, 280, 192)),
            TestFixtures.line("TAHUN PEMBUATAN", b(30, 198, 140, 210)),
            TestFixtures.line("2024", b(160, 198, 200, 210))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("B 5237 BLZ", stnk.nrkb)
        assertEquals("MUHAMAD FADLY", stnk.namaPemilik)
        assertEquals("RUSUN KEBERSIHAN/A/5/13", stnk.alamatJalan)
        assertEquals("HONDA", stnk.merek)
        assertEquals("XIHO2N32L1 AT", stnk.tipe)
        assertEquals("SEPEDA MOTOR", stnk.jenis)
        assertEquals("2024", stnk.tahunPembuatan)

        val checks = ConfidenceCalculator.stnkFormatChecks(stnk).toMap()
        assertTrue("NRKB", checks["NRKB bentuk plat valid"]!!)
        assertTrue("Alamat", checks["Alamat terisi"]!!)
        assertTrue("Nama", checks["Nama berbentuk nama"]!!)
        assertTrue("Merek", checks["Merek berbentuk merek"]!!)
        assertTrue("Tipe", checks["Tipe berisi kode kendaraan"]!!)
        assertTrue("Jenis", checks["Jenis kendaraan dikenali"]!!)
        assertTrue("Tahun", checks["Tahun pembuatan masuk akal"]!!)
    }
}