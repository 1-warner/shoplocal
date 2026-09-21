package com.noor.shoplocal.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.noor.shoplocal.R
import com.noor.shoplocal.data.CartLine
import com.noor.shoplocal.databinding.ItemCartBinding
import com.noor.shoplocal.ui.home.ProductAdapter

/**
 * Cart line adapter with +/- quantity steppers and a remove button. Quantity
 * changes are reported up to the fragment, which persists them to the API and
 * recomputes the totals.
 */
class CartAdapter(
    private val onQuantityChange: (CartLine, Int) -> Unit,
    private val onRemove: (CartLine) -> Unit
) : RecyclerView.Adapter<CartAdapter.VH>() {

    private val lines = mutableListOf<CartLine>()

    fun submit(newLines: List<CartLine>) {
        lines.clear()
        lines.addAll(newLines)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemCartBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = lines[position]
        val b = holder.b
        b.name.text = line.product.name
        b.price.text = ProductAdapter.rand(line.product.effectivePrice)
        b.quantity.text = line.quantity.toString()
        b.lineTotal.text = ProductAdapter.rand(line.product.effectivePrice * line.quantity)
        b.image.load(line.product.imageUrl) {
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
        }

        b.btnIncrease.setOnClickListener { onQuantityChange(line, line.quantity + 1) }
        b.btnDecrease.setOnClickListener {
            if (line.quantity > 1) onQuantityChange(line, line.quantity - 1)
        }
        b.btnRemove.setOnClickListener { onRemove(line) }
    }
}
