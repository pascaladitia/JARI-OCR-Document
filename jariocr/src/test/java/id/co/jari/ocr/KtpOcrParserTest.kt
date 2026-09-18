package id.co.jari.ocr

import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.parser.KtpOcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtpOcrParserTest {

    private fun sampleKtp(): ExtractedText = TestFixtures.text(
        TestFixtures.line("PROVINSI DKI JAKARTA"),
        TestFixtures.line("NIK : 317101O8128800l1"),
        TestFixtures.line("Nama : HENDRO WICAKSONO"),
        TestFixtures.line("Tempat/Tgl Lahir : JAKARTA, 12-08-1988"),
        TestFixtures.line("Jenis Kelamin : LAKI-LAKI Gol. Darah : O"),
        TestFixtures.line("Alamat : JL SUDIRMAN KAV 25 NO 4"),
        TestFixtures.line("RT/RW : 005/002"),
        TestFixtures.line("Kel/Desa : KARET TENGSIN"),
        TestFixtures.line("Kecamatan : TANAH ABANG"),
        TestFixtures.line("Agama : ISLAM"),
        TestFixtures.line("Status Perkawinan : KAWIN"),
        TestFixtures.line("Pekerjaan : KARYAWAN SWASTA"),
        TestFixtures.line("Kewarganegaraan : WNI"),
        TestFixtures.line("KOTA PEMBUATAN : JAKARTA SELATAN"),
        TestFixtures.line("TANGGAL PEMBUATAN : 14-08-2018")
    )

    @Test
    fun `parses all core fields`() {
        val ktp = KtpOcrParser.parse(sampleKtp())

        assertEquals("3171010812880011", ktp.nik)
        assertTrue(ktp.isValidNik)
        assertEquals("HENDRO WICAKSONO", ktp.nama)
        assertEquals("JAKARTA", ktp.tempatLahir)
        assertEquals("1988-08-12", ktp.tanggalLahir)
        assertEquals("LAKI-LAKI", ktp.jenisKelamin)
        assertEquals("O", ktp.golDarah)
        assertEquals("JL SUDIRMAN KAV 25 NO 4", ktp.alamat)
        assertEquals("005", ktp.rt)
        assertEquals("002", ktp.rw)
        assertEquals("KARET TENGSIN", ktp.kelDesa)
        assertEquals("TANAH ABANG", ktp.kecamatan)
        assertEquals("ISLAM", ktp.agama)
        assertEquals("KAWIN", ktp.statusPerkawinan)
        assertEquals("KARYAWAN SWASTA", ktp.pekerjaan)
        assertEquals("WNI", ktp.kewarganegaraan)
        assertEquals("JAKARTA SELATAN", ktp.kotaPembuatan)
        assertEquals("2018-08-14", ktp.tanggalPembuatan)
        assertEquals("SEUMUR HIDUP", ktp.berlakuHingga)
    }

    @Test
    fun `zero pads short RT RW`() {
        val text = TestFixtures.text(
            TestFixtures.line("NIK : 3171012345670001"),
            TestFixtures.line("Nama : BUDI"),
            TestFixtures.line("RT/RW : 5/2")
        )
        val ktp = KtpOcrParser.parse(text)
        assertEquals("005", ktp.rt)
        assertEquals("002", ktp.rw)
    }

    @Test
    fun `handles separate RT RW without slash`() {
        val text = TestFixtures.text(
            TestFixtures.line("NIK : 3171012345670001"),
            TestFixtures.line("RT.007 RW.013")
        )
        val ktp = KtpOcrParser.parse(text)
        assertEquals("007", ktp.rt)
        assertEquals("013", ktp.rw)
    }

    @Test
    fun `NIK with broken digit is flagged`() {
        val text = TestFixtures.text(
            TestFixtures.line("NIK : 317101 345670001"),
            TestFixtures.line("Nama : BUDI")
        )
        val ktp = KtpOcrParser.parse(text)

        assertEquals(false, ktp.isValidNik)
    }

    @Test
    fun `parses label and value on separate lines`() {
        val text = TestFixtures.text(
            TestFixtures.line("NIK"),
            TestFixtures.line("317101O8128800l1"),
            TestFixtures.line("Nama"),
            TestFixtures.line("HENDRO WICAKSONO"),
            TestFixtures.line("Tempat/Tgl Lahir"),
            TestFixtures.line("JAKARTA, 12-08-1988"),
            TestFixtures.line("Alamat"),
            TestFixtures.line("JL SUDIRMAN KAV 25 NO 4")
        )
        val ktp = KtpOcrParser.parse(text)

        assertEquals("3171010812880011", ktp.nik)
        assertEquals("HENDRO WICAKSONO", ktp.nama)
        assertEquals("JAKARTA", ktp.tempatLahir)
        assertEquals("1988-08-12", ktp.tanggalLahir)
        assertEquals("JL SUDIRMAN KAV 25 NO 4", ktp.alamat)
    }

    @Test
    fun `parses NIK recognized as N1K`() {
        val text = TestFixtures.text(
            TestFixtures.line("N1K : 3171010812880011"),
            TestFixtures.line("Nama : BUDI")
        )
        assertEquals("3171010812880011", KtpOcrParser.parse(text).nik)
    }

    @Test
    fun `date with OCR letter typos is corrected`() {
        val text = TestFixtures.text(
            TestFixtures.line("Nama : BUDI"),
            TestFixtures.line("Tempat/Tgl Lahir : JAKARTA, 12-O8-l988")
        )
        val ktp = KtpOcrParser.parse(text)
        assertEquals("1988-08-12", ktp.tanggalLahir)
        assertEquals("JAKARTA", ktp.tempatLahir)
    }

    @Test
    fun `two column layout with noisy labels fills values from data to the right`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("PROVINSI SUMATERA UTARA", b(150, 16, 413, 29)),
            TestFixtures.line("KABUPATEN BATUBARA", b(170, 38, 392, 52)),
            TestFixtures.line("NIK", b(17, 69, 52, 86)),
            TestFixtures.line("3674072257025008", b(133, 69, 295, 82)),
            TestFixtures.line("Nama", b(14, 102, 49, 114)),
            TestFixtures.line("SYAIFUL ARIF", b(140, 105, 223, 114)),
            TestFixtures.line("Ternpat/Tgi Lahir", b(15, 116, 115, 132)),
            TestFixtures.line("JAKARTA, 22-04-1984", b(138, 119, 285, 131)),
            TestFixtures.line("Jenis Kelanin", b(14, 133, 96, 148)),
            TestFixtures.line("LAKI-LAKI", b(140, 134, 203, 144)),
            TestFixtures.line("Alamat", b(15, 153, 57, 163)),
            TestFixtures.line("TAMBAK REJO", b(138, 153, 240, 163)),
            TestFixtures.line("RIRW", b(45, 169, 88, 179)),
            TestFixtures.line("KeVDesa", b(44, 184, 99, 196)),
            TestFixtures.line("Kecamatan", b(44, 200, 113, 213)),
            TestFixtures.line("Agama", b(15, 219, 58, 232))
        )
        val ktp = KtpOcrParser.parse(text)

        assertEquals("3674072257025008", ktp.nik)
        assertTrue(ktp.isValidNik)
        assertEquals("SYAIFUL ARIF", ktp.nama)
        assertEquals("JAKARTA", ktp.tempatLahir)
        assertEquals("1984-04-22", ktp.tanggalLahir)
        assertEquals("LAKI-LAKI", ktp.jenisKelamin)
        assertEquals("TAMBAK REJO", ktp.alamat)
    }

    @Test
    fun `two column layout with rt rw value fills rt and rw`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NIK", b(17, 69, 52, 86)),
            TestFixtures.line("3674072257025008", b(133, 69, 295, 82)),
            TestFixtures.line("Nama", b(14, 102, 49, 114)),
            TestFixtures.line("BUDI SANTOSO", b(140, 105, 223, 114)),
            TestFixtures.line("Alamat", b(15, 153, 57, 163)),
            TestFixtures.line("JL RAYA NO 5", b(138, 153, 240, 163)),
            TestFixtures.line("RIRW", b(45, 169, 88, 179)),
            TestFixtures.line("004/001", b(138, 169, 190, 179)),
            TestFixtures.line("Kecamatan", b(44, 200, 113, 213))
        )
        val ktp = KtpOcrParser.parse(text)

        assertEquals("004", ktp.rt)
        assertEquals("001", ktp.rw)
    }
}
