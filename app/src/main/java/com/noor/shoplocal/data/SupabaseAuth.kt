package com.noor.shoplocal.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Thin auth client for the ShopLocal backend, built on OkHttp (no extra SDK).
 *
 * Talks to Supabase GoTrue for Email/Password sign-up and sign-in. Passwords are
 * never stored by the app — they are sent over HTTPS and hashed with **bcrypt**
 * server-side, satisfying the "encrypt the password" requirement. The resulting
 * session (JWT access token) is cached in SharedPreferences so the app can skip
 * the login screen on relaunch.
 */
object SupabaseAuth {

    private const val TAG = "SupabaseAuth"
    private const val PREFS = "shoplocal_session"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_UID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Session(
        val accessToken: String,
        val userId: String,
        val email: String,
        val name: String
    )

    // ---- Session persistence -------------------------------------------------

    fun currentSession(context: Context): Session? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = p.getString(KEY_TOKEN, null) ?: return null
        val uid = p.getString(KEY_UID, null) ?: return null
        return Session(token, uid, p.getString(KEY_EMAIL, "") ?: "", p.getString(KEY_NAME, "") ?: "")
    }

    private fun saveSession(context: Context, session: Session) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, session.accessToken)
            .putString(KEY_UID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
            .apply()
        Log.d(TAG, "Session cached for user ${session.userId}")
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Log.i(TAG, "User signed out; session cleared")
    }

    /**
     * True if the cached access token has passed its expiry (`exp`) claim. Used at
     * startup to decide whether an old session needs a refresh or a fresh login.
     */
    fun accessTokenExpired(context: Context): Boolean {
        val token = currentSession(context)?.accessToken ?: return true
        return try {
            val payload = token.split(".")[1]
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            val exp = JSONObject(json).optLong("exp", 0L)
            exp > 0L && System.currentTimeMillis() >= exp * 1000L
        } catch (e: Exception) {
            false // if we can't parse it, don't force a logout
        }
    }

    /**
     * Exchanges the stored refresh token for a new access token. Access tokens
     * expire after ~1 hour, so this is called automatically when an authenticated
     * request comes back 401 — without it, the cart/wishlist/orders would silently
     * stop working an hour after login. Returns the new access token, or null if
     * the session can no longer be refreshed (the user must log in again).
     */
    fun refreshBlocking(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val refresh = p.getString(KEY_REFRESH, null) ?: return null
        return try {
            val body = JSONObject().put("refresh_token", refresh).toString()
            val request = Request.Builder()
                .url("${Supabase.AUTH}/token?grant_type=refresh_token")
                .addHeader("apikey", Supabase.ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Token refresh failed (HTTP ${resp.code}); user must re-login")
                    return null
                }
                val json = JSONObject(text)
                val newAccess = json.getString("access_token")
                val newRefresh = json.optString("refresh_token", refresh)
                p.edit().putString(KEY_TOKEN, newAccess).putString(KEY_REFRESH, newRefresh).apply()
                Log.i(TAG, "Access token refreshed")
                newAccess
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh error: ${e.message}")
            null
        }
    }

    // ---- Auth ----------------------------------------------------------------

    /** Create a new Email/Password account, then sign in to obtain a token deterministically. */
    suspend fun signUp(context: Context, name: String, email: String, password: String): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "Registering $email")
                val body = JSONObject()
                    .put("email", email)
                    .put("password", password)
                    .put("data", JSONObject().put("name", name))
                    .toString()

                val request = Request.Builder()
                    .url("${Supabase.AUTH}/signup")
                    .addHeader("apikey", Supabase.ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body.toRequestBody(JSON))
                    .build()

                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error(readError(text, resp.code))
                }
                // A DB trigger auto-confirms the user, so we can log in immediately.
                signInBlocking(context, email, password, name)
            }.onFailure { Log.w(TAG, "Sign-up failed: ${it.message}") }
        }

    /** Authenticate an existing user and cache the session. */
    suspend fun signIn(context: Context, email: String, password: String): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching { signInBlocking(context, email, password, null) }
                .onFailure { Log.w(TAG, "Sign-in failed: ${it.message}") }
        }

    private fun signInBlocking(context: Context, email: String, password: String, name: String?): Session {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val request = Request.Builder()
            .url("${Supabase.AUTH}/token?grant_type=password")
            .addHeader("apikey", Supabase.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(readError(text, resp.code))
            val json = JSONObject(text)
            val token = json.getString("access_token")
            val refresh = json.optString("refresh_token", "")
            val user = json.getJSONObject("user")
            val meta = user.optJSONObject("user_metadata")
            val resolvedName = name ?: meta?.optString("name").orEmpty()
            val session = Session(token, user.getString("id"), user.optString("email", email), resolvedName)
            saveSession(context, session)
            // Persist the refresh token so the session can be renewed after expiry.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REFRESH, refresh).apply()
            Log.i(TAG, "Signed in ${session.email}")
            return session
        }
    }

    private fun readError(text: String, code: Int): String =
        try {
            val o = JSONObject(text)
            o.optString("msg", o.optString("error_description", o.optString("message", "Request failed (HTTP $code)")))
        } catch (e: Exception) {
            "Request failed (HTTP $code)"
        }
}
