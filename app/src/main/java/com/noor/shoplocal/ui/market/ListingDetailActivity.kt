package com.noor.shoplocal.ui.market

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import coil.load
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Listing
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivityListingDetailBinding
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.ui.home.ProductAdapter
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/**
 * Marketplace listing detail. Shows the item, an embedded map of where it is
 * (the "where the seller is selling from" view), a Contact-seller/share action,
 * and — if you own the listing — a delete button.
 */
class ListingDetailActivity : BaseActivity() {

    companion object {
        private const val EXTRA = "extra_listing"
        fun intent(context: Context, listing: Listing): Intent =
            Intent(context, ListingDetailActivity::class.java).putExtra(EXTRA, listing)
    }

    private lateinit var binding: ActivityListingDetailBinding
    private lateinit var listing: Listing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        binding = ActivityListingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        listing = intent.getParcelableExtra(EXTRA) ?: run { finish(); return }

        binding.toolbar.setNavigationOnClickListener { finish() }
        bind()
        setupMap()
        wireActions()
    }

    private fun bind() {
        binding.title.text = listing.title
        binding.price.text = ProductAdapter.rand(listing.price)
        binding.meta.text = getString(R.string.listing_meta, listing.category, listing.sellerName)
        binding.description.text = listing.description
        binding.locationName.text = listing.location
        binding.image.load(listing.imageUrl) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
    }

    private fun setupMap() {
        if (!listing.hasLocation) {
            binding.map.visibility = View.GONE
            return
        }
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        val point = GeoPoint(listing.lat!!, listing.lng!!)
        binding.map.controller.setZoom(11.0)
        binding.map.controller.setCenter(point)
        val marker = Marker(binding.map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = listing.location
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_map_pin)
        binding.map.overlays.add(marker)
    }

    private fun wireActions() {
        binding.btnContact.setOnClickListener {
            // Facebook-Marketplace-style "contact / share" via the Android share sheet.
            val text = getString(
                R.string.contact_share_text,
                listing.title, ProductAdapter.rand(listing.price), listing.location, listing.sellerName
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + ": " + listing.title)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(share, getString(R.string.contact_seller)))
        }

        // Show delete only to the owner of the listing.
        val session = SupabaseAuth.currentSession(this)
        if (session != null && session.userId == listing.sellerUserId) {
            binding.btnDelete.visibility = View.VISIBLE
            binding.btnDelete.setOnClickListener { confirmDelete(session) }
        }
    }

    private fun confirmDelete(session: SupabaseAuth.Session) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_listing)
            .setMessage(R.string.delete_listing_confirm)
            .setPositiveButton(R.string.delete_listing) { _, _ ->
                lifecycleScope.launch {
                    ShopRepository.deleteListing(session, listing.id)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() { super.onResume(); if (listing.hasLocation) binding.map.onResume() }
    override fun onPause() { super.onPause(); if (::listing.isInitialized && listing.hasLocation) binding.map.onPause() }
}
