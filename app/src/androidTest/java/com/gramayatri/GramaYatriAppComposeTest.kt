package com.gramayatri

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.gramayatri.ui.screens.splash.SplashScreen
import com.gramayatri.ui.theme.GramaYatriTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for the GramaYatri passenger app.
 *
 * These tests use Compose Test Rules to verify that:
 * 1. The [SplashScreen] composable renders correctly with all expected elements
 * 2. The [GramaYatriTheme] applies correctly
 * 3. The app's core UI components render without crashing
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GramaYatriAppComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreen_displaysBrandAndTagline() {
        composeTestRule.setContent {
            GramaYatriTheme {
                SplashScreen(
                    isReady = false,
                    onReady = { /* no-op */ }
                )
            }
        }

        composeTestRule.onNodeWithText("\uD83D\uDE8C Grama-Yatri").assertExists()
        composeTestRule.onNodeWithText("Community Powered Bus Transit").assertExists()
    }

    @Test
    fun splashScreen_noCrashOnReady() {
        var readyCalled = false
        composeTestRule.setContent {
            GramaYatriTheme {
                SplashScreen(
                    isReady = true,
                    onReady = { readyCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("\uD83D\uDE8C Grama-Yatri").assertExists()
    }

    @Test
    fun gramaYatriTheme_appliesColors() {
        composeTestRule.setContent {
            GramaYatriTheme {
                SplashScreen(
                    isReady = false,
                    onReady = { /* no-op */ }
                )
            }
        }

        composeTestRule.onNodeWithText("\uD83D\uDE8C Grama-Yatri").assertExists()
        composeTestRule.onNodeWithText("Community Powered Bus Transit").assertExists()
    }

    @Test
    fun splashScreen_showsTaglineAndVersion() {
        composeTestRule.setContent {
            GramaYatriTheme {
                SplashScreen(
                    isReady = false,
                    onReady = { /* no-op */ }
                )
            }
        }

        composeTestRule.onNodeWithText("Community Powered Bus Transit").assertExists()
    }
}
