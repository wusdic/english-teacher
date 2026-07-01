package com.englishteacher.britspeak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.englishteacher.britspeak.ui.navigation.BritSpeakNavHost
import com.englishteacher.britspeak.ui.theme.BritSpeakTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BritSpeakTheme {
                BritSpeakNavHost()
            }
        }
    }
}
