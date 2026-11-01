package com.noor.shoplocal

import android.app.Application
import android.util.Log
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.util.AppTheme

/**
 * Application entry point. Applies the saved light/dark theme before any Activity
 * is created so the app opens in the user's chosen appearance.
 */
class ShopLocalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShopRepository.attach(this)
        AppTheme.applyFromPrefs(this)
        // Prepare the notification channel for FCM pushes.
        com.noor.shoplocal.data.fcm.ShopLocalMessagingService.ensureChannel(this)
        Log.i("ShopLocalApp", "ShopLocal started")
    }
}
