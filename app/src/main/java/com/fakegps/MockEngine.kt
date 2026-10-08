package com.fakegps

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import kotlin.math.*

/** Thin wrapper over LocationManager test providers. */
class MockEngine(ctx: Context) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    /** Throws SecurityException if this app is not selected as the mock location app. */
    fun start() {
        providers.forEach { p ->
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(
                p, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            lm.setTestProviderEnabled(p, true)
        }
    }

    fun push(p: LatLon, bearing: Float, speedMps: Float) {
        providers.forEach { name ->
            val loc = Location(name).apply {
                latitude = p.lat
                longitude = p.lon
                altitude = 0.0
                accuracy = 3f
                this.bearing = bearing
                speed = speedMps
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            lm.setTestProviderLocation(name, loc)
        }
    }

    fun stop() {
        providers.forEach { runCatching { lm.removeTestProvider(it) } }
    }
}

object Geo {
    fun distance(a: LatLon, b: LatLon): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun bearing(a: LatLon, b: LatLon): Float {
        val y = sin(Math.toRadians(b.lon - a.lon)) * cos(Math.toRadians(b.lat))
        val x = cos(Math.toRadians(a.lat)) * sin(Math.toRadians(b.lat)) -
            sin(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * cos(Math.toRadians(b.lon - a.lon))
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    fun cumulative(pts: List<LatLon>): DoubleArray {
        val c = DoubleArray(pts.size)
        for (i in 1 until pts.size) c[i] = c[i - 1] + distance(pts[i - 1], pts[i])
        return c
    }

    /** Point + bearing after travelling [d] metres along the polyline. */
    fun positionAt(pts: List<LatLon>, cum: DoubleArray, d: Double): Pair<LatLon, Float> {
        val idx = cum.binarySearch(d).let { if (it >= 0) it else -it - 2 }.coerceIn(0, pts.size - 2)
        val a = pts[idx]
        val b = pts[idx + 1]
        val seg = cum[idx + 1] - cum[idx]
        val t = if (seg > 0) ((d - cum[idx]) / seg).coerceIn(0.0, 1.0) else 0.0
        return LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t) to bearing(a, b)
    }
}
