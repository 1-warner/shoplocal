package com.noor.shoplocal

import com.noor.shoplocal.data.CartLine
import com.noor.shoplocal.data.Pricing
import com.noor.shoplocal.data.Product
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the cart/checkout arithmetic. These run on the JVM (no device
 * needed) and are executed automatically by the GitHub Actions workflow.
 */
class PricingTest {

    /** Minimal product builder so each test only sets the fields it cares about. */
    private fun product(price: Double, discount: Double? = null) = Product(
        id = "p", name = "Test", description = "", category = "Test",
        price = price, discountPrice = discount, imageUrl = null, stock = 10,
        sellerName = "Maker", sellerVerified = true, sellerStory = null,
        ratingAvg = 0.0, ratingCount = 0, origin = "South Africa"
    )

    private fun line(price: Double, qty: Int, discount: Double? = null) =
        CartLine(product(price, discount), qty)

    @Test
    fun subtotal_sumsEffectivePriceTimesQuantity() {
        val lines = listOf(line(100.0, 2), line(50.0, 1))
        assertEquals(250.0, Pricing.subtotal(lines), 0.001)
    }

    @Test
    fun subtotal_usesDiscountPriceWhenOnSale() {
        val lines = listOf(line(200.0, 1, discount = 150.0))
        assertEquals(150.0, Pricing.subtotal(lines), 0.001)
    }

    @Test
    fun delivery_isChargedBelowThreshold() {
        assertEquals(Pricing.STANDARD_DELIVERY_FEE, Pricing.deliveryFee(200.0), 0.001)
    }

    @Test
    fun delivery_isFreeAtOrAboveThreshold() {
        assertEquals(0.0, Pricing.deliveryFee(Pricing.FREE_DELIVERY_THRESHOLD), 0.001)
        assertEquals(0.0, Pricing.deliveryFee(750.0), 0.001)
    }

    @Test
    fun delivery_isZeroForEmptyCart() {
        assertEquals(0.0, Pricing.deliveryFee(0.0), 0.001)
    }

    @Test
    fun total_addsDeliveryToSubtotal() {
        // R200 subtotal is below the free-delivery threshold, so +R60.
        assertEquals(260.0, Pricing.total(200.0), 0.001)
        // R600 subtotal ships free.
        assertEquals(600.0, Pricing.total(600.0), 0.001)
    }

    @Test
    fun delivery_isFreeForSubscribersBelowThreshold() {
        // A ShopLocal MORE member gets free delivery even on a small basket.
        assertEquals(0.0, Pricing.deliveryFee(200.0, isSubscriber = true), 0.001)
        assertEquals(200.0, Pricing.total(200.0, isSubscriber = true), 0.001)
        // Non-subscriber still pays on the same basket.
        assertEquals(Pricing.STANDARD_DELIVERY_FEE, Pricing.deliveryFee(200.0, isSubscriber = false), 0.001)
    }

    @Test
    fun loyaltyPoints_isOnePerTenRandOfSubtotal() {
        assertEquals(25, Pricing.loyaltyPoints(250.0))
        assertEquals(25, Pricing.loyaltyPoints(259.99)) // rounds down
        assertEquals(0, Pricing.loyaltyPoints(0.0))
    }

    @Test
    fun itemCount_sumsQuantities() {
        val lines = listOf(line(10.0, 3), line(20.0, 2))
        assertEquals(5, Pricing.itemCount(lines))
    }
}
