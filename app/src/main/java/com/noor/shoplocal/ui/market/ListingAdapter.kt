package com.noor.shoplocal.ui.market

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Listing
import com.noor.shoplocal.databinding.ItemListingBinding
import com.noor.shoplocal.ui.home.ProductAdapter

/** Grid adapter for Facebook-Marketplace-style member listings. */
class ListingAdapter(
    private val onClick: (Listing) -> Unit
) : ListAdapter<Listing, ListingAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Listing>() {
            override fun areItemsTheSame(a: Listing, b: Listing) = a.id == b.id
            override fun areContentsTheSame(a: Listing, b: Listing) = a == b
        }
    }

    inner class VH(val b: ItemListingBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemListingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val listing = getItem(position)
        val b = holder.b
        b.listingTitle.text = listing.title
        b.listingPrice.text = ProductAdapter.rand(listing.price)
        b.listingMeta.text = "${listing.sellerName} · ${listing.location}"
        b.listingImage.load(listing.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
        b.root.setOnClickListener { onClick(listing) }
    }
}
