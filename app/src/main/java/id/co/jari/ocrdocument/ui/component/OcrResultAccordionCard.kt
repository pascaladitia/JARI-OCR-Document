package id.co.jari.ocrdocument.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.co.jari.ocr.model.ConfidenceTier
import id.co.jari.ocr.model.KtpModel
import id.co.jari.ocr.model.StnkModel

data class OcrFieldUi(
    val label: String,
    val value: String?,
    val fullWidth: Boolean = false
)

@Composable
fun OcrResultAccordionCard(
    modifier: Modifier = Modifier,
    confidenceScore: Float,
    confidenceTier: ConfidenceTier,
    fields: List<OcrFieldUi>,
    onRescanClicked: () -> Unit,
    photoBitmap: Bitmap? = null,
    title: String = "Hasil Pemindaian Dokumen"
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ FOTO DOKUMEN MELEBAR PENUH ]",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OcrConfidenceBadge(tier = confidenceTier, score = confidenceScore)

                    OutlinedButton(
                        onClick = onRescanClicked,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "\uD83D\uDD04  Scan Ulang",
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (expanded) Color(0xFFEFF6FF) else Color.White,
                        contentColor = Color(0xFF2563EB)
                    )
                ) {
                    Text(
                        text = if (expanded) "Sembunyikan Hasil OCR \u25B4" else "Lihat Hasil OCR \u25BE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "RINCIAN DATA HASIL SCAN:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        fields.chunked(2).forEach { rowFields ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowFields.forEach { field ->
                                    FieldBlock(
                                        field = field,
                                        modifier = if (field.fullWidth || rowFields.size == 1) {
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier.weight(1f)
                                        }
                                    )
                                }
                                if (rowFields.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { expanded = false },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Text("Konfirmasi & Lipat")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldBlock(field: OcrFieldUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(
                text = field.value?.takeIf { it.isNotBlank() } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1E293B)
            )
        }
    }
}

fun KtpModel.toFieldList(): List<OcrFieldUi> = listOf(
    OcrFieldUi("Nomor Induk Kependudukan (NIK)", nik, fullWidth = true),
    OcrFieldUi("Nama Lengkap (Sesuai KTP)", nama, fullWidth = true),
    OcrFieldUi("Tempat Lahir", tempatLahir),
    OcrFieldUi("Tanggal Lahir (ISO)", tanggalLahir),
    OcrFieldUi("Jenis Kelamin", jenisKelamin),
    OcrFieldUi("Golongan Darah", golDarah),
    OcrFieldUi("Alamat Rumah", alamat, fullWidth = true),
    OcrFieldUi("RT", rt),
    OcrFieldUi("RW", rw),
    OcrFieldUi("Kelurahan / Desa", kelDesa),
    OcrFieldUi("Kecamatan", kecamatan),
    OcrFieldUi("Agama", agama),
    OcrFieldUi("Status Perkawinan", statusPerkawinan),
    OcrFieldUi("Pekerjaan", pekerjaan),
    OcrFieldUi("Kewarganegaraan", kewarganegaraan),
    OcrFieldUi("Kota Pembuatan", kotaPembuatan),
    OcrFieldUi("Tanggal Pembuatan", tanggalPembuatan),
    OcrFieldUi("Masa Berlaku", berlakuHingga, fullWidth = true)
)

fun StnkModel.toFieldList(): List<OcrFieldUi> = listOf(
    OcrFieldUi("NRKB / Nomor Polisi", nrkb, fullWidth = true),
    OcrFieldUi("Nama Pemilik", namaPemilik, fullWidth = true),
    OcrFieldUi("Alamat Jalan (Baris 1)", alamatJalan, fullWidth = true),
    OcrFieldUi("RT", rt),
    OcrFieldUi("RW", rw),
    OcrFieldUi("Kelurahan / Desa", kelDesa),
    OcrFieldUi("Kota / Kabupaten", kota),
    OcrFieldUi("Alamat Lengkap (Konsolidasi)", alamatLengkap, fullWidth = true),
    OcrFieldUi("Merek", merek),
    OcrFieldUi("Tipe", tipe),
    OcrFieldUi("Tipe Dagang / Varian", tipeDagang),
    OcrFieldUi("Jenis", jenis),
    OcrFieldUi("Model", model),
    OcrFieldUi("Tahun Pembuatan", tahunPembuatan),
    OcrFieldUi("Isi Silinder / Daya", isiSilinder),
    OcrFieldUi("Nomor Rangka (VIN)", nomorRangka, fullWidth = true),
    OcrFieldUi("Nomor Mesin", nomorMesin, fullWidth = true),
    OcrFieldUi("Warna", warna),
    OcrFieldUi("Bahan Bakar", bahanBakar),
    OcrFieldUi("Warna TNKB", warnaTnkb),
    OcrFieldUi("Tahun Registrasi", tahunRegistrasi),
    OcrFieldUi("Nomor BPKB", nomorBpkb),
    OcrFieldUi("Nomor Pendaftaran", nomorPendaftaran),
    OcrFieldUi("Berlaku Sampai", berlakuSampai, fullWidth = true)
)
