package com.noor.shoplocal.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

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
    private const val KEY_VERIFIER = "pkce_verifier"

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
            return persistTokenResponse(context, text, name, email)
        }
    }

    // ---- Social sign-in (Google / Apple) -------------------------------------

    /**
     * The social providers offered on the login screen. [id] is the name GoTrue
     * expects in `/authorize?provider=...`, and must be enabled (with that
     * provider's own client credentials) in the Supabase dashboard.
     */
    enum class OAuthProvider(val id: String, val label: String) {
        GOOGLE("google", "Google"),
        APPLE("apple", "Apple")
    }

    /**
     * Asks GoTrue which social providers are switched on.
     *
     * Checked before opening the browser because a provider that has not been
     * given its credentials yet answers `/authorize` with a raw JSON 400 — which
     * the user would otherwise read in a browser tab, with no redirect back to
     * the app. If the check itself cannot be completed we return true and let the
     * sign-in attempt proceed, rather than blocking a flow that may well work.
     */
    suspend fun isProviderEnabled(provider: OAuthProvider): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${Supabase.AUTH}/settings")
                    .addHeader("apikey", Supabase.ANON_KEY)
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@use true
                    JSONObject(text).optJSONObject("external")?.optBoolean(provider.id) ?: true
                }
            }.getOrElse {
                Log.w(TAG, "Could not read auth settings: ${it.message}")
                true
            }
        }

    /**
     * Builds the URL that starts a social sign-in. The app opens this in the
     * device browser, the user approves there, and the provider hands control
     * back to [Supabase.OAUTH_REDIRECT] — our deep link, handled by
     * `OAuthCallbackActivity`.
     *
     * The flow is **PKCE**: we keep a random `code_verifier` on the device and
     * send only its SHA-256 hash to the server. The authorization code that
     * comes back through the deep link is therefore useless to any other app
     * that might also claim our URL scheme, because it cannot produce the
     * verifier needed to redeem it.
     */
    fun beginOAuth(context: Context, provider: OAuthProvider): String {
        val verifier = randomVerifier()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VERIFIER, verifier).apply()

        val url = Uri.parse("${Supabase.AUTH}/authorize").buildUpon()
            .appendQueryParameter("provider", provider.id)
            .appendQueryParameter("redirect_to", Supabase.OAUTH_REDIRECT)
            .appendQueryParameter("code_challenge", sha256Base64Url(verifier))
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
            .toString()

        Log.d(TAG, "Starting ${provider.id} sign-in")
        return url
    }

    /**
     * Finishes a social sign-in from the deep link the browser sent us, and
     * caches the resulting session exactly like an email login does.
     *
     * Two shapes are accepted: `?code=...` (the PKCE flow we ask for, redeemed
     * below) and `#access_token=...` (the implicit flow GoTrue falls back to if
     * the challenge is ever dropped) — so the screen keeps working either way.
     */
    suspend fun completeOAuth(context: Context, data: Uri): Result<Session> =
        withContext(Dispatchers.IO) {
            runCatching {
                // A refusal ("cancel" on the consent screen) or a mis-configured
                // provider comes back as redirect parameters, not an HTTP error.
                redirectError(data)?.let { error(it) }

                val code = data.getQueryParameter("code")
                if (code != null) {
                    exchangeCodeForSession(context, code)
                } else {
                    val fragment = parseParams(data.fragment)
                    val access = fragment["access_token"]
                        ?: error("Sign-in did not return a session")
                    sessionFromAccessToken(context, access, fragment["refresh_token"].orEmpty())
                }
            }.onFailure { Log.w(TAG, "Social sign-in failed: ${it.message}") }
        }

    /** Redeems the one-time authorization code, proving we started the flow. */
    private fun exchangeCodeForSession(context: Context, code: String): Session {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val verifier = prefs.getString(KEY_VERIFIER, null)
            ?: error("Sign-in expired, please try again")
        prefs.edit().remove(KEY_VERIFIER).apply() // single use

        val body = JSONObject()
            .put("auth_code", code)
            .put("code_verifier", verifier)
            .toString()
        val request = Request.Builder()
            .url("${Supabase.AUTH}/token?grant_type=pkce")
            .addHeader("apikey", Supabase.ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(readError(text, resp.code))
            return persistTokenResponse(context, text, null)
        }
    }

    /** Builds a session from a bare access token by asking GoTrue who it belongs to. */
    private fun sessionFromAccessToken(context: Context, access: String, refresh: String): Session {
        val request = Request.Builder()
            .url("${Supabase.AUTH}/user")
            .addHeader("apikey", Supabase.ANON_KEY)
            .addHeader("Authorization", "Bearer $access")
            .build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(readError(text, resp.code))
            val user = JSONObject(text)
            val email = user.optString("email", "")
            val session = Session(
                access,
                user.getString("id"),
                email,
                displayName(user.optJSONObject("user_metadata"), email)
            )
            saveSession(context, session)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REFRESH, refresh).apply()
            Log.i(TAG, "Signed in ${session.email} via social provider")
            return session
        }
    }

    // ---- Shared helpers ------------------------------------------------------

    /**
     * Parses a GoTrue token response (password grant or PKCE exchange), caches
     * the session and returns it. [nameHint] wins over the server metadata so a
     * just-registered user keeps the name they typed.
     */
    private fun persistTokenResponse(
        context: Context,
        text: String,
        nameHint: String?,
        emailHint: String = ""
    ): Session {
        val json = JSONObject(text)
        val user = json.getJSONObject("user")
        val email = user.optString("email", emailHint)
        val session = Session(
            accessToken = json.getString("access_token"),
            userId = user.getString("id"),
            email = email,
            name = nameHint?.takeIf { it.isNotBlank() }
                ?: displayName(user.optJSONObject("user_metadata"), email)
        )
        saveSession(context, session)
        // Persist the refresh token so the session can be renewed after expiry.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_REFRESH, json.optString("refresh_token", "")).apply()
        Log.i(TAG, "Signed in ${session.email}")
        return session
    }

    /**
     * Picks a display name out of `user_metadata`. Email sign-ups store `name`;
     * Google sends `name` and `full_name`; Apple sends a name only on the very
     * first authorisation — so fall back to the local part of the address rather
     * than greeting the user with a blank profile.
     */
    private fun displayName(meta: JSONObject?, email: String): String {
        for (key in listOf("name", "full_name", "preferred_username")) {
            val value = meta?.optString(key).orEmpty()
            if (value.isNotBlank()) return value
        }
        val parts = listOf("given_name", "family_name").mapNotNull { meta?.optString(it) }
        return parts.joinToString(" ").trim().ifBlank { email.substringBefore("@") }
    }

    /** Reads an `error` / `error_description` out of the redirect's query or fragment. */
    private fun redirectError(data: Uri): String? {
        val params = parseParams(data.fragment) +
            (data.queryParameterNames.associateWith { data.getQueryParameter(it).orEmpty() })
        val description = params["error_description"] ?: params["error"] ?: return null
        return description.replace('+', ' ')
    }

    private fun parseParams(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null else Uri.decode(pair.substring(0, i)) to Uri.decode(pair.substring(i + 1))
        }.toMap()

    /** 64 random bytes, base64url-encoded — an 86-character PKCE verifier. */
    private fun randomVerifier(): String {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        return base64Url(bytes)
    }

    private fun sha256Base64Url(value: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )

    private fun readError(text: String, code: Int): String =
        try {
            val o = JSONObject(text)
            o.optString("msg", o.optString("error_description", o.optString("message", "Request failed (HTTP $code)")))
        } catch (e: Exception) {
            "Request failed (HTTP $code)"
        }
}
