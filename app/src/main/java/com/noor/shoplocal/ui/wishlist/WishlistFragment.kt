package com.noor.shoplocal.ui.wishlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.FragmentListBinding
import com.noor.shoplocal.ui.home.ProductAdapter
import com.noor.shoplocal.ui.product.ProductDetailActivity
import kotlinx.coroutines.launch

/**
 * The user's saved items (deferred purchases). Loads the wishlist from the API and
 * reuses the same product-card grid as Home. Tapping an item opens its detail
 * screen where it can be moved to the cart or removed.
 */
class WishlistFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.title.text = getString(com.noor.shoplocal.R.string.tab_wishlist)
        adapter = ProductAdapter { product ->
            startActivity(ProductDetailActivity.intent(requireContext(), product))
        }
        binding.list.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val items = ShopRepository.wishlist(session)
            if (_binding == null) return@launch
            adapter.submitList(items)
            binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyState.text = getString(com.noor.shoplocal.R.string.wishlist_empty)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
