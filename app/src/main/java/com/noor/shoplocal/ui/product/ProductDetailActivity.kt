package com.noor.shoplocal.ui.product

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Product
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivityProductDetailBinding
import com.noor.shoplocal.databinding.DialogAddReviewBinding
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.ui.home.ProductAdapter
import kotlinx.coroutines.launch

/**
 * Full product view: photo, price, the artisan's story, and the community reviews.
 * Users can add the item to their cart or wishlist and post their own review — all
 * of which go through the REST API.
 */
class ProductDetailActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PRODUCT = "extra_product"
        fun intent(context: Context, product: Product): Intent =
            Intent(context, ProductDetailActivity::class.java).putExtra(EXTRA_PRODUCT, product)
    }

    private lateinit var binding: ActivityProductDetailBinding
    private lateinit var product: Product
    private val reviewAdapter = ReviewAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        product = intent.getParcelableExtra(EXTRA_PRODUCT)
            ?: run { finish(); return }

        bindProduct()

        binding.reviewList.layoutManager = LinearLayoutManager(this)
        binding.reviewList.adapter = reviewAdapter
        binding.reviewList.isNestedScrollingEnabled = false

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAddCart.setOnClickListener { addToCart() }
        binding.btnAddWishlist.setOnClickListener { addToWishlist() }
        binding.btnWriteReview.setOnClickListener { showReviewDialog() }

        loadReviews()
    }

    private fun bindProduct() {
        binding.toolbar.title = product.name
        binding.productName.text = product.name
        binding.price.text = ProductAdapter.rand(product.effectivePrice)
        binding.description.text = product.description
        binding.sellerName.text = product.sellerName
        binding.sellerStory.text = product.sellerStory ?: ""
        binding.verifiedBadge.visibility = if (product.sellerVerified) View.VISIBLE else View.GONE
        binding.origin.text = getString(R.string.origin_label, product.origin)
        binding.image.load(product.imageUrl) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
        if (product.isOnSale) {
            binding.oldPrice.visibility = View.VISIBLE
            binding.oldPrice.text = ProductAdapter.rand(product.price)
            binding.oldPrice.paintFlags =
                binding.oldPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            binding.oldPrice.visibility = View.GONE
        }
    }

    private fun addToCart() {
        val session = SupabaseAuth.currentSession(this) ?: return
        lifecycleScope.launch {
            // Look up any existing quantity so "Add" increments rather than resets.
            val existing = ShopRepository.cart(session).firstOrNull { it.product.id == product.id }
            val newQty = (existing?.quantity ?: 0) + 1
            ShopRepository.setCartQuantity(session, product.id, newQty)
                .onSuccess { snack(getString(R.string.added_to_cart)) }
                .onFailure { snack(it.message ?: getString(R.string.error_generic)) }
        }
    }

    private fun addToWishlist() {
        val session = SupabaseAuth.currentSession(this) ?: return
        lifecycleScope.launch {
            ShopRepository.addToWishlist(session, product.id)
                .onSuccess { snack(getString(R.string.added_to_wishlist)) }
                .onFailure { snack(it.message ?: getString(R.string.error_generic)) }
        }
    }

    private fun loadReviews() {
        lifecycleScope.launch {
            val reviews = ShopRepository.reviews(product.id)
            reviewAdapter.submit(reviews)
            binding.noReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            binding.ratingSummary.text =
                if (product.ratingCount > 0) "★ %.1f · %d".format(product.ratingAvg, product.ratingCount)
                else getString(R.string.no_reviews_yet)
        }
    }

    private fun showReviewDialog() {
        val session = SupabaseAuth.currentSession(this) ?: return
        val dialogBinding = DialogAddReviewBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.write_review)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.submit) { _, _ ->
                val rating = dialogBinding.ratingBar.rating.toInt().coerceIn(1, 5)
                val body = dialogBinding.reviewBody.text?.toString()?.trim().orEmpty()
                lifecycleScope.launch {
                    ShopRepository.addReview(session, product.id, rating, body)
                        .onSuccess {
                            snack(getString(R.string.review_thanks))
                            loadReviews()
                        }
                        .onFailure { snack(it.message ?: getString(R.string.error_generic)) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
