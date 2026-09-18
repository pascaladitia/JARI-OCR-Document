package id.co.jari.ocr.model

enum class OcrError {
    INVALID_INPUT,
    TEXT_RECOGNITION_FAILED,
    NO_TEXT_FOUND
}

sealed interface OcrResult {
    val isSuccess: Boolean
        get() = this is Ktp || this is Stnk

    data class Ktp(val data: KtpModel) : OcrResult

    data class Stnk(val data: StnkModel) : OcrResult

    data class Failure(
        val error: OcrError,
        val message: String
    ) : OcrResult
}
