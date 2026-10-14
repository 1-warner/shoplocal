package com.noor.shoplocal.ui

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.R
import com.noor.shoplocal.databinding.ActivityAuthBinding
import com.noor.shoplocal.ui.auth.LoginFragment
import com.noor.shoplocal.ui.auth.RegisterFragment

/**
 * Hosts the Login and Register fragments in a single container. Fragments call
 * [showRegister] / [showLogin] to swap, and [onAuthenticated] once a session exists.
 *
 * `OAuthCallbackActivity` also routes back here with [EXTRA_AUTH_ERROR] when a
 * Google / Apple sign-in does not complete, so the reason is shown on the screen
 * the user is returned to instead of being swallowed.
 */
class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) showLogin()
        showAuthError(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showAuthError(intent)
    }

    private fun showAuthError(intent: Intent?) {
        val message = intent?.getStringExtra(EXTRA_AUTH_ERROR) ?: return
        intent.removeExtra(EXTRA_AUTH_ERROR) // don't repeat it on rotation
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
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

    companion object {
        /** Error text handed back by a failed social sign-in. */
        const val EXTRA_AUTH_ERROR = "auth_error"
    }
}
