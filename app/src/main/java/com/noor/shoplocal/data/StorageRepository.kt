package com.noor.shoplocal.data

import android.util.Log
import com.noor.shoplocal.data.SupabaseAuth.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Uploads images to Supabase **Storage** (an S3-style blob store) — this is the
 * app's blob-storage integration. A member can photograph an item (or pick one
 * from the gallery); the JPEG bytes are uploaded here and the resulting public URL
 * is saved on the listing/product, replacing the old "paste an image URL" flow.
 */
object StorageRepository {

    private const val TAG = "StorageRepository"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JPEG = "image/jpeg".toMediaType()

    /**
     * Uploads JPEG [bytes] to the public image bucket under the signed-in user's
     * folder and returns the public URL. The path is namespaced by user id so
     * Row-Level Security lets each member manage only their own uploads.
     */
    suspend fun uploadJpeg(session: Session, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = "${session.userId}/${System.currentTimeMillis()}.jpg"
                val request = Request.Builder()
                    .url("${Supabase.STORAGE}/object/${Supabase.IMAGE_BUCKET}/$path")
                    .addHeader("apikey", Supabase.ANON_KEY)
                    .addHeader("Authorization", "Bearer ${session.accessToken}")
                    .addHeader("x-upsert", "true")
                    .post(bytes.toRequestBody(JPEG))
                    .build()

                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Upload failed (HTTP ${resp.code}): $text")
                }
                val url = Supabase.publicImageUrl(path)
                Log.i(TAG, "Image uploaded: $url")
                url
            }.onFailure { Log.w(TAG, "uploadJpeg failed: ${it.message}") }
        }
}
