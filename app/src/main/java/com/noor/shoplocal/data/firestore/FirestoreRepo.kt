package com.noor.shoplocal.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The app's **NoSQL** integration (Firebase Cloud Firestore — a document database).
 *
 * Firestore stores a per-user "activity feed": a document is written whenever the
 * user does something notable (logs in, views an item, places an order, lists an
 * item). The Activity screen reads these documents back. This demonstrates both
 * writing to and reading from a NoSQL store, alongside the SQL (Supabase) backend.
 *
 * Every call is guarded so the app runs fine even before Firebase is configured
 * (the bundled google-services.json is a placeholder until you add your own).
 */
object FirestoreRepo {

    private const val TAG = "FirestoreRepo"
    private const val COLLECTION = "activity"

    // Known activity actions.
    const val LOGIN = "Signed in"
    const val VIEW = "Viewed a product"
    const val ORDER = "Placed an order"
    const val LISTED = "Listed an item"

    data class ActivityItem(val action: String, val detail: String, val at: Long)

    private fun db(): FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "Firestore unavailable: ${e.message}")
        null
    }

    /** Writes an activity document (fire-and-forget). */
    fun log(uid: String, action: String, detail: String) {
        val d = db() ?: return
        val doc = hashMapOf(
            "uid" to uid,
            "action" to action,
            "detail" to detail,
            "at" to System.currentTimeMillis()
        )
        try {
            d.collection(COLLECTION).add(doc)
                .addOnSuccessListener { Log.i(TAG, "Activity logged: $action") }
                .addOnFailureListener { Log.w(TAG, "log failed: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "log threw: ${e.message}")
        }
    }

    /** Reads this user's recent activity, newest first, via a callback. */
    fun recent(uid: String, onResult: (List<ActivityItem>) -> Unit) {
        val d = db() ?: run { onResult(emptyList()); return }
        try {
            d.collection(COLLECTION)
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        val action = doc.getString("action") ?: return@mapNotNull null
                        ActivityItem(
                            action = action,
                            detail = doc.getString("detail").orEmpty(),
                            at = doc.getLong("at") ?: 0L
                        )
                    }.sortedByDescending { it.at }
                    onResult(items)
                }
                .addOnFailureListener {
                    Log.w(TAG, "recent failed: ${it.message}")
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            Log.w(TAG, "recent threw: ${e.message}")
            onResult(emptyList())
        }
    }
}
