package com.noor.shoplocal.util

import android.content.Context
import android.content.res.Configuration
import com.noor.shoplocal.data.Prefs
import java.util.Locale

/**
 * Wraps a base Context so the whole app renders in the user's chosen language
 * (English, isiZulu or Afrikaans). Each Activity applies this in attachBaseContext,
 * which is why switching the language in Settings re-skins every screen.
 */
object LocaleHelper {

    fun wrap(context: Context): Context {
        val code = Prefs.language(context)
        val locale = Locale(code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
