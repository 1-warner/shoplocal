package com.noor.shoplocal

import com.noor.shoplocal.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the "In your area" distance maths. */
class GeoTest {

    // Reference coordinates.
    private val capeTown = doubleArrayOf(-33.9249, 18.4241)
    private val stellenbosch = doubleArrayOf(-33.9346, 18.8610) // ~40 km from Cape Town
    private val johannesburg = doubleArrayOf(-26.2041, 28.0473) // ~1260 km from Cape Town

    @Test
    fun distance_samepoint_isZero() {
        assertEquals(0.0, Geo.distanceKm(capeTown[0], capeTown[1], capeTown[0], capeTown[1]), 0.5)
    }

    @Test
    fun distance_capeTownToJohannesburg_isRoughly1260km() {
        val d = Geo.distanceKm(capeTown[0], capeTown[1], johannesburg[0], johannesburg[1])
        assertTrue("expected ~1260km but was $d", d in 1200.0..1320.0)
    }

    @Test
    fun nearby_trueForCloseTowns() {
        // Stellenbosch is well within the nearby radius of Cape Town.
        assertTrue(Geo.isNearby(capeTown[0], capeTown[1], stellenbosch[0], stellenbosch[1]))
    }

    @Test
    fun nearby_falseForDistantCities() {
        assertFalse(Geo.isNearby(capeTown[0], capeTown[1], johannesburg[0], johannesburg[1]))
    }
}
