package id.co.jari.ocr

import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.OcrBlock
import id.co.jari.ocr.model.OcrElement
import id.co.jari.ocr.model.OcrLine
import id.co.jari.ocr.model.OcrRect

object TestFixtures {

    fun line(text: String, box: OcrRect? = null): OcrLine =
        OcrLine(
            text = text,
            boundingBox = box,
            elements = listOf(OcrElement(text = text, confidence = 0.95f, boundingBox = box))
        )

    fun text(vararg lines: OcrLine): ExtractedText =
        ExtractedText(listOf(OcrBlock(lines.toList())))

    fun box(left: Int, top: Int, right: Int, bottom: Int) = OcrRect(left, top, right, bottom)
}
