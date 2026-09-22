package com.noor.shoplocal.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Prefs
import com.noor.shoplocal.data.Product
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.FragmentHomeBinding
import com.noor.shoplocal.ui.product.ProductDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The shopfront: search, category chips, sort (newest / price / rating), a deals
 * toggle, a "recently viewed" strip, and a 2-column grid of products loaded live
 * from the REST API. Pull-to-refresh re-fetches the catalogue.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductAdapter
    private lateinit var recentAdapter: MiniProductAdapter
    private var allProducts: List<Product> = emptyList()
    private var selectedCategory = "All"
    private var sort = ShopRepository.Sort.NEWEST
    private var dealsOnly = false
    private var area = ShopRepository.Area.ALL
    private var userLat: Double? = null
    private var userLng: Double? = null
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductAdapter { openProduct(it) }
        binding.productGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.productGrid.adapter = adapter
        binding.productGrid.isNestedScrollingEnabled = false

        recentAdapter = MiniProductAdapter { openProduct(it) }
        binding.recentList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recentList.adapter = recentAdapter

        binding.swipeRefresh.setOnRefreshListener { load() }

        binding.btnArtisanMap.setOnClickListener {
            startActivity(android.content.Intent(requireContext(),
                com.noor.shoplocal.ui.map.ArtisanMapActivity::class.java))
        }

        binding.btnSort.setOnClickListener { showSortMenu() }
        binding.chipDeals.setOnCheckedChangeListener { _, checked ->
            dealsOnly = checked
            applyFilters()
        }
        binding.areaChips.setOnCheckedStateChangeListener { _, checkedIds ->
            area = when (checkedIds.firstOrNull()) {
                R.id.chipAreaNear -> ShopRepository.Area.NEARBY
                R.id.chipAreaElse -> ShopRepository.Area.ELSEWHERE
                else -> ShopRepository.Area.ALL
            }
            applyFilters()
        }

        binding.searchInput.doAfterTextChanged {
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(250)
                applyFilters()
            }
        }

        load()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the recently-viewed strip when returning from a product.
        if (allProducts.isNotEmpty()) showRecentlyViewed()
    }

    private fun openProduct(product: Product) {
        startActivity(ProductDetailActivity.intent(requireContext(), product))
    }

    private fun load() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allProducts = ShopRepository.products(requireContext())
                // Load the user's saved area so we can show "in your area" + distances.
                SupabaseAuth.currentSession(requireContext())?.let { session ->
                    val profile = ShopRepository.profile(session)
                    userLat = profile?.lat
                    userLng = profile?.lng
                    adapter.userLat = userLat
                    adapter.userLng = userLng
                }
                buildCategoryChips(ShopRepository.categoriesFrom(allProducts))
                applyFilters()
                showRecentlyViewed()
            } catch (e: Exception) {
                Log.w("HomeFragment", "Failed to load catalogue: ${e.message}")
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.text = getString(R.string.error_offline)
            } finally {
                if (_binding != null) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSort)
        popup.menu.add(0, 0, 0, getString(R.string.sort_newest))
        popup.menu.add(0, 1, 1, getString(R.string.sort_price_low))
        popup.menu.add(0, 2, 2, getString(R.string.sort_price_high))
        popup.menu.add(0, 3, 3, getString(R.string.sort_rating))
        popup.setOnMenuItemClickListener { item ->
            sort = when (item.itemId) {
                1 -> ShopRepository.Sort.PRICE_LOW
                2 -> ShopRepository.Sort.PRICE_HIGH
                3 -> ShopRepository.Sort.RATING
                else -> ShopRepository.Sort.NEWEST
            }
            binding.btnSort.text = item.title
            applyFilters()
            true
        }
        popup.show()
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
        var filtered = ShopRepository.filterAndSort(allProducts, selectedCategory, query, sort, dealsOnly)
        filtered = ShopRepository.filterByArea(filtered, userLat, userLng, area)
        adapter.submitList(filtered)
        binding.resultCount.text = resources.getQuantityStringSafe(filtered.size)

        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) {
            // A helpful message when "in my area" is chosen but no location is set.
            binding.emptyState.text = if (area != ShopRepository.Area.ALL && userLat == null)
                getString(R.string.area_set_location)
            else
                getString(R.string.no_products)
        }
    }

    private fun showRecentlyViewed() {
        val ids = Prefs.recentlyViewed(requireContext())
        val recents = ids.mapNotNull { id -> allProducts.firstOrNull { it.id == id } }
        recentAdapter.submit(recents)
        binding.recentSection.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
    }

    // Small helper to show "N items" without a plurals resource.
    private fun android.content.res.Resources.getQuantityStringSafe(count: Int): String =
        if (count == 1) "1 item" else "$count items"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
