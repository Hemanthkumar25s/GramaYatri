package com.gramayatri

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        showLaunchWindow()

        lifecycleScope.launch {
            delay(50)
            setContent {
                GramaYatriApp()
            }
        }
    }

    private fun showLaunchWindow() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(255, 111, 0))
            setPadding(48, 48, 48, 48)
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bus_splash)
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                bottomMargin = 32
            }
        }

        val title = TextView(this).apply {
            text = "Grama-Yatri"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitle = TextView(this).apply {
            text = "Loading live bus tracking..."
            textSize = 15f
            setTextColor(Color.argb(220, 255, 255, 255))
            gravity = Gravity.CENTER
        }

        content.addView(icon)
        content.addView(title)
        content.addView(subtitle)
        setContentView(content)
    }
}
