package com.noor.shoplocal.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.data.Validators
import com.noor.shoplocal.databinding.FragmentLoginBinding
import com.noor.shoplocal.ui.AuthActivity
import kotlinx.coroutines.launch

/** Email/password login. Validates input locally, then authenticates via the API. */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linkRegister.setOnClickListener { (activity as AuthActivity).showRegister() }
        binding.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        // Guard invalid input so we never fire a doomed request or crash.
        if (!Validators.isValidEmail(email)) {
            binding.inputEmailLayout.error = getString(com.noor.shoplocal.R.string.error_email)
            return
        }
        binding.inputEmailLayout.error = null
        if (password.isEmpty()) {
            binding.inputPasswordLayout.error = getString(com.noor.shoplocal.R.string.error_password_empty)
            return
        }
        binding.inputPasswordLayout.error = null

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SupabaseAuth.signIn(requireContext(), email, password)
            setLoading(false)
            result.onSuccess {
                Log.i("LoginFragment", "Login OK for $email")
                (activity as AuthActivity).onAuthenticated()
            }.onFailure {
                binding.inputPasswordLayout.error = it.message ?: getString(com.noor.shoplocal.R.string.error_generic)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (_binding == null) return
        binding.btnLogin.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
