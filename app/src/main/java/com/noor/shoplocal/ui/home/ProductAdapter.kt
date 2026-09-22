package com.noor.shoplocal.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Product
import com.noor.shoplocal.databinding.ItemProductBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * Grid adapter for product cards. Shows the photo (loaded from the API with Coil),
 * name, artisan, price (with a struck-through original when on sale) and rating.
 * A tap opens the product detail screen.
 */
class ProductAdapter(
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.VH>(DIFF) {

    // Optional user location — when set, product cards show distance to the maker.
    var userLat: Double? = null
    var userLng: Double? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Product>() {
            override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
            override fun areContentsTheSame(a: Product, b: Product) = a == b
        }

        /** Rand currency formatting shared across the app (e.g. "R 349.00"). */
        fun rand(amount: Double): String {
            val nf = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            return nf.format(amount)
        }
    }

    inner class VH(val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val b = holder.b
        b.productName.text = p.name
        // Show distance to the maker when we know both locations, else just the name.
        val uLat = userLat; val uLng = userLng
        b.sellerName.text = if (uLat != null && uLng != null && p.sellerLat != null && p.sellerLng != null) {
            val km = com.noor.shoplocal.data.Geo.distanceKm(uLat, uLng, p.sellerLat, p.sellerLng).toInt()
            "${p.sellerName} · ${km} km"
        } else {
            p.sellerName
        }
        b.price.text = rand(p.effectivePrice)

        if (p.isOnSale) {
            b.oldPrice.visibility = android.view.View.VISIBLE
            b.oldPrice.text = rand(p.price)
            b.oldPrice.paintFlags = b.oldPrice.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            b.saleBadge.visibility = android.view.View.VISIBLE
        } else {
            b.oldPrice.visibility = android.view.View.GONE
            b.saleBadge.visibility = android.view.View.GONE
        }

        b.rating.text = if (p.ratingCount > 0)
            "★ %.1f (%d)".format(p.ratingAvg, p.ratingCount)
        else
            holder.itemView.context.getString(R.string.no_reviews_yet)

        b.productImage.load(p.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }

        b.root.setOnClickListener { onClick(p) }
    }
}
