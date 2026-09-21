package com.noor.shoplocal.ui

import android.content.Intent
import android.os.Bundle
import com.noor.shoplocal.R
import com.noor.shoplocal.databinding.ActivityAuthBinding
import com.noor.shoplocal.ui.auth.LoginFragment
import com.noor.shoplocal.ui.auth.RegisterFragment

/**
 * Hosts the Login and Register fragments in a single container. Fragments call
 * [showRegister] / [showLogin] to swap, and [onAuthenticated] once a session exists.
 */
class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) showLogin()
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
}
