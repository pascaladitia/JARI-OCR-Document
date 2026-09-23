package id.co.jari.ocr

import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.parser.StnkOcrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StnkOcrParserTest {

    private fun sampleStnk(): ExtractedText {
        val b = TestFixtures::box
        return TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI / NRKB : B 1234 ABC", b(50, 10, 320, 30)),
            TestFixtures.line("NAMA PEMILIK : HENDRO WICAKSONO", b(50, 40, 320, 60)),
            TestFixtures.line("ALAMAT : JL CEMPAKA PUTIH TENGAH NO 15", b(50, 70, 340, 90)),
            TestFixtures.line("RT.004 RW.002 KEL. CEMPAKA PUTIH JAKARTA PUSAT", b(60, 95, 420, 115)),
            TestFixtures.line("MEREK", b(50, 140, 100, 158)),
            TestFixtures.line("HONDA", b(260, 158, 320, 176)),
            TestFixtures.line("TYPE", b(50, 180, 100, 198)),
            TestFixtures.line("NF12E2S-AT", b(260, 198, 360, 216)),
            TestFixtures.line("TAHUN PEMBUATAN", b(50, 220, 170, 238)),
            TestFixtures.line("2023", b(260, 238, 300, 256)),
            TestFixtures.line("NO. RANGKA / NIK / VIN", b(50, 260, 210, 278)),
            TestFixtures.line("MH1JB8118KK123456", b(260, 278, 470, 296)),
            TestFixtures.line("NO. MESIN", b(50, 300, 140, 318)),
            TestFixtures.line("JB81E1123456", b(260, 318, 380, 336)),
            TestFixtures.line("WARNA", b(50, 340, 110, 358)),
            TestFixtures.line("HITAM", b(260, 358, 310, 376)),
            TestFixtures.line("BERLAKU SAMPAI", b(50, 380, 170, 398)),
            TestFixtures.line("17-09-2028", b(260, 398, 330, 416))

        )
    }

    @Test
    fun `parses off-grid label value pairs via spatial anchor`() {
        val stnk = StnkOcrParser.parse(sampleStnk())

        assertEquals("B 1234 ABC", stnk.nrkb)
        assertEquals("HENDRO WICAKSONO", stnk.namaPemilik)
        assertEquals("HONDA", stnk.merek)
        assertEquals("NF12E2S-AT", stnk.tipe)
        assertEquals("2023", stnk.tahunPembuatan)
        assertEquals("MH1JB8118KK123456", stnk.nomorRangka)
        assertEquals("JB81E1123456", stnk.nomorMesin)
        assertEquals("HITAM", stnk.warna)
        assertEquals("2028-09-17", stnk.berlakuSampai)
    }

    @Test
    fun `splits two-line address and normalizes RT RW`() {
        val stnk = StnkOcrParser.parse(sampleStnk())

        assertEquals("JL CEMPAKA PUTIH TENGAH NO 15", stnk.alamatJalan)
        assertEquals("004", stnk.rt)
        assertEquals("002", stnk.rw)
        assertEquals("CEMPAKA PUTIH", stnk.kelDesa)
        assertEquals("JAKARTA PUSAT", stnk.kota)
    }

    @Test
    fun `parses plate with spaces`() {
        val text = TestFixtures.text(
            TestFixtures.line("B 1234 ABC", TestFixtures.box(50, 10, 200, 30))
        )
        assertEquals("B 1234 ABC", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `VIN with letter O is corrected to zero`() {
        val text = TestFixtures.text(
            TestFixtures.line("NO. RANGKA / NIK / VIN", TestFixtures.box(50, 10, 210, 28)),
            TestFixtures.line("MH1JB8118KK1O3456", TestFixtures.box(260, 28, 470, 46))
        )
        assertEquals("MH1JB8118KK103456", StnkOcrParser.parse(text).nomorRangka)
    }

    @Test
    fun `missing fields remain null`() {
        val stnk = StnkOcrParser.parse(
            TestFixtures.text(TestFixtures.line("NAMA PEMILIK : BUDI", TestFixtures.box(10, 10, 300, 30)))
        )
        assertNull(stnk.merek)
        assertNull(stnk.tipe)
        assertNull(stnk.tahunPembuatan)
        assertEquals("BUDI", stnk.namaPemilik)
    }

    @Test
    fun `extracts plate when label has OCR typo and suffix is merged with next column`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISIRASI: DA 8513 BSKENGARAA BARU", b(17, 70, 400, 84)),
            TestFixtures.line("NAMA PEMILIK : PT SUMBER BARU", b(134, 86, 400, 99))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("DA 8513 BS", stnk.nrkb)
        assertEquals("PT SUMBER BARU", stnk.namaPemilik)
    }

    @Test
    fun `extracts labelless plate that appears alone on a line`() {
        val text = TestFixtures.text(
            TestFixtures.line("B 1234 ABC", TestFixtures.box(50, 10, 200, 30))
        )
        assertEquals("B 1234 ABC", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `merged merk type and jenis model header rows with combined inline values fill all fields`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASINAI", b(99, 58, 259, 74)),
            TestFixtures.line("DA 8513 BS", b(280, 60, 350, 72)),
            TestFixtures.line("NAMA PEMILIK", b(96, 83, 169, 91)),
            TestFixtures.line("BUDI SUKO", b(190, 84, 240, 91)),
            TestFixtures.line("ALAMAT", b(96, 103, 137, 110)),
            TestFixtures.line("KOMP PERGUDANGAN BIZPAR", b(190, 105, 400, 112)),
            TestFixtures.line("MERK/ TYPE", b(94, 153, 168, 166)),
            TestFixtures.line("HONDANF100 SLD", b(191, 152, 324, 164)),
            TestFixtures.line("JENIS / MODEL SEPEDA MOTOR", b(91, 166, 304, 183)),
            TestFixtures.line("TAHUN PENBUATAN PERAKGR905/2005", b(91, 186, 259, 204))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("DA 8513 BS", stnk.nrkb)
        assertEquals("BUDI SUKO", stnk.namaPemilik)
        assertEquals("HONDA", stnk.merek)
        assertEquals("NF100 SLD", stnk.tipe)
        assertEquals("SEPEDA MOTOR", stnk.jenis)
        assertEquals("SEPEDA MOTOR", stnk.model)
        assertEquals("2005", stnk.tahunPembuatan)
    }

    @Test
    fun `separate merk and typo typed rows fill from the right column`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("MERK", b(11, 131, 35, 139)),
            TestFixtures.line("HONDA", b(160, 133, 220, 141)),
            TestFixtures.line("TXPE", b(11, 146, 32, 157)),
            TestFixtures.line("NF100 SLD", b(160, 148, 220, 159))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("HONDA", stnk.merek)
        assertEquals("NF100 SLD", stnk.tipe)
    }

    @Test
    fun `noisy polri capture ignores header garbage and finds the real plate`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("sEPOUSIAN NEGARA REPUBLINN No so.F SNAER", b(131, 31, 597, 61)),
            TestFixtures.line("AAMANG, T109enberz922", b(498, 31, 629, 42)),
            TestFixtures.line("SURAT TANDA NOMOR KENDARAAN BERMOTOR", b(95, 50, 384, 81)),
            TestFixtures.line("o 157107220855096", b(338, 83, 476, 100)),
            TestFixtures.line("MOR RSSTRASI", b(50, 89, 121, 102)),
            TestFixtures.line("BG 1923 NY", b(145, 89, 198, 102)),
            TestFixtures.line("KOMGES POLIe", b(527, 97, 666, 131)),
            TestFixtures.line("DRS, IOKATIUS MARWoTO", b(141, 99, 271, 115)),
            TestFixtures.line("NAMA PEMILIK", b(32, 101, 100, 111)),
            TestFixtures.line("M PRATAMA,", b(519, 103, 573, 113))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("BG 1923 NY", stnk.nrkb)
        assertEquals("DRS, IOKATIUS MARWoTO", stnk.namaPemilik)
        assertNull(stnk.merek)
        assertNull(stnk.tipe)
        assertNull(stnk.alamatJalan)
    }

    @Test
    fun `scrambled ocr line order still fills front table fields using their spatial values`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("MERK", b(7, 514, 35, 526)),
            TestFixtures.line("TYPE", b(7, 535, 34, 546)),
            TestFixtures.line("JENIS", b(7, 550, 40, 568)),
            TestFixtures.line("MODEL", b(8, 567, 45, 589)),
            TestFixtures.line("HITAM", b(250, 516, 300, 526)),
            TestFixtures.line("NOMOR REGISTRASINAI", b(99, 58, 259, 74)),
            TestFixtures.line("DA 8513 BS", b(280, 60, 350, 72)),
            TestFixtures.line("1s SILINDER", b(3, 607, 75, 626)),
            TestFixtures.line("NAMA PEMILIK", b(96, 83, 169, 91)),
            TestFixtures.line("BUDI SUKO", b(190, 84, 240, 91)),
            TestFixtures.line("ALAMAT", b(96, 103, 137, 110)),
            TestFixtures.line("KOMP PERGUDANGAN", b(190, 105, 400, 112)),
            TestFixtures.line("NOMOR MESIN", b(6, 647, 84, 664)),
            TestFixtures.line("JB81E11234", b(250, 650, 340, 662)),
            TestFixtures.line("HONDANF100 SLD", b(191, 152, 324, 164)),
            TestFixtures.line("MERK/ TYPE", b(94, 153, 168, 166)),
            TestFixtures.line("JENIS / MODEL SEPEDA MOTOR", b(91, 166, 304, 183)),
            TestFixtures.line("TAHUN PENBUATAN PERAKGR905/2005", b(91, 186, 259, 204))
        )
        val stnk = StnkOcrParser.parse(text)

        assertEquals("HONDA", stnk.merek)
        assertEquals("NF100 SLD", stnk.tipe)
        assertEquals("SEPEDA MOTOR", stnk.jenis)
        assertEquals("SEPEDA MOTOR", stnk.model)
        assertEquals("2005", stnk.tahunPembuatan)
        assertEquals("DA 8513 BS", stnk.nrkb)
        assertEquals("BUDI SUKO", stnk.namaPemilik)
        assertEquals("JB81E11234", stnk.nomorMesin)
        assertEquals("KOMP PERGUDANGAN", stnk.alamatJalan)
    }

    @Test
    fun `corrects letter O misread inside plate digits`() {
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI : B O234 ABC", TestFixtures.box(50, 10, 400, 30))
        )
        assertEquals("B 0234 ABC", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `corrects digit misread inside plate suffix letters`() {
        val text = TestFixtures.text(
            TestFixtures.line("NRKB : B 1234 AB0", TestFixtures.box(50, 10, 400, 30))
        )
        assertEquals("B 1234 ABO", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `corrects first digit misread as plate prefix letter`() {
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI / NRKB : 8 1234 ABC", TestFixtures.box(50, 10, 420, 30))
        )
        assertEquals("B 1234 ABC", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `corrects mixed confusion in two letter prefix plates`() {
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI : DA 8O13 BS", TestFixtures.box(50, 10, 400, 30))
        )
        assertEquals("DA 8013 BS", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `prefers whole line plate over frame number substring`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("NOMOR REGISTRASI", b(50, 5, 180, 17)),
            TestFixtures.line("MH1JB8118KK123456", b(200, 6, 420, 18)),
            TestFixtures.line("B 1234 ABC", b(200, 40, 300, 52))
        )
        assertEquals("B 1234 ABC", StnkOcrParser.parse(text).nrkb)
    }

    @Test
    fun `parses year misread by OCR first digit confusion`() {
        val text = TestFixtures.text(
            TestFixtures.line("TAHUN PEMBUATAN : Z023", TestFixtures.box(50, 10, 250, 30))
        )
        assertEquals("2023", StnkOcrParser.parse(text).tahunPembuatan)
    }

    @Test
    fun `rejects impossible year extracted from misread value`() {
        val text = TestFixtures.text(
            TestFixtures.line("TAHUN PEMBUATAN : 8023", TestFixtures.box(50, 10, 250, 30))
        )
        assertNull(StnkOcrParser.parse(text).tahunPembuatan)
    }

    @Test
    fun `keeps letter I in short frame numbers but corrects full length VIN`() {
        val b = TestFixtures::box
        val short = StnkOcrParser.parse(
            TestFixtures.text(
                TestFixtures.line("NO. RANGKA", b(50, 10, 150, 28)),
                TestFixtures.line("JB8I1234", b(260, 28, 340, 46))
            )
        )
        assertEquals("JB8I1234", short.nomorRangka)

        val vin = StnkOcrParser.parse(
            TestFixtures.text(
                TestFixtures.line("NO. RANGKA", b(50, 10, 150, 28)),
                TestFixtures.line("MH1JB8118KKI23456", b(260, 28, 470, 46))
            )
        )
        assertEquals("MH1JB8118KK123456", vin.nomorRangka)
    }

    @Test
    fun `dotted warna tnkb label feeds warnaTnkb not warna`() {
        val b = TestFixtures::box
        val text = TestFixtures.text(
            TestFixtures.line("WARNA T.N.K.B", b(50, 340, 170, 358)),
            TestFixtures.line("HITAM", b(260, 358, 310, 376))
        )
        val stnk = StnkOcrParser.parse(text)
        assertEquals("HITAM", stnk.warnaTnkb)
        assertNull(stnk.warna)
    }

    @Test
    fun `fuzzy resolves OCR misspelled brand`() {
        val text = TestFixtures.text(
            TestFixtures.line("MEREK : SUZUKB AXELO", TestFixtures.box(50, 10, 300, 30))
        )
        assertEquals("SUZUKI", StnkOcrParser.parse(text).merek)
    }
}
