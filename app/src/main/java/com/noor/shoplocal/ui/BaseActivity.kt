package com.noor.shoplocal.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.noor.shoplocal.util.LocaleHelper

/**
 * Every screen extends this so the user's chosen language is applied consistently.
 * attachBaseContext wraps the Context with the selected locale before the layout
 * is inflated.
 */
open class BaseActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
