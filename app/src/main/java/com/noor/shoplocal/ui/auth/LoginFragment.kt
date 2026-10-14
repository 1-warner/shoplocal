package com.noor.shoplocal.ui.auth

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.data.Validators
import com.noor.shoplocal.databinding.FragmentLoginBinding
import com.noor.shoplocal.ui.AuthActivity
import kotlinx.coroutines.launch

/**
 * Login screen. Offers two routes to a session:
 *
 *  - email + password, validated locally and then checked by the API;
 *  - "Continue with Google / Apple", which hands off to the provider in a Custom
 *    Tab and comes back through the `shoplocal://auth-callback` deep link that
 *    [OAuthCallbackActivity] handles.
 *
 * A Custom Tab is used rather than a WebView on purpose: the user can see the real
 * google.com / apple.com address bar before typing a password, and both providers
 * refuse to authenticate inside an embedded WebView.
 */
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
        binding.btnGoogle.setOnClickListener { startSocialSignIn(SupabaseAuth.OAuthProvider.GOOGLE) }
        binding.btnApple.setOnClickListener { startSocialSignIn(SupabaseAuth.OAuthProvider.APPLE) }
    }

    /**
     * Opens the provider's consent page. The result does not come back to this
     * fragment — the browser redirects to our deep link, so the rest of the flow
     * lives in [OAuthCallbackActivity].
     */
    private fun startSocialSignIn(provider: SupabaseAuth.OAuthProvider) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val enabled = SupabaseAuth.isProviderEnabled(provider)
            setLoading(false)
            if (!enabled) {
                // The provider has no credentials on the backend yet; saying so
                // beats sending the user to a page of JSON they can't act on.
                Log.w("LoginFragment", "${provider.id} is not enabled on the backend")
                snack(getString(com.noor.shoplocal.R.string.provider_unavailable, provider.label))
                return@launch
            }
            val url = SupabaseAuth.beginOAuth(requireContext(), provider)
            try {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                    .launchUrl(requireContext(), Uri.parse(url))
            } catch (e: ActivityNotFoundException) {
                Log.w("LoginFragment", "No browser available for ${provider.id} sign-in")
                snack(getString(com.noor.shoplocal.R.string.no_browser))
            }
        }
    }

    private fun snack(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
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
        binding.btnGoogle.isEnabled = !loading
        binding.btnApple.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
