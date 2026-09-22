package com.noor.shoplocal.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Product
import com.noor.shoplocal.databinding.ItemProductMiniBinding

/** Compact horizontal card used by the "recently viewed" strip on Home. */
class MiniProductAdapter(
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<MiniProductAdapter.VH>() {

    private val items = mutableListOf<Product>()

    fun submit(products: List<Product>) {
        items.clear(); items.addAll(products); notifyDataSetChanged()
    }

    inner class VH(val b: ItemProductMiniBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemProductMiniBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.b.miniName.text = p.name
        holder.b.miniPrice.text = ProductAdapter.rand(p.effectivePrice)
        holder.b.miniImage.load(p.imageUrl) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }
        holder.b.root.setOnClickListener { onClick(p) }
    }
}
