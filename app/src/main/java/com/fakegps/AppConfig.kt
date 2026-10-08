package com.fakegps

import android.content.Context
import com.fakegps.R
import org.json.JSONArray
import kotlin.random.Random

object AppConfig {
    @Volatile
    var contactEmail: String = ""
        private set

    /** Call once at launch. */
    fun init(ctx: Context) {
        contactEmail = runCatching {
            val json = ctx.resources
                .openRawResource(R.raw.contact_emails)
                .bufferedReader()
                .use { it.readText() }

            val emails = JSONArray(json)
            if (emails.length() > 0) {
                emails.getString(Random.nextInt(emails.length()))
            } else {
                ""
            }
        }.getOrDefault("")
    }

    val userAgent: String
        get() = "FakeGps/1.0 (contact: ${contactEmail.ifBlank { "unknown" }})"
}