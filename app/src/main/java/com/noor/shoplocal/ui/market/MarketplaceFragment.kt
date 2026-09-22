package com.noor.shoplocal.ui.market

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.databinding.FragmentMarketplaceBinding
import kotlinx.coroutines.launch

/**
 * The member marketplace (Facebook-Marketplace style): a grid of items listed by
 * other ShopLocal members. The "Sell" button opens a form to post your own item;
 * tapping a listing opens its detail screen with an embedded location map.
 */
class MarketplaceFragment : Fragment() {

    private var _binding: FragmentMarketplaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ListingAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentMarketplaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ListingAdapter { listing ->
            startActivity(ListingDetailActivity.intent(requireContext(), listing))
        }
        binding.listingGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.listingGrid.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.fabSell.setOnClickListener {
            startActivity(Intent(requireContext(), SellActivity::class.java))
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        load() // refresh so a newly-posted item shows immediately
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val items = ShopRepository.listings()
            if (_binding == null) return@launch
            adapter.submitList(items)
            binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
