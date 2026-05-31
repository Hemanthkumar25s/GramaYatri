package com.gramayatri.ui.screens.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gramayatri.data.model.AppLanguage
import com.gramayatri.ui.i18n.AppText

@Composable
fun IntroScreen(
    language: AppLanguage,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (language == AppLanguage.KANNADA) "ಗ್ರಾಮ-ಯಾತ್ರಿ ಹೇಗೆ ಸಹಾಯ ಮಾಡುತ್ತದೆ" else "How Grama-Yatri helps",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(18.dp))
        IntroItem(
            icon = Icons.Default.Search,
            title = if (language == AppLanguage.KANNADA) "From ಮತ್ತು To ನೀಡಿ" else "Enter From and To",
            body = if (language == AppLanguage.KANNADA) {
                "ನಿಮ್ಮ ಮಾರ್ಗದಲ್ಲಿ ಓಡುವ ಬಸ್‌ಗಳನ್ನು ನೋಡಿ."
            } else {
                "See buses running on your route."
            }
        )
        IntroItem(
            icon = Icons.Default.LocationOn,
            title = if (language == AppLanguage.KANNADA) "ಲೈವ್ ಬಸ್ ಸ್ಥಳ" else "Live bus location",
            body = if (language == AppLanguage.KANNADA) {
                "ಟಿಕೆಟ್ ಯಂತ್ರ ಅಥವಾ ಚಾಲಕ GPS ನಿಂದ ನಿಖರ ಸ್ಥಳ."
            } else {
                "Accurate location from ticket machine or driver GPS."
            }
        )
        IntroItem(
            icon = Icons.Default.Speed,
            title = if (language == AppLanguage.KANNADA) "ನೀವು ಬಸ್ ಒಳಗಿದ್ದರೆ" else "If you are inside the bus",
            body = if (language == AppLanguage.KANNADA) {
                "ನಿಮ್ಮ ಮೊಬೈಲ್ GPS ಬಳಸಿ ವೇಗವನ್ನು ನಿಮಗಾಗಿ ಮಾತ್ರ ತೋರಿಸುತ್ತದೆ."
            } else {
                "Your phone GPS can show speed only on your screen."
            }
        )
        IntroItem(
            icon = Icons.Default.DirectionsBus,
            title = if (language == AppLanguage.KANNADA) "ಲಾಗಿನ್ ಬೇಕೇ?" else "Is login required?",
            body = if (language == AppLanguage.KANNADA) {
                "ಪ್ರಯಾಣಿಕರಿಗೆ ಲಾಗಿನ್ ಬೇಕಿಲ್ಲ. ಚಾಲಕರಿಗೆ ಮಾತ್ರ ಪ್ರತ್ಯೇಕ ಮೋಡ್ ಇದೆ."
            } else {
                "Passengers do not need login. Driver mode is separate."
            }
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(AppText.continueText(language), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IntroItem(
    icon: ImageVector,
    title: String,
    body: String
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
