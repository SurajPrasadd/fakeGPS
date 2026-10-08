package com.fakegps

import com.fakegps.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LatLon(val lat: Double, val lon: Double)
data class Place(val name: String, val point: LatLon)

object Api {
    private val PIN = Regex("^\\d{6}$")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", AppConfig.userAgent)
        c.setRequestProperty("Accept-Language", "en")
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        return try {
            val code = c.responseCode
            if (code !in 200..299) {
                val body = c.errorStream?.bufferedReader()?.readText().orEmpty()
                error("HTTP $code: ${body.take(150)}")
            }
            c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String): List<Place> {
        val arr = JSONArray(json)
        return List(arr.length()) {
            val o = arr.getJSONObject(it)
            Place(
                o.getString("display_name"),
                LatLon(o.getString("lat").toDouble(), o.getString("lon").toDouble())
            )
        }
    }

    /** Address / PIN code -> places (OpenStreetMap Nominatim). */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val base = "https://nominatim.openstreetmap.org/search?format=json&limit=5"
        if (PIN.matches(q)) {
            val r = parse(get("$base&countrycodes=in&postalcode=$q"))
            if (r.isNotEmpty()) return@withContext r
            return@withContext parse(get("$base&q=" + URLEncoder.encode("$q, India", "UTF-8")))
        }
        parse(get("$base&q=" + URLEncoder.encode(q, "UTF-8")))
    }

    /** Start/end -> road polyline (OSRM public demo server, driving profile). */
    suspend fun route(a: LatLon, b: LatLon): List<LatLon> = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${a.lon},${a.lat};${b.lon},${b.lat}?overview=full&geometries=geojson"
        val json = JSONObject(get(url))
        check(json.getString("code") == "Ok") { "No route found" }
        val coords = json.getJSONArray("routes").getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        List(coords.length()) {
            val c = coords.getJSONArray(it) // [lon, lat]
            LatLon(c.getDouble(1), c.getDouble(0))
        }
    }
}