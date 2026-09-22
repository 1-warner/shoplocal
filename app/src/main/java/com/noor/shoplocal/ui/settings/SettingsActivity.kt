package com.noor.shoplocal.ui.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.R
import com.noor.shoplocal.data.Prefs
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivitySettingsBinding
import com.noor.shoplocal.ui.BaseActivity
import com.noor.shoplocal.util.AppTheme
import kotlinx.coroutines.launch

/**
 * Settings menu that makes sense for a shopping app: profile details, language
 * (English / isiZulu / Afrikaans), light-dark theme, and notification preferences.
 * Choices are saved locally (so they apply instantly and survive restarts) and
 * synced to the user's profile via the REST API.
 */
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // Parallel arrays: index maps a language label to its resource/locale code.
    private val languageCodes = listOf("en", "zu", "af")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupLanguage()
        setupTheme()
        setupNotifications()
        prefillProfile()

        binding.btnSave.setOnClickListener { save() }
    }

    private fun setupLanguage() {
        val labels = listOf(
            getString(R.string.lang_english),
            getString(R.string.lang_zulu),
            getString(R.string.lang_afrikaans)
        )
        binding.languageDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        val current = languageCodes.indexOf(Prefs.language(this)).coerceAtLeast(0)
        binding.languageDropdown.setText(labels[current], false)
        binding.languageDropdown.setOnItemClickListener { _, _, position, _ ->
            binding.languageDropdown.tag = languageCodes[position]
        }
        binding.languageDropdown.tag = languageCodes[current]
    }

    private fun setupTheme() {
        when (Prefs.theme(this)) {
            "light" -> binding.themeGroup.check(R.id.themeLight)
            "dark" -> binding.themeGroup.check(R.id.themeDark)
            else -> binding.themeGroup.check(R.id.themeSystem)
        }
    }

    private fun setupNotifications() {
        binding.switchPush.isChecked = Prefs.notifyPush(this)
        binding.switchEmail.isChecked = Prefs.notifyEmail(this)
    }

    private fun prefillProfile() {
        val session = SupabaseAuth.currentSession(this) ?: return
        binding.inputName.setText(session.name)
        lifecycleScope.launch {
            val profile = ShopRepository.profile(session) ?: return@launch
            binding.inputName.setText(profile.name)
            binding.inputPhone.setText(profile.phone ?: "")
            binding.inputAddress.setText(profile.address ?: "")
            binding.switchSubscribe.isChecked = profile.isSubscriber
        }
    }

    private fun save() {
        val languageCode = (binding.languageDropdown.tag as? String) ?: "en"
        val themeMode = when (binding.themeGroup.checkedRadioButtonId) {
            R.id.themeLight -> "light"
            R.id.themeDark -> "dark"
            else -> "system"
        }
        val languageChanged = languageCode != Prefs.language(this)

        // Persist locally first so the UI reflects the choice immediately.
        Prefs.setLanguage(this, languageCode)
        Prefs.setTheme(this, themeMode)
        Prefs.setNotifyPush(this, binding.switchPush.isChecked)
        Prefs.setNotifyEmail(this, binding.switchEmail.isChecked)
        AppTheme.apply(themeMode)

        // Sync to the server profile (fire-and-forget; failures are non-fatal).
        val session = SupabaseAuth.currentSession(this)
        if (session != null) {
            lifecycleScope.launch {
                ShopRepository.updateProfile(
                    session = session,
                    name = binding.inputName.text?.toString()?.trim().orEmpty(),
                    phone = binding.inputPhone.text?.toString()?.trim().orEmpty(),
                    address = binding.inputAddress.text?.toString()?.trim().orEmpty(),
                    language = languageCode,
                    theme = themeMode,
                    notifyPush = binding.switchPush.isChecked,
                    notifyEmail = binding.switchEmail.isChecked
                )
                ShopRepository.setSubscriber(session, binding.switchSubscribe.isChecked)
            }
        }

        Snackbar.make(binding.root, getString(R.string.settings_saved), Snackbar.LENGTH_SHORT).show()

        // Recreate so a language change re-inflates every string right away.
        if (languageChanged) recreate()
    }
}
