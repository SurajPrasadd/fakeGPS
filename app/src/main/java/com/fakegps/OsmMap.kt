package com.fakegps

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val GOOGLE_BLUE = Color.parseColor("#1A73E8")
private val GOOGLE_RED = Color.parseColor("#EA4335")

/** Lets Compose buttons drive the map. */
class MapCommands {
    internal var map: MapView? = null
    fun zoomIn() { map?.controller?.zoomIn() }
    fun zoomOut() { map?.controller?.zoomOut() }
    fun center(p: LatLon) { map?.controller?.animateTo(GeoPoint(p.lat, p.lon)) }
}

@Composable
fun OsmMap(
    modifier: Modifier,
    commands: MapCommands,
    focus: LatLon?,
    fitTo: List<LatLon>,
    pin: LatLon?,
    start: LatLon?,
    end: LatLon?,
    route: List<LatLon>,
    current: LatLon?,
    onTap: (LatLon) -> Unit,
) {
    val ctx = LocalContext.current
    val tap by rememberUpdatedState(onTap)

    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
            commands.map = this
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); commands.map = null; map.onDetach() }
    }

    LaunchedEffect(focus) {
        focus?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon), 16.0, 800L) }
    }
    LaunchedEffect(fitTo) {
        if (fitTo.size >= 2) map.post {
            val box = BoundingBox.fromGeoPoints(fitTo.map { GeoPoint(it.lat, it.lon) })
            map.zoomToBoundingBox(box, true, 160)
        }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { m ->
        val c = m.context
        m.overlays.clear()
        m.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                tap(LatLon(p.latitude, p.longitude)); return true
            }
            override fun longPressHelper(p: GeoPoint) = false
        }))
        if (route.size >= 2) {
            val pts = route.map { GeoPoint(it.lat, it.lon) }
            // white casing + blue line, like Google Maps
            m.overlays.add(line(pts, Color.WHITE, 20f))
            m.overlays.add(line(pts, GOOGLE_BLUE, 12f))
        }
        start?.let { m.overlays.add(dotMarker(m, it, "Start", dot(c, Color.WHITE, GOOGLE_BLUE, 18, 4))) }
        end?.let { m.overlays.add(pinMarker(m, it, "Destination", GOOGLE_RED)) }
        pin?.let { m.overlays.add(pinMarker(m, it, "Fake location", GOOGLE_RED)) }
        current?.let { m.overlays.add(dotMarker(m, it, "Mock position", blueDot(c))) }
        m.invalidate()
    })
}

private fun line(pts: List<GeoPoint>, color: Int, width: Float) = Polyline().apply {
    setPoints(pts)
    outlinePaint.color = color
    outlinePaint.strokeWidth = width
    outlinePaint.strokeCap = Paint.Cap.ROUND
    outlinePaint.strokeJoin = Paint.Join.ROUND
}

private fun pinMarker(m: MapView, p: LatLon, title: String, color: Int) = Marker(m).apply {
    position = GeoPoint(p.lat, p.lon)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    this.title = title
    icon = icon?.mutate()?.also { DrawableCompat.setTint(it, color) }
}

private fun dotMarker(m: MapView, p: LatLon, title: String, d: Drawable) = Marker(m).apply {
    position = GeoPoint(p.lat, p.lon)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    this.title = title
    icon = d
}

private fun dot(ctx: Context, fill: Int, stroke: Int, sizeDp: Int, strokeDp: Int): GradientDrawable {
    val d = ctx.resources.displayMetrics.density
    val s = (sizeDp * d).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke((strokeDp * d).toInt(), stroke)
        setSize(s, s)
    }
}

/** Blue dot with translucent halo, like Google's "my location". */
private fun blueDot(ctx: Context): Drawable {
    val d = ctx.resources.displayMetrics.density
    val halo = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(60, 26, 115, 232))
    }
    val core = dot(ctx, GOOGLE_BLUE, Color.WHITE, 20, 3)
    return LayerDrawable(arrayOf(halo, core)).apply {
        val big = (56 * d).toInt()
        val small = (20 * d).toInt()
        setLayerSize(0, big, big); setLayerGravity(0, Gravity.CENTER)
        setLayerSize(1, small, small); setLayerGravity(1, Gravity.CENTER)
    }
}
