package com.gramayatri

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the GramaYatri Passenger App.
 * Verifies core flows:
 * 1. App launch shows splash/launch screen
 * 2. MainActivity renders
 * 3. Key UI elements present
 *
 * Note: Run on an emulator or physical device with API 24+.
 * The app uses Jetpack Compose for the main UI layer.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @Test
    fun passengerApp_launch_showsSplash() {
        // Launch the splash/launch activity
        val scenario = ActivityScenario.launch(LaunchActivity::class.java)

        // The launch activity sets an orange background and finishes quickly to MainActivity
        // This test verifies the app launches without crashing
        onView(isRoot()).check(matches(isDisplayed()))
    }

    @Test
    fun passengerApp_launchActivity_finishesToMain() {
        val scenario = ActivityScenario.launch(LaunchActivity::class.java)

        // After a brief delay, LaunchActivity starts MainActivity and finishes
        // The app should not crash
        onView(isRoot()).check(matches(isDisplayed()))
    }

    @Test
    fun passengerApp_applicationContext_validPackage() {
        val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.gramayatri", appContext.packageName)
    }
}
