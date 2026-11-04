package com.momosubtitle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.momosubtitle.app.navigation.MomoRoot
import com.momosubtitle.core.ui.MomoSubtitleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MomoSubtitleTheme {
                MomoRoot()
            }
        }
    }
}
