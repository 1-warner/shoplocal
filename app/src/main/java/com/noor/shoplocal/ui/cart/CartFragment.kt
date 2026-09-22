package com.noor.shoplocal.ui.cart

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noor.shoplocal.R
import com.noor.shoplocal.data.CartLine
import com.noor.shoplocal.data.Pricing
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.FragmentCartBinding
import com.noor.shoplocal.ui.home.ProductAdapter
import kotlinx.coroutines.launch

/**
 * The basket. Lists cart lines, keeps the running subtotal / delivery / total in
 * sync with the shared [Pricing] rules, requires a delivery location before
 * ordering, and checks out through the custom `place_order` REST endpoint — which
 * also awards Local Points.
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CartAdapter
    private var lines: List<CartLine> = emptyList()
    private var isSubscriber = false
    private var deliveryAddress: String = ""   // the user's saved delivery location

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CartAdapter(
            onQuantityChange = { line, qty -> changeQuantity(line, qty) },
            onRemove = { line -> changeQuantity(line, 0) }
        )
        binding.cartList.layoutManager = LinearLayoutManager(requireContext())
        binding.cartList.adapter = adapter
        binding.btnCheckout.setOnClickListener { promptCheckout() }
        binding.locationBanner.setOnClickListener { showAddressDialog(afterSave = null) }
        binding.locationSet.setOnClickListener { showAddressDialog(afterSave = null) }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            lines = ShopRepository.cart(session)
            val profile = ShopRepository.profile(session)
            isSubscriber = profile?.isSubscriber ?: false
            deliveryAddress = profile?.address?.trim().orEmpty()
            if (_binding == null) return@launch
            adapter.submit(lines)
            renderTotals()
            renderLocation()
            (activity as? com.noor.shoplocal.ui.MainActivity)?.refreshCartBadge()
        }
    }

    /** Shows the location warning or the "delivering to …" confirmation banner. */
    private fun renderLocation() {
        val hasLocation = deliveryAddress.isNotBlank()
        binding.locationBanner.visibility = if (hasLocation) View.GONE else View.VISIBLE
        binding.locationSet.visibility = if (hasLocation) View.VISIBLE else View.GONE
        if (hasLocation) {
            binding.locationSet.text = getString(R.string.location_set_to, deliveryAddress)
        }
    }

    private fun renderTotals() {
        val subtotal = Pricing.subtotal(lines)
        val delivery = Pricing.deliveryFee(subtotal, isSubscriber)
        val total = Pricing.total(subtotal, isSubscriber)

        binding.subtotalValue.text = ProductAdapter.rand(subtotal)
        binding.deliveryValue.text =
            if (delivery == 0.0 && subtotal > 0) getString(R.string.free) else ProductAdapter.rand(delivery)
        binding.totalValue.text = ProductAdapter.rand(total)
        binding.pointsPreview.text = getString(R.string.points_preview, Pricing.loyaltyPoints(subtotal))

        val empty = lines.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.checkoutPanel.visibility = if (empty) View.GONE else View.VISIBLE
        binding.btnCheckout.isEnabled = !empty
    }

    private fun changeQuantity(line: CartLine, newQty: Int) {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            ShopRepository.setCartQuantity(session, line.product.id, newQty)
                .onFailure { Log.w("CartFragment", "quantity update failed: ${it.message}") }
            load()
        }
    }

    /** Prompts for / edits the delivery address and saves it to the profile. */
    private fun showAddressDialog(afterSave: (() -> Unit)?) {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.delivery_address_hint)
            setText(deliveryAddress)
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delivery_address_title)
            .setView(input)
            .setPositiveButton(R.string.save_location) { _, _ ->
                val entered = input.text.toString().trim()
                if (entered.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(), R.string.delivery_address_required,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    ShopRepository.updateAddress(session, entered)
                    deliveryAddress = entered
                    if (_binding != null) renderLocation()
                    afterSave?.invoke()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptCheckout() {
        // Require a delivery location before an order can be placed.
        if (deliveryAddress.isBlank()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delivery_address_title)
                .setMessage(R.string.need_location_to_order)
                .setPositiveButton(R.string.set_location) { _, _ ->
                    showAddressDialog(afterSave = { confirmOrderDialog() })
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        confirmOrderDialog()
    }

    /** Final confirmation once a location exists. */
    private fun confirmOrderDialog() {
        if (deliveryAddress.isBlank()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.checkout)
            .setMessage(getString(R.string.location_set_to, deliveryAddress))
            .setPositiveButton(R.string.place_order) { _, _ -> placeOrder(deliveryAddress) }
            .setNeutralButton(R.string.set_location) { _, _ -> showAddressDialog(afterSave = null) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun placeOrder(address: String) {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        val delivery = Pricing.deliveryFee(Pricing.subtotal(lines), isSubscriber)
        binding.btnCheckout.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ShopRepository.placeOrder(session, address, delivery)
            if (_binding == null) return@launch
            result.onSuccess { order ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.order_placed)
                    .setMessage(
                        getString(
                            R.string.order_placed_body,
                            ProductAdapter.rand(order.total),
                            order.pointsEarned
                        )
                    )
                    .setPositiveButton(R.string.ok, null)
                    .show()
                load()
            }.onFailure {
                binding.btnCheckout.isEnabled = true
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error_generic)
                    .setMessage(it.message)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
