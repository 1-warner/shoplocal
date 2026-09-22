package com.noor.shoplocal.data

import android.content.Context
import android.util.Log
import com.noor.shoplocal.data.SupabaseAuth.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * All of the app's talking to the hosted REST API lives here. Every screen goes
 * through this one class, which keeps the network code in a single, testable place.
 *
 * Reads of the public catalogue use the anon key; anything user-specific
 * (wishlist, cart, orders, posting a review, placing an order) sends the signed-in
 * user's JWT so Row-Level Security on the server returns only their own data.
 *
 * Product fields are selected with an embedded `sellers(...)` join so one request
 * returns the product *and* its artisan's story.
 */
object ShopRepository {

    private const val TAG = "ShopRepository"
    private const val PRODUCT_SELECT =
        "id,name,description,category,price,discount_price,image_url,stock,rating_avg,rating_count,origin,sellers(name,verified,story)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ---- Request helpers -----------------------------------------------------

    private fun get(path: String, token: String? = null): Request.Builder =
        Request.Builder()
            .url("${Supabase.REST}/$path")
            .addHeader("apikey", Supabase.ANON_KEY)
            .apply { if (token != null) addHeader("Authorization", "Bearer $token") }

    private fun authed(path: String, token: String): Request.Builder =
        get(path, token).addHeader("Content-Type", "application/json")

    private fun Request.runForString(): String {
        client.newCall(this).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error(parseError(text, resp.code))
            return text
        }
    }

    // ---- Catalogue (public) --------------------------------------------------

    /**
     * Loads the product catalogue from the API. On network failure it falls back
     * to the last successfully-cached response so the app still works offline.
     */
    suspend fun products(context: Context, category: String? = null, search: String? = null): List<Product> =
        withContext(Dispatchers.IO) {
            try {
                var path = "products?select=$PRODUCT_SELECT&order=created_at.desc"
                if (!category.isNullOrBlank() && category != "All") path += "&category=eq.$category"
                if (!search.isNullOrBlank()) path += "&name=ilike.*${search.trim()}*"

                val body = get(path).build().runForString()
                Prefs.cacheCatalogue(context, body)
                Log.d(TAG, "Fetched ${JSONArray(body).length()} products from API")
                parseProducts(body)
            } catch (e: Exception) {
                Log.w(TAG, "products() network failed (${e.message}); using offline cache")
                val cached = Prefs.cachedCatalogue(context) ?: throw e
                parseProducts(cached).filter { p ->
                    (category.isNullOrBlank() || category == "All" || p.category == category) &&
                        (search.isNullOrBlank() || p.name.contains(search, ignoreCase = true))
                }
            }
        }

    /** Distinct category names for the filter chips, derived from the cached catalogue. */
    fun categoriesFrom(products: List<Product>): List<String> =
        listOf("All") + products.map { it.category }.distinct().sorted()

    // ---- Sellers (artisan map) ----------------------------------------------

    /**
     * Loads all sellers that have map coordinates, with a count of how many
     * products each one has, for the artisan map screen.
     */
    suspend fun sellers(): List<Seller> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                "sellers?select=name,verified,location,story,lat,lng,products(count)&lat=not.is.null&order=name"
            ).build().runForString()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                // PostgREST returns the embedded aggregate as products:[{count:N}]
                val count = o.optJSONArray("products")?.optJSONObject(0)?.optInt("count", 0) ?: 0
                Seller(
                    name = o.optString("name", "Local artisan"),
                    verified = o.optBoolean("verified", false),
                    location = o.optString("location", ""),
                    story = if (o.isNull("story")) null else o.optString("story"),
                    lat = o.optDouble("lat", 0.0),
                    lng = o.optDouble("lng", 0.0),
                    productCount = count
                )
            }
        }.getOrElse { Log.w(TAG, "sellers() failed: ${it.message}"); emptyList() }
    }

    /** Products for one seller (by name), used when a map pin is tapped. */
    suspend fun productsBySeller(sellerName: String): List<Product> = withContext(Dispatchers.IO) {
        runCatching {
            // `sellers!inner(...)` makes the join an inner join, so filtering on the
            // embedded seller name filters the products rows themselves.
            val select =
                "id,name,description,category,price,discount_price,image_url,stock,rating_avg,rating_count,origin,sellers!inner(name,verified,story)"
            val encoded = java.net.URLEncoder.encode(sellerName, "UTF-8")
            val path = "products?select=$select&sellers.name=eq.$encoded&order=name"
            parseProducts(get(path).build().runForString())
        }.getOrElse { Log.w(TAG, "productsBySeller() failed: ${it.message}"); emptyList() }
    }

    // ---- Reviews -------------------------------------------------------------

    suspend fun reviews(productId: String): List<Review> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                "reviews?select=id,user_name,rating,body,helpful_count,created_at&product_id=eq.$productId&order=created_at.desc"
            ).build().runForString()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Review(
                    id = o.getString("id"),
                    userName = o.optString("user_name", "Shopper"),
                    rating = o.optInt("rating", 0),
                    body = o.optString("body", ""),
                    helpfulCount = o.optInt("helpful_count", 0),
                    createdAt = o.optString("created_at", "")
                )
            }
        }.getOrElse { Log.w(TAG, "reviews() failed: ${it.message}"); emptyList() }
    }

    suspend fun addReview(session: Session, productId: String, rating: Int, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("product_id", productId)
                    .put("user_id", session.userId)
                    .put("user_name", session.name.ifBlank { session.email.substringBefore("@") })
                    .put("rating", rating)
                    .put("body", body)
                    .toString()
                authed("reviews", session.accessToken)
                    .addHeader("Prefer", "return=minimal")
                    .post(payload.toRequestBody(JSON))
                    .build().runForString()
                Unit
            }
        }

    // ---- Wishlist ------------------------------------------------------------

    suspend fun wishlist(session: Session): List<Product> = withContext(Dispatchers.IO) {
        runCatching {
            val body = authed("wishlist?select=products($PRODUCT_SELECT)", session.accessToken)
                .build().runForString()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                arr.getJSONObject(i).optJSONObject("products")?.let { parseProduct(it) }
            }
        }.getOrElse { Log.w(TAG, "wishlist() failed: ${it.message}"); emptyList() }
    }

    suspend fun addToWishlist(session: Session, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("user_id", session.userId)
                    .put("product_id", productId)
                    .toString()
                // on_conflict makes re-adding a no-op instead of an error.
                authed("wishlist?on_conflict=user_id,product_id", session.accessToken)
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(payload.toRequestBody(JSON))
                    .build().runForString()
                Unit
            }
        }

    suspend fun removeFromWishlist(session: Session, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                authed("wishlist?user_id=eq.${session.userId}&product_id=eq.$productId", session.accessToken)
                    .delete()
                    .build().runForString()
                Unit
            }
        }

    // ---- Cart ----------------------------------------------------------------

    suspend fun cart(session: Session): List<CartLine> = withContext(Dispatchers.IO) {
        runCatching {
            val body = authed("cart_items?select=quantity,products($PRODUCT_SELECT)", session.accessToken)
                .build().runForString()
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val p = o.optJSONObject("products") ?: return@mapNotNull null
                CartLine(parseProduct(p), o.optInt("quantity", 1))
            }
        }.getOrElse { Log.w(TAG, "cart() failed: ${it.message}"); emptyList() }
    }

    /** Adds or updates a cart line (upsert on user+product). */
    suspend fun setCartQuantity(session: Session, productId: String, quantity: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (quantity <= 0) return@runCatching removeFromCart(session, productId).getOrThrow()
                val payload = JSONObject()
                    .put("user_id", session.userId)
                    .put("product_id", productId)
                    .put("quantity", quantity)
                    .toString()
                authed("cart_items?on_conflict=user_id,product_id", session.accessToken)
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(payload.toRequestBody(JSON))
                    .build().runForString()
                Unit
            }
        }

    suspend fun removeFromCart(session: Session, productId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                authed("cart_items?user_id=eq.${session.userId}&product_id=eq.$productId", session.accessToken)
                    .delete()
                    .build().runForString()
                Unit
            }
        }

    // ---- Checkout: the custom REST endpoint we authored ----------------------

    /**
     * Calls POST /rest/v1/rpc/place_order — the server-side function that turns the
     * cart into an order, awards Local Points and clears the cart, all atomically.
     */
    suspend fun placeOrder(session: Session, address: String, deliveryFee: Double): Result<Order> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("delivery_address", address)
                    .put("delivery_fee", deliveryFee)
                    .toString()
                val body = authed("rpc/place_order", session.accessToken)
                    .post(payload.toRequestBody(JSON))
                    .build().runForString()
                Log.i(TAG, "place_order succeeded")
                // RPC returning a single row may come back as an object or a 1-element array.
                val o = if (body.trimStart().startsWith("[")) JSONArray(body).getJSONObject(0) else JSONObject(body)
                parseOrder(o)
            }.onFailure { Log.w(TAG, "placeOrder failed: ${it.message}") }
        }

    suspend fun orders(session: Session): List<Order> = withContext(Dispatchers.IO) {
        runCatching {
            val body = authed(
                "orders?select=id,subtotal,delivery_fee,total,status,points_earned,created_at&order=created_at.desc",
                session.accessToken
            ).build().runForString()
            val arr = JSONArray(body)
            (0 until arr.length()).map { parseOrder(arr.getJSONObject(it)) }
        }.getOrElse { Log.w(TAG, "orders() failed: ${it.message}"); emptyList() }
    }

    // ---- Profile / loyalty ---------------------------------------------------

    suspend fun profile(session: Session): Profile? = withContext(Dispatchers.IO) {
        runCatching {
            val body = authed(
                "profiles?select=id,name,email,loyalty_points,phone,address&id=eq.${session.userId}",
                session.accessToken
            ).build().runForString()
            val arr = JSONArray(body)
            if (arr.length() == 0) return@runCatching null
            val o = arr.getJSONObject(0)
            Profile(
                id = o.getString("id"),
                name = o.optString("name", session.name),
                email = o.optString("email", session.email),
                loyaltyPoints = o.optInt("loyalty_points", 0),
                phone = o.optString("phone").ifBlank { null },
                address = o.optString("address").ifBlank { null }
            )
        }.getOrElse { Log.w(TAG, "profile() failed: ${it.message}"); null }
    }

    /** Persists profile + notification settings back to the API. */
    suspend fun updateProfile(
        session: Session,
        name: String,
        phone: String,
        address: String,
        language: String,
        theme: String,
        notifyPush: Boolean,
        notifyEmail: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("name", name)
                .put("phone", phone)
                .put("address", address)
                .put("language", language)
                .put("theme", theme)
                .put("notify_push", notifyPush)
                .put("notify_email", notifyEmail)
                .toString()
            authed("profiles?id=eq.${session.userId}", session.accessToken)
                .addHeader("Prefer", "return=minimal")
                .patch(payload.toRequestBody(JSON))
                .build().runForString()
            Unit
        }
    }

    // ---- JSON parsing --------------------------------------------------------

    private fun parseProducts(body: String): List<Product> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { parseProduct(arr.getJSONObject(it)) }
    }

    private fun parseProduct(o: JSONObject): Product {
        val seller = o.optJSONObject("sellers")
        return Product(
            id = o.getString("id"),
            name = o.optString("name", ""),
            description = o.optString("description", ""),
            category = o.optString("category", ""),
            price = o.optDouble("price", 0.0),
            discountPrice = if (o.isNull("discount_price")) null else o.optDouble("discount_price"),
            imageUrl = if (o.isNull("image_url")) null else o.optString("image_url"),
            stock = o.optInt("stock", 0),
            sellerName = seller?.optString("name") ?: "Local artisan",
            sellerVerified = seller?.optBoolean("verified", false) ?: false,
            sellerStory = seller?.optString("story"),
            ratingAvg = o.optDouble("rating_avg", 0.0),
            ratingCount = o.optInt("rating_count", 0),
            origin = o.optString("origin", "South Africa")
        )
    }

    private fun parseOrder(o: JSONObject): Order = Order(
        id = o.getString("id"),
        subtotal = o.optDouble("subtotal", 0.0),
        deliveryFee = o.optDouble("delivery_fee", 0.0),
        total = o.optDouble("total", 0.0),
        status = o.optString("status", "pending"),
        pointsEarned = o.optInt("points_earned", 0),
        createdAt = o.optString("created_at", "")
    )

    private fun parseError(text: String, code: Int): String =
        try {
            val o = JSONObject(text)
            o.optString("message", o.optString("msg", o.optString("hint", "Request failed (HTTP $code)")))
        } catch (e: Exception) {
            "Request failed (HTTP $code)"
        }
}
