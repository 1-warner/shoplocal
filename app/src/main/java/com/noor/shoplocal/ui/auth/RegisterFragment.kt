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
import com.noor.shoplocal.databinding.FragmentRegisterBinding
import com.noor.shoplocal.ui.AuthActivity
import kotlinx.coroutines.launch

/**
 * Account creation. Enforces the password policy from Validators before sending
 * the request; the password itself is bcrypt-hashed on the server.
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.linkLogin.setOnClickListener { (activity as AuthActivity).showLogin() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
    }

    private fun attemptRegister() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val confirm = binding.inputConfirm.text?.toString().orEmpty()

        // One validator call decides whether the whole form is acceptable.
        val error = Validators.registrationError(name, email, password, confirm)
        if (error != null) {
            binding.errorText.text = error
            binding.errorText.visibility = View.VISIBLE
            return
        }
        binding.errorText.visibility = View.GONE

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SupabaseAuth.signUp(requireContext(), name, email, password)
            setLoading(false)
            result.onSuccess {
                Log.i("RegisterFragment", "Registered $email")
                (activity as AuthActivity).onAuthenticated()
            }.onFailure {
                binding.errorText.text = it.message ?: getString(com.noor.shoplocal.R.string.error_generic)
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (_binding == null) return
        binding.btnRegister.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
