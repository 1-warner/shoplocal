package com.noor.shoplocal.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geographic helpers for the "In your area" feature. Pure maths, no Android types,
 * so it can be unit-tested (see GeoTest).
 */
object Geo {

    /** Items within this many km of the user count as "in your area". */
    const val NEARBY_RADIUS_KM = 250.0

    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle (haversine) distance between two points, in kilometres. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return EARTH_RADIUS_KM * (2 * atan2(sqrt(a), sqrt(1 - a)))
    }

    fun isNearby(userLat: Double, userLng: Double, lat: Double, lng: Double): Boolean =
        distanceKm(userLat, userLng, lat, lng) <= NEARBY_RADIUS_KM

    /** A short human label like "12 km away" / "in your area". */
    fun label(distanceKm: Double): String = when {
        distanceKm <= NEARBY_RADIUS_KM -> "${distanceKm.toInt()} km · in your area"
        else -> "${distanceKm.toInt()} km away"
    }
}
