package com.mehuljain.airshare.service

import android.content.Context
import android.os.Build
import com.mehuljain.airshare.printer.PrinterConfig
import java.util.UUID

/** SharedPreferences persistence of [PrinterConfig]. */
object PrinterPrefs {
    private const val FILE = "airshare_prefs"

    fun load(context: Context): PrinterConfig {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val uuid = sp.getString("uuid", null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val name = sp.getString("name", null)?.let(::sanitizeName)?.takeIf { it.isNotEmpty() } ?: defaultName()
        val cfg = PrinterConfig(
            name = name,
            port = validPort(sp.getInt("port", PrinterConfig.DEFAULT_PORT)),
            uuid = uuid,
            location = sp.getString("location", "") ?: "",
            makeAndModel = sp.getString("makeAndModel", null) ?: "AirShare Photo & Document Receiver",
            compatibilityMode = sp.getBoolean("compatibilityMode", true),
            savePhotosAsImages = sp.getBoolean("savePhotosAsImages", true),
            extractPdfPhotos = sp.getBoolean("extractPdfPhotos", true),
            autoAccept = sp.getBoolean("autoAccept", false),
            autoOffMinutes = sp.getInt("autoOffMinutes", 10),
        )
        if (!sp.contains("uuid") || !sp.contains("name")) save(context, cfg)
        return cfg
    }

    fun save(context: Context, config: PrinterConfig) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("name", config.name)
            .putInt("port", config.port)
            .putString("uuid", config.uuid)
            .putString("location", config.location)
            .putString("makeAndModel", config.makeAndModel)
            .putBoolean("compatibilityMode", config.compatibilityMode)
            .putBoolean("savePhotosAsImages", config.savePhotosAsImages)
            .putBoolean("extractPdfPhotos", config.extractPdfPhotos)
            .putBoolean("autoAccept", config.autoAccept)
            .putInt("autoOffMinutes", config.autoOffMinutes)
            .apply()
    }

    fun validPort(p: Int): Int = if (p in 1024..65535) p else PrinterConfig.DEFAULT_PORT

    fun sanitizeName(s: String): String =
        s.filter { !it.isISOControl() }.trim().take(63).trim()

    private fun defaultName(): String =
        sanitizeName("AirShare (${Build.MODEL ?: "Android"})").ifEmpty { "AirShare" }
}
