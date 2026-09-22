package com.noor.shoplocal.data

import android.content.Context

/**
 * Per-device settings and a small offline cache, stored in SharedPreferences.
 *
 * The Settings screen writes here so the user's choices (language, theme,
 * notifications) survive restarts, and the last catalogue we fetched is cached as
 * JSON so the Home screen still shows products when the device is offline.
 */
object Prefs {
    private const val FILE = "shoplocal_prefs"

    const val KEY_LANGUAGE = "language"       // "en" | "zu" | "af"
    const val KEY_THEME = "theme"             // "light" | "dark" | "system"
    const val KEY_NOTIFY_PUSH = "notify_push"
    const val KEY_NOTIFY_EMAIL = "notify_email"
    private const val KEY_CATALOGUE_CACHE = "catalogue_cache"
    private const val KEY_RECENT = "recently_viewed"
    private const val MAX_RECENT = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun language(ctx: Context): String = prefs(ctx).getString(KEY_LANGUAGE, "en") ?: "en"
    fun setLanguage(ctx: Context, code: String) = prefs(ctx).edit().putString(KEY_LANGUAGE, code).apply()

    fun theme(ctx: Context): String = prefs(ctx).getString(KEY_THEME, "system") ?: "system"
    fun setTheme(ctx: Context, mode: String) = prefs(ctx).edit().putString(KEY_THEME, mode).apply()

    fun notifyPush(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_NOTIFY_PUSH, true)
    fun setNotifyPush(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_NOTIFY_PUSH, on).apply()

    fun notifyEmail(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_NOTIFY_EMAIL, false)
    fun setNotifyEmail(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_NOTIFY_EMAIL, on).apply()

    // ---- Offline catalogue cache --------------------------------------------
    fun cacheCatalogue(ctx: Context, json: String) =
        prefs(ctx).edit().putString(KEY_CATALOGUE_CACHE, json).apply()

    fun cachedCatalogue(ctx: Context): String? =
        prefs(ctx).getString(KEY_CATALOGUE_CACHE, null)

    // ---- Recently viewed (Shein/Takealot-style) -----------------------------
    /** Record a product id as most-recently viewed (de-duplicated, capped). */
    fun addRecentlyViewed(ctx: Context, productId: String) {
        val current = recentlyViewed(ctx).toMutableList()
        current.remove(productId)
        current.add(0, productId)
        val trimmed = current.take(MAX_RECENT)
        prefs(ctx).edit().putString(KEY_RECENT, trimmed.joinToString(",")).apply()
    }

    /** Product ids most-recently viewed, newest first. */
    fun recentlyViewed(ctx: Context): List<String> =
        prefs(ctx).getString(KEY_RECENT, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
