package com.noor.shoplocal.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.noor.shoplocal.R
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.ui.AuthActivity
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Landing point for the `shoplocal://auth-callback` deep link that closes a
 * Google / Apple sign-in (see [SupabaseAuth.beginOAuth]).
 *
 * The browser hands us a one-time authorization code; we redeem it for a session
 * and then route onward — to the shop on success, or back to the login screen
 * with the reason on failure. The brand splash layout is reused so the moment
 * between the browser closing and the shop opening is not a blank screen.
 */
class OAuthCallbackActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handle(intent?.data)
    }

    /** `singleTask` means a second callback arrives here rather than in a new instance. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handle(intent?.data)
    }

    private fun handle(data: Uri?) {
        if (data == null) {
            finishTo(AuthActivity::class.java, getString(R.string.oauth_failed))
            return
        }
        lifecycleScope.launch {
            SupabaseAuth.completeOAuth(this@OAuthCallbackActivity, data)
                .onSuccess {
                    Log.i("OAuthCallback", "Social sign-in complete for ${it.email}")
                    finishTo(MainActivity::class.java, null)
                }
                .onFailure {
                    finishTo(AuthActivity::class.java, it.message ?: getString(R.string.oauth_failed))
                }
        }
    }

    private fun finishTo(target: Class<*>, error: String?) {
        val next = Intent(this, target)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (error != null) next.putExtra(AuthActivity.EXTRA_AUTH_ERROR, error)
        startActivity(next)
        finish()
    }
}
