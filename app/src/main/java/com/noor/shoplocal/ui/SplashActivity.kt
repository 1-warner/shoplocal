package com.noor.shoplocal.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.noor.shoplocal.data.SupabaseAuth

/**
 * Brand splash. After a short beat it routes the user straight to the shop if a
 * cached session exists, otherwise to the login / registration flow.
 */
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.noor.shoplocal.R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val signedIn = SupabaseAuth.currentSession(this) != null
            val next = if (signedIn) MainActivity::class.java else AuthActivity::class.java
            startActivity(Intent(this, next))
            finish()
        }, 1200)
    }
}
