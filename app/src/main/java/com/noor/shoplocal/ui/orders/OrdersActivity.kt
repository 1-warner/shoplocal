package com.noor.shoplocal.ui.orders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        val session = SupabaseAuth.currentSession(this) ?: run { finish(); return }
        lifecycleScope.launch {
            val orders = ShopRepository.orders(session)
            adapter.submit(orders)
            binding.emptyState.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Nested adapter — orders are simple enough to keep the adapter in-file. */
    class OrderAdapter : RecyclerView.Adapter<OrderAdapter.VH>() {
        private val items = mutableListOf<Order>()

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
        }
    }
}
