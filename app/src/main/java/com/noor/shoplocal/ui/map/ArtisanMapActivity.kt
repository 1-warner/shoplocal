package com.noor.shoplocal.ui.map

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Seller
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.databinding.ActivityArtisanMapBinding
import com.noor.shoplocal.databinding.SheetSellerProductsBinding
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.ui.home.ProductAdapter
import com.noor.shoplocal.ui.product.ProductDetailActivity
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/**
 * The artisan map. Every verified local seller is dropped as a pin on an
 * OpenStreetMap view (via osmdroid — no API key). Tapping a pin opens a bottom
 * sheet with the maker's story and their products, each of which links straight
 * to the product detail screen. Reinforces the "shop local" concept by literally
 * showing where in South Africa each maker is based.
 */
class ArtisanMapActivity : BaseActivity() {

    private lateinit var binding: ActivityArtisanMapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid needs a user-agent set before the map view is created.
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityArtisanMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        // Centre on South Africa with a country-level zoom.
        binding.map.controller.setZoom(5.3)
        binding.map.controller.setCenter(GeoPoint(-29.0, 25.0))

        loadSellers()
    }

    private fun loadSellers() {
        lifecycleScope.launch {
            val sellers = ShopRepository.sellers()
            sellers.forEach { addPin(it) }
            binding.map.invalidate()
        }
    }

    private fun addPin(seller: Seller) {
        val marker = Marker(binding.map)
        marker.position = GeoPoint(seller.lat, seller.lng)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = seller.name
        marker.snippet = seller.location
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_map_pin)
        marker.relatedObject = seller
        marker.setOnMarkerClickListener { m, _ ->
            (m.relatedObject as? Seller)?.let { showSellerSheet(it) }
            true
        }
        binding.map.overlays.add(marker)
    }

    /** Bottom sheet with the maker's story and their products. */
    private fun showSellerSheet(seller: Seller) {
        val sheetBinding = SheetSellerProductsBinding.inflate(layoutInflater)
        sheetBinding.sheetSellerName.text = seller.name
        sheetBinding.sheetLocation.text = seller.location
        sheetBinding.sheetStory.text = seller.story ?: ""
        sheetBinding.sheetVerified.visibility = if (seller.verified) View.VISIBLE else View.GONE

        val dialog = BottomSheetDialog(this)
        val adapter = ProductAdapter { product ->
            startActivity(ProductDetailActivity.intent(this, product))
            dialog.dismiss()
        }
        sheetBinding.sheetProducts.layoutManager = GridLayoutManager(this, 2)
        sheetBinding.sheetProducts.adapter = adapter

        dialog.setContentView(sheetBinding.root)
        dialog.show()

        // Load this seller's products from the API into the sheet.
        lifecycleScope.launch {
            val products = ShopRepository.productsBySeller(seller.name)
            adapter.submitList(products)
        }
    }

    // osmdroid map lifecycle hooks.
    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
}
