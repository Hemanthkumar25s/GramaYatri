package com.gramayatri.ui.i18n

import com.gramayatri.data.model.AppLanguage

object AppText {
    fun chooseLanguage(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಭಾಷೆ ಆಯ್ಕೆಮಾಡಿ"
        AppLanguage.ENGLISH -> "Choose language"
    }

    fun english(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "English"
        AppLanguage.ENGLISH -> "English"
    }

    fun kannada(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಕನ್ನಡ"
        AppLanguage.ENGLISH -> "Kannada"
    }

    fun continueText(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಮುಂದುವರಿಸಿ"
        AppLanguage.ENGLISH -> "Continue"
    }

    fun from(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಇಂದ"
        AppLanguage.ENGLISH -> "From"
    }

    fun to(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಗೆ"
        AppLanguage.ENGLISH -> "To"
    }

    fun busesFound(language: AppLanguage, count: Int) = when (language) {
        AppLanguage.KANNADA -> "$count ಬಸ್‌ಗಳು ಕಂಡುಬಂದಿವೆ"
        AppLanguage.ENGLISH -> "$count buses found"
    }

    fun insideBus(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ನಾನು ಈ ಬಸ್ ಒಳಗಿದ್ದೇನೆ"
        AppLanguage.ENGLISH -> "I am inside this bus"
    }

    fun privateTracking(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ನಿಮ್ಮ ಮೊಬೈಲ್ GPS ನಿಮಗಾಗಿ ಮಾತ್ರ ಬಳಸಲಾಗುತ್ತದೆ."
        AppLanguage.ENGLISH -> "Your phone GPS is used only for your own screen."
    }

    fun speed(language: AppLanguage, kmh: Int) = when (language) {
        AppLanguage.KANNADA -> "ವೇಗ: $kmh km/h"
        AppLanguage.ENGLISH -> "Speed: $kmh km/h"
    }

    // ─── Route Search ───────────────────────────────────────────────────

    fun searchBuses(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಬಸ್‌ಗಳನ್ನು ಹುಡುಕಿ"
        AppLanguage.ENGLISH -> "Search Buses"
    }

    fun selectDate(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ದಿನಾಂಕ ಆಯ್ಕೆಮಾಡಿ"
        AppLanguage.ENGLISH -> "Select Date"
    }

    fun selectTime(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಸಮಯ ಆಯ್ಕೆಮಾಡಿ"
        AppLanguage.ENGLISH -> "Select Time"
    }

    fun departureTime(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಹೊರಡುವ ಸಮಯ"
        AppLanguage.ENGLISH -> "Departure Time"
    }

    fun busSchedule(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಬಸ್ ವೇಳಾಪಟ್ಟಿ"
        AppLanguage.ENGLISH -> "Bus Schedule"
    }

    fun busTypeKSRTC(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "KSRTC ಸಾರಿಗೆ"
        AppLanguage.ENGLISH -> "KSRTC Transport"
    }

    fun viaStops(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಮೂಲಕ"
        AppLanguage.ENGLISH -> "Via"
    }

    fun noBusesFound(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಈ ಮಾರ್ಗದಲ್ಲಿ ಯಾವುದೇ ಬಸ್‌ಗಳು ಕಂಡುಬಂದಿಲ್ಲ"
        AppLanguage.ENGLISH -> "No buses found on this route"
    }

    fun serviceFrequency(language: AppLanguage) = when (language) {
        AppLanguage.KANNADA -> "ಸೇವಾ ಆವರ್ತನ"
        AppLanguage.ENGLISH -> "Service Frequency"
    }
}

