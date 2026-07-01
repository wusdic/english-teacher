package com.englishteacher.britspeak

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-device end-to-end smoke test: launches [MainActivity] (which wires the full Hilt
 * graph — engines, offline STT, TTS, Room, DataStore — and the Compose navigation) and verifies
 * the app renders its main navigation without crashing.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndShowsBottomNavigation() {
        composeRule.onNodeWithText("Topics").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
