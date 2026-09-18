package id.co.jari.ocrdocument.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.co.jari.ocr.model.ConfidenceTier

private data class BadgeStyle(
    val background: Color,
    val foreground: Color,
    val label: String,
    val icon: String
)

private fun styleFor(tier: ConfidenceTier): BadgeStyle = when (tier) {
    ConfidenceTier.HIGH -> BadgeStyle(Color(0xFFDCFCE7), Color(0xFF166534), "Tinggi", "\uD83D\uDFE2")
    ConfidenceTier.MEDIUM -> BadgeStyle(Color(0xFFFEF9C3), Color(0xFF854D0E), "Sedang", "\uD83D\uDFE1")
    ConfidenceTier.LOW -> BadgeStyle(Color(0xFFFEE2E2), Color(0xFF991B1B), "Rendah", "\uD83D\uDD34")
}

@Composable
fun OcrConfidenceBadge(
    tier: ConfidenceTier,
    score: Float,
    modifier: Modifier = Modifier
) {
    val style = styleFor(tier)
    val percentage = (score * 100).toInt().coerceIn(0, 100)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.background,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "${style.icon} ", style = MaterialTheme.typography.labelSmall)
            Text(
                text = "Akurasi $percentage% (${style.label})",
                color = style.foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
