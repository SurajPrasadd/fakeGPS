package com.fakegps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private fun granted(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun locationGranted(ctx: Context) =
    granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun notifGranted(ctx: Context) =
    Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

private fun appSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private enum class Missing { NONE, LOCATION, NOTIFICATION }

/**
 * Returns a function to call from your Start / Set buttons.
 * Asks for whatever is missing, then runs [onReady].
 *  - Location is required (foreground service of type "location" needs it).
 *  - Notifications are strongly recommended (Stop button), so the user may continue without.
 */
@Composable
fun rememberEnsurePermissions(onReady: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var missing by remember { mutableStateOf(Missing.NONE) }
    val ready by rememberUpdatedState(onReady)

    fun evaluate() {
        when {
            !locationGranted(ctx) -> missing = Missing.LOCATION
            !notifGranted(ctx) -> missing = Missing.NOTIFICATION
            else -> ready()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { evaluate() }

    when (missing) {
        Missing.LOCATION -> AlertDialog(
            onDismissRequest = { missing = Missing.NONE },
            title = { Text("Location permission needed") },
            text = { Text("Android requires location permission for an app that runs a location service. Please allow it in app settings.") },
            confirmButton = {
                TextButton(onClick = { missing = Missing.NONE; appSettings(ctx) }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { missing = Missing.NONE }) { Text("Cancel") }
            },
        )
        Missing.NOTIFICATION -> AlertDialog(
            onDismissRequest = { missing = Missing.NONE },
            title = { Text("Notification permission needed") },
            text = { Text("Without notifications you can't stop mock location from the notification shade.") },
            confirmButton = {
                TextButton(onClick = { missing = Missing.NONE; appSettings(ctx) }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { missing = Missing.NONE; ready() }) { Text("Continue anyway") }
            },
        )
        Missing.NONE -> Unit
    }

    return {
        val needed = buildList {
            if (!locationGranted(ctx)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!notifGranted(ctx)) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) ready() else launcher.launch(needed.toTypedArray())
    }
}

/** Shown when MockService reports this app is not the selected mock location app. */
@Composable
fun MockLocationSetupDialog(error: String?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    if (error?.contains("mock location app", ignoreCase = true) != true) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select mock location app") },
        text = { Text("Open Developer options, tap \"Select mock location app\", choose this app, then try again.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }) { Text("Open Developer options") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}