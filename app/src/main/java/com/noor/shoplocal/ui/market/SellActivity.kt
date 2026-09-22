package com.noor.shoplocal.ui.market

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.R
import com.noor.shoplocal.data.SaCities
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivitySellBinding
import com.noor.shoplocal.ui.BaseActivity
import kotlinx.coroutines.launch

/**
 * "Sell an item" form — the create side of the member marketplace. Validates the
 * input, resolves the chosen city to coordinates (so the listing shows on a map),
 * and posts it to the REST API.
 */
class SellActivity : BaseActivity() {

    private lateinit var binding: ActivitySellBinding

    private val categories = listOf(
        "General", "Jewellery", "Bags", "Homeware", "Decor",
        "Clothing", "Beauty", "Art", "Food", "Electronics", "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.inputCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        )
        binding.inputCategory.setText(categories.first(), false)

        binding.inputCity.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, SaCities.NAMES)
        )
        binding.inputCity.setText(SaCities.NAMES.first(), false)

        binding.btnPost.setOnClickListener { post() }
    }

    private fun post() {
        val session = SupabaseAuth.currentSession(this) ?: run { finish(); return }
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()
        val category = binding.inputCategory.text?.toString()?.ifBlank { "General" } ?: "General"
        val priceText = binding.inputPrice.text?.toString()?.trim().orEmpty()
        val cityName = binding.inputCity.text?.toString().orEmpty()
        val imageUrl = binding.inputImage.text?.toString()?.trim()?.ifBlank { null }

        // Validate before hitting the network so the user never sees a crash.
        val price = priceText.toDoubleOrNull()
        val error = when {
            title.length < 3 -> getString(R.string.sell_error_title)
            price == null || price <= 0.0 -> getString(R.string.sell_error_price)
            SaCities.byName(cityName) == null -> getString(R.string.sell_error_city)
            else -> null
        }
        if (error != null) {
            binding.errorText.text = error
            binding.errorText.visibility = View.VISIBLE
            return
        }
        binding.errorText.visibility = View.GONE

        val city = SaCities.byName(cityName)!!
        setLoading(true)
        lifecycleScope.launch {
            val result = ShopRepository.createListing(
                session = session,
                title = title,
                description = description,
                category = category,
                price = price!!,
                imageUrl = imageUrl,
                location = city.name,
                lat = city.lat,
                lng = city.lng
            )
            setLoading(false)
            result.onSuccess {
                Snackbar.make(binding.root, getString(R.string.listing_posted), Snackbar.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                binding.errorText.text = it.message ?: getString(R.string.error_generic)
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnPost.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
