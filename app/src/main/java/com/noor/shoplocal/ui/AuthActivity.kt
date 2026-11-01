package com.noor.shoplocal.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.noor.shoplocal.R
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.data.firestore.FirestoreRepo
import com.noor.shoplocal.databinding.ActivityAuthBinding
import com.noor.shoplocal.ui.auth.LoginFragment
import com.noor.shoplocal.ui.auth.RegisterFragment
import kotlinx.coroutines.launch

/**
 * Hosts the Login and Register fragments in a single container. Also receives the
 * Google SSO redirect (shoplocal://auth-callback) and completes the sign-in.
 */
class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) showLogin()
        handleOAuthRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthRedirect(intent)
    }

    /**
     * The SSO redirect returns the tokens in the URL fragment, e.g.
     * shoplocal://auth-callback#access_token=…&refresh_token=…. We parse them,
     * cache the session, then continue into the app.
     */
    private fun handleOAuthRedirect(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "shoplocal") return
        val fragment = data.fragment
        if (fragment.isNullOrBlank()) {
            // An error may come back as a query param instead.
            if (data.getQueryParameter("error") != null) {
                Toast.makeText(this, R.string.sso_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val params = fragment.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
        }.toMap()

        val accessToken = params["access_token"]
        val refreshToken = params["refresh_token"].orEmpty()
        if (accessToken.isNullOrBlank()) {
            Toast.makeText(this, R.string.sso_failed, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            SupabaseAuth.saveExternalSession(this@AuthActivity, accessToken, refreshToken)
                .onSuccess { session ->
                    FirestoreRepo.log(session.userId, FirestoreRepo.LOGIN, "Signed in with Google")
                    onAuthenticated()
                }
                .onFailure { Toast.makeText(this@AuthActivity, R.string.sso_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    fun showLogin() = swap(LoginFragment())
    fun showRegister() = swap(RegisterFragment())

    private fun swap(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.authContainer, fragment)
            .commit()
    }

    /** Called by the fragments once sign-in / sign-up succeeds. */
    fun onAuthenticated() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /** Opens the Google sign-in flow in a browser tab. Called by the fragments. */
    fun startGoogleSignIn() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupabaseAuth.googleOAuthUrl())))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sso_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
