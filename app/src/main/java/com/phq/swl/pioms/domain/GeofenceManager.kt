package com.phq.swl.pioms.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeofenceCoordinate(
    val coordinateID: Int = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

data class GeofenceData(
    val geofenceID: Int = 0,
    val geofenceName: String = "",
    val coordinates: List<GeofenceCoordinate> = emptyList(),
    val radiusMeters: Double = 100.0,
)

object GeofenceManager {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate the distance between two geographic points using Haversine formula
     * @return Distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Check if a point is inside a polygon using ray casting algorithm
     */
    fun isPointInPolygon(
        latitude: Double,
        longitude: Double,
        polygon: List<GeofenceCoordinate>,
    ): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        var p1Lat = polygon.last().latitude
        var p1Lon = polygon.last().longitude

        for (i in polygon.indices) {
            val p2Lat = polygon[i].latitude
            val p2Lon = polygon[i].longitude

            if (longitude > minOf(p1Lon, p2Lon)) {
                if (longitude <= maxOf(p1Lon, p2Lon)) {
                    if (latitude <= maxOf(p1Lat, p2Lat)) {
                        if (p1Lon != p2Lon) {
                            val xinters = (longitude - p1Lon) * (p2Lat - p1Lat) / (p2Lon - p1Lon) + p1Lat
                            if (p1Lat == p2Lat || latitude <= xinters) {
                                inside = !inside
                            }
                        }
                    }
                }
            }
            p1Lat = p2Lat
            p1Lon = p2Lon
        }
        return inside
    }

    /**
     * Check if current location is inside the geofence
     */
    fun isInsideGeofence(
        currentLat: Double,
        currentLon: Double,
        geofenceData: GeofenceData,
    ): Boolean {
        // If polygon coordinates exist, use polygon-based check
        if (geofenceData.coordinates.isNotEmpty()) {
            return isPointInPolygon(currentLat, currentLon, geofenceData.coordinates)
        }

        // Fallback: if no coordinates defined, consider as inside
        return true
    }

    /**
     * Get the centroid of the geofence polygon
     */
    fun getGeofenceCentroid(coordinates: List<GeofenceCoordinate>): Pair<Double, Double>? {
        if (coordinates.isEmpty()) return null

        val avgLat = coordinates.map { it.latitude }.average()
        val avgLon = coordinates.map { it.longitude }.average()
        return Pair(avgLat, avgLon)
    }
}
