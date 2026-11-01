package com.noor.shoplocal.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.data.firestore.FirestoreRepo
import com.noor.shoplocal.databinding.ActivityFeedBinding
import com.noor.shoplocal.ui.BaseActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads the signed-in user's activity feed from Firestore (NoSQL) and lists it.
 * Demonstrates reading from the NoSQL store; writes happen across the app via
 * [FirestoreRepo.log].
 */
class ActivityFeedActivity : BaseActivity() {

    private lateinit var binding: ActivityFeedBinding
    private val adapter = FeedAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.feedList.layoutManager = LinearLayoutManager(this)
        binding.feedList.adapter = adapter

        val session = SupabaseAuth.currentSession(this) ?: run { finish(); return }
        FirestoreRepo.recent(session.userId) { items ->
            runOnUiThread {
                binding.progress.visibility = View.GONE
                adapter.submit(items)
                binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private class FeedAdapter : RecyclerView.Adapter<FeedAdapter.VH>() {
        private val items = mutableListOf<FirestoreRepo.ActivityItem>()
        private val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

        fun submit(list: List<FirestoreRepo.ActivityItem>) {
            items.clear(); items.addAll(list); notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(android.R.id.text1)
            val subtitle: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.action
            val when_ = if (item.at > 0) fmt.format(Date(item.at)) else ""
            holder.subtitle.text = listOf(item.detail, when_).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
}
