package id.co.jari.ocr

import id.co.jari.ocr.core.DateParser
import id.co.jari.ocr.core.OcrTypoDictionary
import id.co.jari.ocr.parser.ParserSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUtilsTest {

    @Test
    fun `typo dictionary converts O and l to digits for NIK`() {
        assertEquals("3171010812880011", OcrTypoDictionary.toStrictNumeric("317101O8128800l1"))
    }

    @Test
    fun `typo dictionary handles Z S B`() {
        assertEquals("2586", OcrTypoDictionary.toStrictNumeric("ZSB6"))
    }

    @Test
    fun `date parser splits place and converts DD-MM-YYYY to ISO`() {
        assertEquals("1988-08-12", DateParser.extractIso("JAKARTA, 12-08-1988"))
    }

    @Test
    fun `date parser rejects impossible date`() {
        assertNull(DateParser.extractIso("31-02-2020"))
        assertFalse(DateParser.isValidIso("2020-02-31"))
    }

    @Test
    fun `pad3 zero pads RT RW`() {
        assertEquals("005", ParserSupport.pad3("5"))
        assertEquals("002", ParserSupport.pad3("2"))
        assertEquals("010", ParserSupport.pad3("O1O"))
        assertNull(ParserSupport.pad3("1234"))
    }
}
