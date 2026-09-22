package com.noor.shoplocal.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain models. [Product] is [Parcelable] so it can be handed straight to the
 * product-detail screen through an Intent without a second network round-trip.
 */
@Parcelize
data class Product(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val price: Double,
    val discountPrice: Double?,      // null when the item is not on promotion
    val imageUrl: String?,
    val stock: Int,
    val sellerName: String,
    val sellerVerified: Boolean,
    val sellerStory: String?,
    val ratingAvg: Double,
    val ratingCount: Int,
    val origin: String,
    val sellerLat: Double? = null,
    val sellerLng: Double? = null
) : Parcelable {

    /** True when we have coordinates to plot the seller on the embedded map. */
    val hasLocation: Boolean get() = sellerLat != null && sellerLng != null

    /** Price the customer actually pays — the promotion price when one is set. */
    val effectivePrice: Double get() = discountPrice ?: price

    val isOnSale: Boolean get() = discountPrice != null && discountPrice < price
}

/** A product plus the quantity the user placed in their cart. */
data class CartLine(
    val product: Product,
    val quantity: Int
)

data class Order(
    val id: String,
    val total: Double,
    val subtotal: Double,
    val deliveryFee: Double,
    val status: String,
    val pointsEarned: Int,
    val createdAt: String
)

data class Review(
    val id: String,
    val userName: String,
    val rating: Int,
    val body: String,
    val helpfulCount: Int,
    val createdAt: String
)

/**
 * A Facebook-Marketplace-style user listing: an item put up for sale by a
 * ShopLocal member (as opposed to a curated artisan product). Parcelable so it
 * can be passed to the listing-detail screen.
 */
@Parcelize
data class Listing(
    val id: String,
    val sellerUserId: String,
    val sellerName: String,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    val imageUrl: String?,
    val location: String,
    val lat: Double?,
    val lng: Double?,
    val status: String,
    val createdAt: String
) : Parcelable {
    val hasLocation: Boolean get() = lat != null && lng != null
}

/** A verified local artisan/seller, with map coordinates for the artisan map. */
data class Seller(
    val name: String,
    val verified: Boolean,
    val location: String,
    val story: String?,
    val lat: Double,
    val lng: Double,
    val productCount: Int
)

/** The signed-in user's profile, including their Local Points balance and settings. */
data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val loyaltyPoints: Int,
    val phone: String?,
    val address: String?,
    val isSubscriber: Boolean = false
)
