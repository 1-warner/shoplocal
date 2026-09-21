package com.noor.shoplocal.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.noor.shoplocal.R
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.FragmentProfileBinding
import com.noor.shoplocal.ui.AuthActivity
import com.noor.shoplocal.ui.orders.OrdersActivity
import com.noor.shoplocal.ui.settings.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Account hub: shows the user's name, email and Local Points balance, with links
 * to Settings, order history, and sign-out.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session = SupabaseAuth.currentSession(requireContext())
        binding.name.text = session?.name?.ifBlank { session.email.substringBefore("@") } ?: "Shopper"
        binding.email.text = session?.email ?: ""

        binding.rowSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.rowOrders.setOnClickListener {
            startActivity(Intent(requireContext(), OrdersActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    override fun onResume() {
        super.onResume()
        loadPoints()
    }

    private fun loadPoints() {
        val session = SupabaseAuth.currentSession(requireContext()) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = ShopRepository.profile(session)
            if (_binding == null) return@launch
            binding.pointsValue.text = (profile?.loyaltyPoints ?: 0).toString()
            profile?.name?.takeIf { it.isNotBlank() }?.let { binding.name.text = it }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.logout) { _, _ ->
                SupabaseAuth.signOut(requireContext())
                startActivity(
                    Intent(requireContext(), AuthActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                requireActivity().finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
