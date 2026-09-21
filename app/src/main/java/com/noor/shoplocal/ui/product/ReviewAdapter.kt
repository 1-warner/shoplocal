package com.noor.shoplocal.ui.product

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noor.shoplocal.data.Review
import com.noor.shoplocal.databinding.ItemReviewBinding

/** Simple list adapter for the community reviews under a product. */
class ReviewAdapter : RecyclerView.Adapter<ReviewAdapter.VH>() {

    private val items = mutableListOf<Review>()

    fun submit(reviews: List<Review>) {
        items.clear()
        items.addAll(reviews)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemReviewBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.b.reviewer.text = r.userName
        holder.b.stars.text = "★".repeat(r.rating.coerceIn(0, 5)) + "☆".repeat((5 - r.rating).coerceIn(0, 5))
        holder.b.body.text = r.body
    }
}
