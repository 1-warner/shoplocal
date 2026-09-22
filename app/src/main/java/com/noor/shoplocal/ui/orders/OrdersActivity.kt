package com.noor.shoplocal.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Order
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivityOrdersBinding
import com.noor.shoplocal.databinding.ItemOrderBinding
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.ui.home.ProductAdapter
import kotlinx.coroutines.launch

/** Read-only order history, loaded from the REST API. */
class OrdersActivity : BaseActivity() {

    private lateinit var binding: ActivityOrdersBinding
    private val adapter = OrderAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.orderList.layoutManager = LinearLayoutManager(this)
        binding.orderList.adapter = adapter

        adapter.onCancel = { order -> confirmCancel(order) }
        loadOrders()
    }

    private fun loadOrders() {
        val session = SupabaseAuth.currentSession(this) ?: run { finish(); return }
        lifecycleScope.launch {
            val orders = ShopRepository.orders(session)
            adapter.submit(orders)
            binding.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmCancel(order: Order) {
        val session = SupabaseAuth.currentSession(this) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cancel_order)
            .setMessage(R.string.cancel_order_confirm)
            .setPositiveButton(R.string.cancel_order) { _, _ ->
                lifecycleScope.launch {
                    ShopRepository.cancelOrder(session, order.id)
                    loadOrders()
                }
            }
            .setNegativeButton(R.string.back, null)
            .show()
    }

    /** Nested adapter — orders are simple enough to keep the adapter in-file. */
    class OrderAdapter : RecyclerView.Adapter<OrderAdapter.VH>() {
        private val items = mutableListOf<Order>()
        var onCancel: ((Order) -> Unit)? = null

        fun submit(orders: List<Order>) {
            items.clear(); items.addAll(orders); notifyDataSetChanged()
        }

        inner class VH(val b: ItemOrderBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val o = items[position]
            val ctx = holder.itemView.context
            holder.b.orderId.text = ctx.getString(R.string.order_ref, o.id.take(8).uppercase())
            holder.b.orderTotal.text = ProductAdapter.rand(o.total)
            holder.b.orderStatus.text = o.status.replaceFirstChar { it.uppercase() }
            holder.b.orderPoints.text = ctx.getString(R.string.points_earned, o.pointsEarned)
            holder.b.orderDate.text = o.createdAt.take(10)

            // A pending/processing order can still be cancelled.
            val cancellable = o.status.lowercase() in listOf("pending", "processing")
            holder.b.btnCancel.visibility = if (cancellable) View.VISIBLE else View.GONE
            holder.b.btnCancel.setOnClickListener { onCancel?.invoke(o) }
        }
    }
}
