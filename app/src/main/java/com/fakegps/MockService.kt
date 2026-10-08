package com.fakegps

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class MockUiState(
    val running: Boolean = false,
    val current: LatLon? = null,
    val progress: Float = 0f,
    val finished: Boolean = false,
    val routeMode: Boolean = false,
    val error: String? = null,
)

/** Shared state between UI and service. */
object MockController {
    val state = MutableStateFlow(MockUiState())
    val speedKmh = MutableStateFlow(40f)
    @Volatile var fixed: LatLon? = null
    @Volatile var route: List<LatLon> = emptyList()
}

class MockService : Service() {
    companion object {
        const val ACTION_SPOT = "spot"
        const val ACTION_ROUTE = "route"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "mock"

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, MockService::class.java).setAction(action)
            if (action == ACTION_STOP) ctx.startService(i) else ctx.startForegroundService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var engine: MockEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopMock()
            ACTION_SPOT, ACTION_ROUTE -> {
                try {
                    goForeground()
                } catch (e: SecurityException) {
                    // Missing location permission (required for FOREGROUND_SERVICE_TYPE_LOCATION)
                    MockController.state.value = MockUiState(
                        error = "Location permission is required. Allow it in app settings."
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                begin(routeMode = intent.action == ACTION_ROUTE)
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(routeMode: Boolean) {
        job?.cancel()
        try {
            if (engine == null) engine = MockEngine(this).also { it.start() }
        } catch (e: SecurityException) {
            MockController.state.value = MockUiState(
                error = "Select this app under Developer options > Select mock location app"
            )
            stopMock(keepError = true)
            return
        }
        val eng = engine!!

        job = scope.launch {
            val pts = MockController.route
            val cum = if (routeMode && pts.size >= 2) Geo.cumulative(pts) else null
            var travelled = 0.0

            MockController.state.value = MockUiState(running = true, routeMode = cum != null)
            while (isActive) {
                val speedMps = MockController.speedKmh.value / 3.6
                var bearing = 0f
                var speedOut = 0f
                val pos: LatLon?

                if (cum != null && travelled < cum.last()) {
                    travelled += speedMps // tick = 1 second
                    val (p, b) = Geo.positionAt(pts, cum, travelled.coerceAtMost(cum.last()))
                    pos = p; bearing = b; speedOut = speedMps.toFloat()
                    if (travelled >= cum.last()) MockController.fixed = pts.last() // park at destination
                    MockController.state.update {
                        it.copy(
                            running = true, current = p,
                            progress = (travelled / cum.last()).toFloat().coerceAtMost(1f),
                            finished = travelled >= cum.last()
                        )
                    }
                } else {
                    pos = MockController.fixed
                    MockController.state.update { it.copy(running = true, current = pos) }
                }

                if (pos != null) {
                    try {
                        eng.push(pos, bearing, speedOut)
                    } catch (e: Exception) {
                        MockController.state.value = MockUiState(error = e.message)
                        stopMock(keepError = true)
                        return@launch
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopMock(keepError: Boolean = false) {
        job?.cancel()
        engine?.stop()
        engine = null
        if (!keepError) MockController.state.value = MockUiState()
        else MockController.state.update { it.copy(running = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Mock location", NotificationManager.IMPORTANCE_LOW)
        )

        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, MockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("Fake GPS running")
            .setContentText("Tap to open · expand to stop")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stop)
            .build()

        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onDestroy() {
        scope.cancel()
        engine?.stop()
        super.onDestroy()
    }
}