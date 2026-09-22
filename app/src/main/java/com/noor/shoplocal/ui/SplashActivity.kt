package com.noor.shoplocal.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.noor.shoplocal.data.SupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Brand splash. If a cached session exists it proactively refreshes the access
 * token (they expire after ~1 hour) so the shop is immediately usable, then routes
 * to the shop; otherwise it opens the login / registration flow.
 */
class SplashActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.noor.shoplocal.R.layout.activity_splash)

        lifecycleScope.launch {
            var signedIn = SupabaseAuth.currentSession(this@SplashActivity) != null
            if (signedIn) {
                // Best-effort token refresh so authenticated calls work right away.
                val refreshed = withContext(Dispatchers.IO) {
                    SupabaseAuth.refreshBlocking(this@SplashActivity)
                }
                // An old session that can't be refreshed and has expired must log in
                // again (e.g. a session created before refresh tokens were stored).
                if (refreshed == null && SupabaseAuth.accessTokenExpired(this@SplashActivity)) {
                    SupabaseAuth.signOut(this@SplashActivity)
                    signedIn = false
                }
            }
            delay(900)
            val next = if (signedIn) MainActivity::class.java else AuthActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }
}
