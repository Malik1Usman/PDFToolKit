package com.example.pdftoolkit.ui.screens.root

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pdftoolkit.ui.screens.main.MainScreen
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfToolkitTheme {
                MainScreen()
            }
        }
    }
}
