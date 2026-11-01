package com.noor.shoplocal.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Pricing
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivityMainBinding
import com.noor.shoplocal.ui.cart.CartFragment
import com.noor.shoplocal.ui.home.HomeFragment
import com.noor.shoplocal.ui.market.MarketplaceFragment
import com.noor.shoplocal.ui.profile.ProfileFragment
import com.noor.shoplocal.ui.wishlist.WishlistFragment
import kotlinx.coroutines.launch

/**
 * The signed-in shell. A BottomNavigationView switches between the four main
 * sections: Home (browse the catalogue), Wishlist, Cart and Profile.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeAskNotificationPermission()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_market -> MarketplaceFragment()
                R.id.nav_wishlist -> WishlistFragment()
                R.id.nav_cart -> CartFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            show(fragment)
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    override fun onResume() {
        super.onResume()
        syncThenRefresh()
    }

    /** Replays any actions queued while offline, then updates the cart badge. */
    private fun syncThenRefresh() {
        val session = SupabaseAuth.currentSession(this) ?: return
        lifecycleScope.launch {
            val synced = com.noor.shoplocal.data.offline.SyncManager.syncPending(this@MainActivity, session)
            if (synced > 0) {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root, getString(R.string.synced_offline, synced),
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                ).show()
            }
            refreshCartBadge()
        }
    }

    /** On Android 13+ ask once for permission to show push notifications. */
    private fun maybeAskNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Shows the live item count as a badge on the Cart tab. */
    fun refreshCartBadge() {
        val session = SupabaseAuth.currentSession(this) ?: return
        lifecycleScope.launch {
            val count = Pricing.itemCount(ShopRepository.cart(session))
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_cart)
            badge.isVisible = count > 0
            badge.number = count
        }
    }

    /** Refresh the cart when returning to it (e.g. after adding items elsewhere). */
    fun goToCart() {
        binding.bottomNav.selectedItemId = R.id.nav_cart
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }
}
