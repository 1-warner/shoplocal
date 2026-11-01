package com.noor.shoplocal.ui.market

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.noor.shoplocal.R
import com.noor.shoplocal.data.SaCities
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.StorageRepository
import com.noor.shoplocal.data.SupabaseAuth
import com.noor.shoplocal.databinding.ActivitySellBinding
import com.noor.shoplocal.ui.BaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * "Sell an item" form — the create side of the member marketplace. The member
 * photographs the item (camera) or picks one from the gallery; the image is
 * uploaded to blob storage and its URL saved with the listing. Input is validated
 * before any network call so the form never crashes on bad data.
 */
class SellActivity : BaseActivity() {

    private lateinit var binding: ActivitySellBinding

    private val categories = listOf(
        "General", "Jewellery", "Bags", "Homeware", "Decor",
        "Clothing", "Beauty", "Art", "Food", "Electronics", "Other"
    )

    private var uploadedImageUrl: String? = null
    private var cameraTempUri: Uri? = null

    // Gallery picker → returns a content Uri.
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handlePickedImage(readBitmap(it)) }
    }

    // Camera capture → writes a full-size photo to our temp Uri.
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraTempUri?.let { handlePickedImage(readBitmap(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.inputCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        )
        binding.inputCategory.setText(categories.first(), false)

        binding.inputCity.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, SaCities.NAMES)
        )
        binding.inputCity.setText(SaCities.NAMES.first(), false)

        binding.btnCamera.setOnClickListener { launchCamera() }
        binding.btnGallery.setOnClickListener { pickImage.launch("image/*") }
        binding.btnPost.setOnClickListener { post() }
    }

    // ---- Image capture + upload ---------------------------------------------

    private fun launchCamera() {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraTempUri = uri
        takePhoto.launch(uri)
    }

    private fun readBitmap(uri: Uri): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    /** Scales + compresses the chosen image, previews it, and uploads to storage. */
    private fun handlePickedImage(bitmap: Bitmap?) {
        if (bitmap == null) {
            Snackbar.make(binding.root, R.string.photo_upload_failed, Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.imagePreview.setImageBitmap(bitmap)
        val session = SupabaseAuth.currentSession(this) ?: return

        binding.uploadProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.Default) { compress(bitmap) }
            val result = StorageRepository.uploadJpeg(session, bytes)
            binding.uploadProgress.visibility = View.GONE
            result.onSuccess {
                uploadedImageUrl = it
                Snackbar.make(binding.root, R.string.listing_posted_photo, Snackbar.LENGTH_SHORT).show()
            }.onFailure {
                Snackbar.make(binding.root, R.string.photo_upload_failed, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    /** Downscale to max 1024px on the long edge and JPEG-compress to keep uploads small. */
    private fun compress(src: Bitmap): ByteArray {
        val max = 1024
        val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val bmp = if (scale < 1f)
            Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        else src
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    // ---- Post the listing ----------------------------------------------------

    private fun post() {
        val session = SupabaseAuth.currentSession(this) ?: run { finish(); return }
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()
        val category = binding.inputCategory.text?.toString()?.ifBlank { "General" } ?: "General"
        val priceText = binding.inputPrice.text?.toString()?.trim().orEmpty()
        val cityName = binding.inputCity.text?.toString().orEmpty()

        val price = priceText.toDoubleOrNull()
        val error = when {
            title.length < 3 -> getString(R.string.sell_error_title)
            price == null || price <= 0.0 -> getString(R.string.sell_error_price)
            SaCities.byName(cityName) == null -> getString(R.string.sell_error_city)
            else -> null
        }
        if (error != null) {
            binding.errorText.text = error
            binding.errorText.visibility = View.VISIBLE
            return
        }
        binding.errorText.visibility = View.GONE

        val city = SaCities.byName(cityName)!!
        setLoading(true)
        lifecycleScope.launch {
            val result = ShopRepository.createListing(
                session = session,
                title = title,
                description = description,
                category = category,
                price = price!!,
                imageUrl = uploadedImageUrl,   // the photo uploaded to blob storage
                location = city.name,
                lat = city.lat,
                lng = city.lng
            )
            setLoading(false)
            result.onSuccess {
                com.noor.shoplocal.data.firestore.FirestoreRepo
                    .log(session.userId, com.noor.shoplocal.data.firestore.FirestoreRepo.LISTED, title)
                Snackbar.make(binding.root, getString(R.string.listing_posted), Snackbar.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                binding.errorText.text = it.message ?: getString(R.string.error_generic)
                binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnPost.isEnabled = !loading
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
