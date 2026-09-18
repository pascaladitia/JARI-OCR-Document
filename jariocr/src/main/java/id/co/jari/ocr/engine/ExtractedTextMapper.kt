package id.co.jari.ocr.engine

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import id.co.jari.ocr.model.ExtractedText
import id.co.jari.ocr.model.OcrBlock
import id.co.jari.ocr.model.OcrElement
import id.co.jari.ocr.model.OcrLine
import id.co.jari.ocr.model.OcrRect

object ExtractedTextMapper {

    fun map(mlText: Text): ExtractedText {
        val blocks = mlText.textBlocks.map { block ->
            OcrBlock(
                block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        boundingBox = line.boundingBox?.toOcrRect(),
                        elements = line.elements.map { element ->
                            OcrElement(
                                text = element.text,
                                confidence = element.confidence.coerceIn(0f, 1f),
                                boundingBox = element.boundingBox?.toOcrRect()
                            )
                        }
                    )
                }
            )
        }
        return ExtractedText(blocks)
    }

    private fun Rect.toOcrRect(): OcrRect = OcrRect(left, top, right, bottom)
}
