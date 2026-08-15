package com.example.util

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object NavigationUtils {

    /**
     * Launch Turn-by-Turn GPS Navigation directly in Google Maps or fallback to browser
     */
    fun openGoogleMapsNavigation(
        context: Context,
        destinationLat: Double?,
        destinationLng: Double?,
        destinationAddress: String = "",
        destinationName: String = ""
    ) {
        try {
            if (destinationLat != null && destinationLng != null && !destinationLat.isNaN() && !destinationLng.isNaN()) {
                // Try Google Maps Navigation URI scheme (turn-by-turn)
                val navUri = Uri.parse("google.navigation:q=$destinationLat,$destinationLng&mode=d")
                val mapIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                    setPackage("com.google.android.apps.maps")
                }

                if (mapIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mapIntent)
                    return
                }

                // Fallback 1: Generic geo or maps.google.com URI
                val webNavUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destinationLat,$destinationLng&travelmode=driving")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, webNavUri)
                context.startActivity(fallbackIntent)
            } else if (destinationAddress.isNotBlank()) {
                // Navigate by street address
                val encodedAddress = Uri.encode(destinationAddress)
                val addressNavUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedAddress&travelmode=driving")
                val fallbackIntent = Intent(Intent.ACTION_VIEW, addressNavUri)
                context.startActivity(fallbackIntent)
            } else {
                Toast.makeText(context, "No hay coordenadas ni dirección para este cliente", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // General fallback
            try {
                val query = if (destinationLat != null && destinationLng != null) {
                    "$destinationLat,$destinationLng"
                } else {
                    Uri.encode(destinationAddress)
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$query"))
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "No se pudo abrir la aplicación de mapas: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Calculate approximate distance between two points in meters
     */
    fun calculateDistanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(endLat - startLat)
        val dLon = Math.toRadians(endLng - startLng)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Format distance human-readably (e.g. "350 m", "1.8 km")
     */
    fun formatDistance(
        currentLocation: Location?,
        targetLat: Double?,
        targetLng: Double?
    ): String? {
        if (currentLocation == null || targetLat == null || targetLng == null || targetLat.isNaN() || targetLng.isNaN()) {
            return null
        }
        val distanceMeters = calculateDistanceMeters(
            currentLocation.latitude,
            currentLocation.longitude,
            targetLat,
            targetLng
        )

        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
        }
    }
}
