package com.noor.shoplocal

import com.noor.shoplocal.data.Product
import com.noor.shoplocal.data.ShopRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure catalogue filtering + sorting used by the Home screen. */
class CatalogueTest {

    private fun product(
        id: String, name: String, category: String, price: Double, discount: Double? = null, rating: Double = 0.0
    ) = Product(
        id = id, name = name, description = "", category = category,
        price = price, discountPrice = discount, imageUrl = null, stock = 5,
        sellerName = "Maker", sellerVerified = true, sellerStory = null,
        ratingAvg = rating, ratingCount = if (rating > 0) 1 else 0, origin = "South Africa"
    )

    private val catalogue = listOf(
        product("1", "Beaded Necklace", "Jewellery", 300.0, discount = 250.0, rating = 4.5),
        product("2", "Leather Bag", "Bags", 1000.0, rating = 4.0),
        product("3", "Clay Mug", "Homeware", 150.0, discount = 120.0, rating = 5.0),
        product("4", "Wool Rug", "Homeware", 1800.0, rating = 3.0)
    )

    @Test
    fun filter_byCategory() {
        val result = ShopRepository.filterAndSort(catalogue, "Homeware", null, ShopRepository.Sort.NEWEST, false)
        assertEquals(2, result.size)
        assertTrue(result.all { it.category == "Homeware" })
    }

    @Test
    fun filter_bySearchTerm() {
        val result = ShopRepository.filterAndSort(catalogue, "All", "bag", ShopRepository.Sort.NEWEST, false)
        assertEquals(1, result.size)
        assertEquals("Leather Bag", result.first().name)
    }

    @Test
    fun filter_dealsOnlyKeepsDiscountedItems() {
        val result = ShopRepository.filterAndSort(catalogue, "All", null, ShopRepository.Sort.NEWEST, true)
        assertEquals(2, result.size)
        assertTrue(result.all { it.isOnSale })
    }

    @Test
    fun sort_priceLowToHigh_usesEffectivePrice() {
        val result = ShopRepository.filterAndSort(catalogue, "All", null, ShopRepository.Sort.PRICE_LOW, false)
        // Clay Mug's effective (discounted) price 120 is the lowest.
        assertEquals("Clay Mug", result.first().name)
        val prices = result.map { it.effectivePrice }
        assertEquals(prices.sorted(), prices)
    }

    @Test
    fun sort_byRating_highestFirst() {
        val result = ShopRepository.filterAndSort(catalogue, "All", null, ShopRepository.Sort.RATING, false)
        assertEquals("Clay Mug", result.first().name) // rating 5.0
    }
}
