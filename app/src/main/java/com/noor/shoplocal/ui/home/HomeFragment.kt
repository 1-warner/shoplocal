package com.noor.shoplocal.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.noor.shoplocal.data.Product
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.databinding.FragmentHomeBinding
import com.noor.shoplocal.ui.product.ProductDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The shopfront: a search box, category filter chips, and a 2-column grid of
 * products loaded live from the REST API. Pull-to-refresh re-fetches the catalogue.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductAdapter
    private var allProducts: List<Product> = emptyList()
    private var selectedCategory = "All"
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductAdapter { product ->
            startActivity(ProductDetailActivity.intent(requireContext(), product))
        }
        binding.productGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.productGrid.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { load() }

        binding.btnArtisanMap.setOnClickListener {
            startActivity(android.content.Intent(requireContext(),
                com.noor.shoplocal.ui.map.ArtisanMapActivity::class.java))
        }

        binding.searchInput.doAfterTextChanged {
            // Debounce so we filter after the user pauses typing.
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(250)
                applyFilters()
            }
        }

        load()
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allProducts = ShopRepository.products(requireContext())
                buildCategoryChips(ShopRepository.categoriesFrom(allProducts))
                applyFilters()
            } catch (e: Exception) {
                Log.w("HomeFragment", "Failed to load catalogue: ${e.message}")
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(com.noor.shoplocal.R.string.error_offline)
            } finally {
                if (_binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun buildCategoryChips(categories: List<String>) {
        binding.categoryChips.removeAllViews()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = category == selectedCategory
                setOnClickListener {
                    selectedCategory = category
                    applyFilters()
                }
            }
            binding.categoryChips.addView(chip)
        }
    }

    private fun applyFilters() {
        if (_binding == null) return
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        val filtered = allProducts.filter { p ->
            (selectedCategory == "All" || p.category == selectedCategory) &&
                (query.isEmpty() || p.name.contains(query, ignoreCase = true) ||
                    p.category.contains(query, ignoreCase = true))
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) {
            binding.emptyState.text = getString(com.noor.shoplocal.R.string.no_products)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
