package com.noor.shoplocal.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.noor.shoplocal.data.ShopRepository
import com.noor.shoplocal.data.SupabaseAuth.Session

/**
 * Coordinates offline actions and their synchronisation.
 *
 * When the device is online, wishlist/cart changes go straight to the REST API.
 * When it is offline, they are saved to the local Room database and replayed the
 * next time the app is online (startup or when connectivity returns), giving the
 * app true offline-mode-with-sync.
 */
object SyncManager {

    private const val TAG = "SyncManager"

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Adds to wishlist now if online, otherwise queues it. Returns true if synced live. */
    suspend fun addToWishlist(context: Context, session: Session, productId: String): Boolean {
        if (isOnline(context)) {
            val ok = ShopRepository.addToWishlist(session, productId).isSuccess
            if (ok) return true
        }
        AppDatabase.get(context).pendingOps()
            .insert(PendingOp(type = PendingOp.WISHLIST_ADD, productId = productId))
        Log.i(TAG, "Queued wishlist add for $productId (offline)")
        return false
    }

    /** Sets a cart quantity now if online, otherwise queues it. Returns true if synced live. */
    suspend fun setCart(context: Context, session: Session, productId: String, quantity: Int): Boolean {
        if (isOnline(context)) {
            val ok = ShopRepository.setCartQuantity(session, productId, quantity).isSuccess
            if (ok) return true
        }
        AppDatabase.get(context).pendingOps()
            .insert(PendingOp(type = PendingOp.CART_SET, productId = productId, quantity = quantity))
        Log.i(TAG, "Queued cart set for $productId x$quantity (offline)")
        return false
    }

    suspend fun pendingCount(context: Context): Int =
        AppDatabase.get(context).pendingOps().count()

    /**
     * Replays every queued action against the API, deleting each once it succeeds.
     * Returns the number of actions synced.
     */
    suspend fun syncPending(context: Context, session: Session): Int {
        if (!isOnline(context)) return 0
        val dao = AppDatabase.get(context).pendingOps()
        var synced = 0
        for (op in dao.all()) {
            val ok = when (op.type) {
                PendingOp.WISHLIST_ADD -> ShopRepository.addToWishlist(session, op.productId).isSuccess
                PendingOp.CART_SET -> ShopRepository.setCartQuantity(session, op.productId, op.quantity).isSuccess
                else -> true // unknown op: drop it
            }
            if (ok) {
                dao.delete(op.id)
                synced++
            }
        }
        if (synced > 0) Log.i(TAG, "Synced $synced offline action(s)")
        return synced
    }
}
