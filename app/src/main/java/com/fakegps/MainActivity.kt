package com.fakegps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )

        Configuration.getInstance().apply {
            load(this@MainActivity, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osm")
            osmdroidTileCache = File(cacheDir, "osm/tiles")
        }
        setContent { FakeGpsTheme { FakeGpsScreen() } }
    }
}

enum class PickMode(val label: String, val icon: ImageVector, val hint: String) {
    PIN("Pin", Icons.Default.Place, "Search a place to fake"),
    START("Start", Icons.Default.TripOrigin, "Search starting point"),
    END("Destination", Icons.Default.Flag, "Search destination"),
}

private val GreenEta = Color(0xFF188038)
private val PinRed = Color(0xFFEA4335)

private fun fmtDist(m: Double) = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)
private fun fmtDur(s: Double): String {
    val min = (s / 60).roundToInt().coerceAtLeast(1)
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
private fun fmtPt(p: LatLon) = "%.5f, %.5f".format(p.lat, p.lon)

@Composable
fun FakeGpsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val cmds = remember { MapCommands() }
    val mock by MockController.state.collectAsStateWithLifecycle()
    val speed by MockController.speedKmh.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(PickMode.PIN) }
    var pin by remember { mutableStateOf<LatLon?>(null) }
    var start by remember { mutableStateOf<LatLon?>(null) }
    var end by remember { mutableStateOf<LatLon?>(null) }
    var route by remember { mutableStateOf<List<LatLon>>(emptyList()) }
    var focus by remember { mutableStateOf<LatLon?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    val routeDist = remember(route) { if (route.size >= 2) Geo.cumulative(route).last() else 0.0 }

    // Ask for missing permissions right when the user taps a start button
    val ensureSpot = rememberEnsurePermissions {
        MockController.fixed = pin
        MockService.send(ctx, MockService.ACTION_SPOT)
    }
    val ensureRoute = rememberEnsurePermissions {
        MockController.route = route
        MockController.fixed = route.firstOrNull()
        MockService.send(ctx, MockService.ACTION_ROUTE)
    }

    // Shows "Select mock location app" dialog when the service reports it
    MockLocationSetupDialog(mock.error) {
        MockController.state.update { it.copy(error = null) }
    }

    fun place(p: LatLon) {
        when (mode) {
            PickMode.PIN -> pin = p
            PickMode.START -> { start = p; route = emptyList() }
            PickMode.END -> { end = p; route = emptyList() }
        }
    }

    fun search() {
        if (query.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            busy = true; msg = null
            runCatching { Api.search(query) }
                .onSuccess { results = it; if (it.isEmpty()) msg = "No results found" }
                .onFailure { msg = "Search failed: ${it.message}" }
            busy = false
        }
    }

    fun findRoute() {
        scope.launch {
            busy = true; msg = null
            runCatching { Api.route(start!!, end!!) }
                .onSuccess { route = it }
                .onFailure { msg = "Route failed: ${it.message}" }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            Modifier.fillMaxSize(),
            commands = cmds, focus = focus, fitTo = route,
            pin = pin, start = start, end = end, route = route, current = mock.current,
            onTap = { results = emptyList(); focusManager.clearFocus(); place(it) }
        )

        // ───────── Top: search bar + chips ─────────
        Column(
            Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp), shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Search, null,
                        Modifier.padding(start = 18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f), singleLine = true,
                        placeholder = { Text(mode.hint) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(22.dp), strokeWidth = 2.5.dp)
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; results = emptyList(); msg = null }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        results.forEachIndexed { i, r ->
                            val title = r.name.substringBefore(",")
                            val sub = r.name.substringAfter(",", "").trim()
                            ListItem(
                                modifier = Modifier.clickable {
                                    place(r.point); focus = r.point; results = emptyList()
                                },
                                leadingContent = {
                                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            )
                            if (i < results.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m, onClick = { mode = m },
                        label = { Text(m.label) },
                        leadingIcon = { Icon(m.icon, null, Modifier.size(18.dp)) },
                        elevation = FilterChipDefaults.filterChipElevation(elevation = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                }
            }
        }

        // ───────── Bottom: FABs + sheet ─────────
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Column(
                Modifier.padding(end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                val fabColor = MaterialTheme.colorScheme.surface
                SmallFloatingActionButton(
                    onClick = { cmds.zoomIn() }, containerColor = fabColor,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) { Icon(Icons.Default.Add, "Zoom in") }
                SmallFloatingActionButton(
                    onClick = { cmds.zoomOut() }, containerColor = fabColor,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) { Icon(Icons.Default.Remove, "Zoom out") }
                FloatingActionButton(
                    onClick = { (mock.current ?: pin ?: start)?.let { cmds.center(it) } },
                    containerColor = fabColor, contentColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Default.MyLocation, "Center") }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally)
                            .size(32.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    when {
                        mock.running && mock.routeMode -> RunningPanel(
                            mock = mock, speed = speed, routeDist = routeDist,
                            onSpeed = { MockController.speedKmh.value = it },
                            onStop = { MockService.send(ctx, MockService.ACTION_STOP) }
                        )
                        route.size >= 2 -> RoutePanel(
                            dist = routeDist, speed = speed,
                            onSpeed = { MockController.speedKmh.value = it },
                            onStart = ensureRoute,
                            onClear = { route = emptyList() }
                        )
                        else -> IdlePanel(
                            mode = mode, onMode = { mode = it },
                            pin = pin, start = start, end = end,
                            mocking = mock.running, busy = busy,
                            onSet = ensureSpot,
                            onStop = { MockService.send(ctx, MockService.ACTION_STOP) },
                            onDirections = { findRoute() }
                        )
                    }

                    (mock.error ?: msg)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String,
    selected: Boolean, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun IdlePanel(
    mode: PickMode, onMode: (PickMode) -> Unit,
    pin: LatLon?, start: LatLon?, end: LatLon?,
    mocking: Boolean, busy: Boolean,
    onSet: () -> Unit, onStop: () -> Unit, onDirections: () -> Unit
) {
    if (mocking) {
        Text("Fake location is active", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
    PlaceRow(Icons.Default.Place, PinRed, "Fake location", pin?.let(::fmtPt) ?: "Tap the map or search",
        mode == PickMode.PIN) { onMode(PickMode.PIN) }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    PlaceRow(Icons.Default.TripOrigin, MaterialTheme.colorScheme.primary, "Start", start?.let(::fmtPt) ?: "Choose starting point",
        mode == PickMode.START) { onMode(PickMode.START) }
    PlaceRow(Icons.Default.Flag, PinRed, "Destination", end?.let(::fmtPt) ?: "Choose destination",
        mode == PickMode.END) { onMode(PickMode.END) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSet, enabled = pin != null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (mocking) "Move here" else "Set location")
        }
        FilledTonalButton(
            onClick = onDirections, enabled = start != null && end != null && !busy,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Directions, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Directions")
        }
    }
    if (mocking) {
        OutlinedButton(
            onClick = onStop, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop mocking")
        }
    }
}

@Composable
private fun SpeedControl(speed: Float, onSpeed: (Float) -> Unit) {
    Column {
        Text("Speed: ${speed.toInt()} km/h", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = speed, onValueChange = onSpeed, valueRange = 5f..120f)
    }
}

@Composable
private fun RoutePanel(
    dist: Double, speed: Float, onSpeed: (Float) -> Unit, onStart: () -> Unit, onClear: () -> Unit
) {
    val eta = dist / (speed / 3.6)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(fmtDur(eta), style = MaterialTheme.typography.headlineMedium, color = GreenEta, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Text("(${fmtDist(dist)})", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
    }
    Text("Fastest route · simulated at your chosen speed", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    SpeedControl(speed, onSpeed)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start")
        }
        OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancel")
        }
    }
}

@Composable
private fun RunningPanel(
    mock: MockUiState, speed: Float, routeDist: Double,
    onSpeed: (Float) -> Unit, onStop: () -> Unit
) {
    val remaining = routeDist * (1 - mock.progress)
    Text(
        if (mock.finished) "You have arrived" else "${fmtDur(remaining / (speed / 3.6))} left",
        style = MaterialTheme.typography.headlineMedium, color = GreenEta, fontWeight = FontWeight.Medium
    )
    Text(
        (if (mock.finished) "Holding at destination" else "${fmtDist(remaining)} remaining") +
                (mock.current?.let { " · ${fmtPt(it)}" } ?: ""),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LinearProgressIndicator(
        progress = { mock.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
    )
    if (!mock.finished) SpeedControl(speed, onSpeed)
    Button(
        onClick = onStop, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
    ) {
        Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop")
    }
}