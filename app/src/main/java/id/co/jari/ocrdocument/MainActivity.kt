package id.co.jari.ocrdocument

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import id.co.jari.ocrdocument.ui.OcrDemoScreen
import id.co.jari.ocrdocument.ui.theme.OCRDocumentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OCRDocumentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OcrDemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}