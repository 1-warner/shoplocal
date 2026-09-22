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
        "id,name,description,category,price,discount_price,image_url,stock,rating_avg,rating_count,origin,sellers(name,verified,story,lat,lng)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** Application context, used only to refresh an expired token on a 401. */
    private var appContext: android.content.Context? = null
    fun attach(context: android.content.Context) { appContext = context.applicationContext }

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
            // An authenticated request that comes back 401 usually means the access
            // token expired. Refresh it once and retry the identical request, so the
            // cart/wishlist/orders keep working long after login.
            if (resp.code == 401 && header("Authorization") != null) {
                val newToken = appContext?.let { SupabaseAuth.refreshBlocking(it) }
                if (newToken != null) {
                    val retried = newBuilder().header("Authorization", "Bearer $newToken").build()
                    client.newCall(retried).execute().use { r2 ->
                        val t2 = r2.body?.string().orEmpty()
                        if (!r2.isSuccessful) error(parseError(t2, r2.code))
                        return t2
                    }
                }
            }
            if (!resp.isSuccessful) error(parseError(text, resp.code))
            return text
        }
    }

    // ---- Catalogue (public) --------------------------------------------------

    /**
     * Loads the product catalogue from the API. On network failure it falls back
     * to the last successfully-cached response so the app still works offline.
     */
    /** Sort options for the catalogue (mirrors Superbalist/Bash-style sorting). */
    enum class Sort(val order: String) {
        NEWEST("created_at.desc"),
        PRICE_LOW("price.asc"),
        PRICE_HIGH("price.desc"),
        RATING("rating_avg.desc")
    }

    suspend fun products(
        context: Context,
        category: String? = null,
        search: String? = null,
        sort: Sort = Sort.NEWEST,
        onSaleOnly: Boolean = false
    ): List<Product> =
        withContext(Dispatchers.IO) {
            // We always fetch the full catalogue (so it can be cached for offline use)
            // and apply category/search/sale/sort locally — keeps one cache authoritative.
            val all = try {
                val body = get("products?select=$PRODUCT_SELECT&order=created_at.desc").build().runForString()
                Prefs.cacheCatalogue(context, body)
                Log.d(TAG, "Fetched ${JSONArray(body).length()} products from API")
                parseProducts(body)
            } catch (e: Exception) {
                Log.w(TAG, "products() network failed (${e.message}); using offline cache")
                val cached = Prefs.cachedCatalogue(context) ?: throw e
                parseProducts(cached)
            }
            filterAndSort(all, category, search, sort, onSaleOnly)
        }

    /** Area filter for the "In your area" feature. */
    enum class Area { ALL, NEARBY, ELSEWHERE }

    /**
     * Splits the catalogue by proximity to the user's saved location. Products
     * whose seller has no coordinates are treated as "elsewhere". With no user
     * location, ALL is returned unchanged.
     */
    fun filterByArea(products: List<Product>, userLat: Double?, userLng: Double?, area: Area): List<Product> {
        if (area == Area.ALL || userLat == null || userLng == null) return products
        return products.filter { p ->
            val near = p.sellerLat != null && p.sellerLng != null &&
                Geo.isNearby(userLat, userLng, p.sellerLat, p.sellerLng)
            if (area == Area.NEARBY) near else !near
        }
    }

    /** Pure client-side filtering + sorting, reused by both the network and cache paths. */
    fun filterAndSort(
        all: List<Product>,
        category: String?,
        search: String?,
        sort: Sort,
        onSaleOnly: Boolean
    ): List<Product> {
        val filtered = all.filter { p ->
            (category.isNullOrBlank() || category == "All" || p.category == category) &&
                (search.isNullOrBlank() || p.name.contains(search, ignoreCase = true) ||
                    p.category.contains(search, ignoreCase = true)) &&
                (!onSaleOnly || p.isOnSale)
        }
        return when (sort) {
            Sort.PRICE_LOW -> filtered.sortedBy { it.effectivePrice }
            Sort.PRICE_HIGH -> filtered.sortedByDescending { it.effectivePrice }
            Sort.RATING -> filtered.sortedByDescending { it.ratingAvg }
            Sort.NEWEST -> filtered
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
                "profiles?select=id,name,email,loyalty_points,phone,address,is_subscriber,lat,lng&id=eq.${session.userId}",
                session.accessToken
            ).build().runForString()
            val arr = JSONArray(body)
            if (arr.length() == 0) return@runCatching null
            val o = arr.getJSONObject(0)
            Profile(
                id = o.getString("id"),
                name = str(o, "name") ?: session.name,
                email = str(o, "email") ?: session.email,
                loyaltyPoints = o.optInt("loyalty_points", 0),
                phone = str(o, "phone"),
                address = str(o, "address"),
                isSubscriber = o.optBoolean("is_subscriber", false),
                lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                lng = if (o.isNull("lng")) null else o.optDouble("lng")
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

    // ---- Marketplace (user listings) ----------------------------------------

    private const val LISTING_SELECT =
        "id,seller_user_id,seller_name,title,description,category,price,image_url,location,lat,lng,status,created_at"

    /** All available marketplace listings, newest first. */
    suspend fun listings(): List<Listing> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("marketplace_listings?select=$LISTING_SELECT&status=eq.available&order=created_at.desc")
                .build().runForString()
            parseListings(body)
        }.getOrElse { Log.w(TAG, "listings() failed: ${it.message}"); emptyList() }
    }

    /** The signed-in user's own listings (available or sold). */
    suspend fun myListings(session: Session): List<Listing> = withContext(Dispatchers.IO) {
        runCatching {
            val body = authed(
                "marketplace_listings?select=$LISTING_SELECT&seller_user_id=eq.${session.userId}&order=created_at.desc",
                session.accessToken
            ).build().runForString()
            parseListings(body)
        }.getOrElse { Log.w(TAG, "myListings() failed: ${it.message}"); emptyList() }
    }

    /** Create a marketplace listing (the "sell your own item" flow). */
    suspend fun createListing(
        session: Session,
        title: String,
        description: String,
        category: String,
        price: Double,
        imageUrl: String?,
        location: String,
        lat: Double?,
        lng: Double?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("seller_user_id", session.userId)
                .put("seller_name", session.name.ifBlank { session.email.substringBefore("@") })
                .put("title", title)
                .put("description", description)
                .put("category", category)
                .put("price", price)
                .put("image_url", imageUrl ?: JSONObject.NULL)
                .put("location", location)
                .put("lat", lat ?: JSONObject.NULL)
                .put("lng", lng ?: JSONObject.NULL)
                .toString()
            authed("marketplace_listings", session.accessToken)
                .addHeader("Prefer", "return=minimal")
                .post(payload.toRequestBody(JSON))
                .build().runForString()
            Log.i(TAG, "Listing created: $title")
            Unit
        }.onFailure { Log.w(TAG, "createListing failed: ${it.message}") }
    }

    suspend fun deleteListing(session: Session, id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                authed("marketplace_listings?id=eq.$id", session.accessToken)
                    .delete().build().runForString()
                Unit
            }
        }

    // ---- Subscription (ShopLocal MORE) & order status ------------------------

    suspend fun setSubscriber(session: Session, subscribed: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("is_subscriber", subscribed).toString()
                authed("profiles?id=eq.${session.userId}", session.accessToken)
                    .addHeader("Prefer", "return=minimal")
                    .patch(payload.toRequestBody(JSON))
                    .build().runForString()
                Unit
            }
        }

    /**
     * Saves the delivery location — address text plus coordinates, so the app can
     * work out which items are "in your area". Used by the cart / settings prompts.
     */
    suspend fun updateLocation(session: Session, address: String, lat: Double?, lng: Double?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject()
                    .put("address", address)
                    .put("lat", lat ?: JSONObject.NULL)
                    .put("lng", lng ?: JSONObject.NULL)
                    .toString()
                authed("profiles?id=eq.${session.userId}", session.accessToken)
                    .addHeader("Prefer", "return=minimal")
                    .patch(payload.toRequestBody(JSON))
                    .build().runForString()
                Unit
            }
        }

    suspend fun cancelOrder(session: Session, orderId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().put("status", "cancelled").toString()
                authed("orders?id=eq.$orderId", session.accessToken)
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
            origin = o.optString("origin", "South Africa"),
            sellerLat = seller?.takeIf { !it.isNull("lat") }?.optDouble("lat"),
            sellerLng = seller?.takeIf { !it.isNull("lng") }?.optDouble("lng")
        )
    }

    private fun parseListings(body: String): List<Listing> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Listing(
                id = o.getString("id"),
                sellerUserId = o.optString("seller_user_id", ""),
                sellerName = o.optString("seller_name", "Member"),
                title = o.optString("title", ""),
                description = o.optString("description", ""),
                category = o.optString("category", "General"),
                price = o.optDouble("price", 0.0),
                imageUrl = if (o.isNull("image_url")) null else o.optString("image_url"),
                location = o.optString("location", ""),
                lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                lng = if (o.isNull("lng")) null else o.optDouble("lng"),
                status = o.optString("status", "available"),
                createdAt = o.optString("created_at", "")
            )
        }
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

    /**
     * Safely reads a string field, returning null for a missing value, a JSON null,
     * a blank string, or the literal "null" that Android's optString produces for
     * JSON nulls (the cause of "null" showing in the settings fields).
     */
    private fun str(o: JSONObject, key: String): String? {
        if (o.isNull(key)) return null
        val v = o.optString(key).trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun parseError(text: String, code: Int): String =
        try {
            val o = JSONObject(text)
            o.optString("message", o.optString("msg", o.optString("hint", "Request failed (HTTP $code)")))
        } catch (e: Exception) {
            "Request failed (HTTP $code)"
        }
}
