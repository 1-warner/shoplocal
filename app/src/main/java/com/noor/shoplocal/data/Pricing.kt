package com.noor.shoplocal.data

import kotlin.math.floor

/**
 * Pure cart/checkout arithmetic. Kept free of Android types so it can be covered
 * by fast JVM unit tests (see PricingTest). The same rules are enforced on the
 * server by the `place_order` RPC, so the figures the app shows match the order.
 */
object Pricing {

    /** Free delivery once the basket reaches this subtotal (in Rand). */
    const val FREE_DELIVERY_THRESHOLD = 500.0
    const val STANDARD_DELIVERY_FEE = 60.0

    /** Loyalty rule: the customer earns one Local Point for every R10 spent. */
    const val RAND_PER_POINT = 10

    /** Subtotal = sum of each line's effective price × quantity. */
    fun subtotal(lines: List<CartLine>): Double =
        lines.sumOf { it.product.effectivePrice * it.quantity }

    /**
     * Delivery is free above the threshold or for ShopLocal MORE subscribers
     * (a TakealotMORE-style perk), otherwise a flat fee. Empty cart ships nothing.
     */
    fun deliveryFee(subtotal: Double, isSubscriber: Boolean = false): Double = when {
        subtotal <= 0.0 -> 0.0
        isSubscriber -> 0.0
        subtotal >= FREE_DELIVERY_THRESHOLD -> 0.0
        else -> STANDARD_DELIVERY_FEE
    }

    fun total(subtotal: Double, isSubscriber: Boolean = false): Double =
        subtotal + deliveryFee(subtotal, isSubscriber)

    /** Local Points earned on an order, based on the subtotal (not delivery). */
    fun loyaltyPoints(subtotal: Double): Int =
        if (subtotal <= 0.0) 0 else floor(subtotal / RAND_PER_POINT).toInt()

    /** Count of items across all cart lines (for the cart badge). */
    fun itemCount(lines: List<CartLine>): Int = lines.sumOf { it.quantity }
}
