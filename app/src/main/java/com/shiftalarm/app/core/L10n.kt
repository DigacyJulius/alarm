package com.shiftalarm.app.core

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shiftalarm.app.data.Store
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tiny in-app localization. The app defaults to English; the user can
 * switch to Chinese in Settings. Both translations live at the call site:
 *   t("Save", "儲存")
 * `lang` is a Compose state so every composable that calls t() recomposes
 * when the language changes.
 */
object L10n {
    var lang by mutableStateOf("en") // "en" | "zh"

    fun t(en: String, zh: String): String = if (lang == "zh") zh else en

    /** Locale for date/week formatting in the current language. */
    val locale: Locale
        get() = if (lang == "zh") Locale.TRADITIONAL_CHINESE else Locale.ENGLISH

    /** Long date pattern like "9月13日 週日" / "Sun, Sep 13". */
    val longDatePattern: String
        get() = if (lang == "zh") "M月d日 EEEE" else "EEE, MMM d"

    /** Short date-time pattern like "9月13日 09:00" / "Sep 13, 09:00". */
    val shortDatePattern: String
        get() = if (lang == "zh") "M月d日 HH:mm" else "MMM d, HH:mm"

    fun newLongDateFmt(): SimpleDateFormat = SimpleDateFormat(longDatePattern, locale)
    fun newShortDateFmt(): SimpleDateFormat = SimpleDateFormat(shortDatePattern, locale)

    /**
     * Load the saved language before showing UI outside of the main app
     * flow (notifications, receivers, services) so it matches the user's
     * choice even if no Activity has run yet in this process.
     */
    fun syncFromDisk(context: Context) {
        runCatching {
            val saved = runBlocking {
                Store(context).data.first().settings.language
            }
            if (saved.isNotEmpty()) lang = saved
        }
    }
}

/** Convenience top-level wrapper. */
fun t(en: String, zh: String): String = L10n.t(en, zh)
