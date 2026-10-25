package com.example.privacyshield

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * تقريب برمجي فقط: Extra Dim + تدرج رمادي. لا يستطيع تضييق زاوية الرؤية في OLED.
 *
 * - كل أوامر الروت تُنفَّذ على خيط واحد متسلسل (لا تعارض بين on/off).
 * - القيم الأصلية تُحفظ على القرص قبل أي تعديل، فإذا أُغلق التطبيق بالقوة
 *   أو أُعيد تشغيل الهاتف يمكن استرجاعها لاحقاً (hasPendingRestore + off).
 */
object DisplayShield {
    private const val TAG = "PrivacyShield"
    private const val STATE_FILE = "shield_state"
    private const val KEY_SAVED = "saved_settings"
    private const val RETRY_COOLDOWN_MS = 5_000L

    private val keys = listOf(
        "reduce_bright_colors_activated",
        "reduce_bright_colors_level",
        "accessibility_display_daltonizer_enabled",
        "accessibility_display_daltonizer"
    )

    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var appContext: Context

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    private var pendingOn = false

    @Volatile
    private var lastFailure = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

    /** هل توجد إعدادات أصلية محفوظة لم تُستعد بعد؟ */
    fun hasPendingRestore(): Boolean = prefs().contains(KEY_SAVED)

    private fun persist(values: Map<String, String>) {
        prefs().edit().putString(KEY_SAVED, JSONObject(values).toString()).commit()
    }

    private fun loadSaved(): Map<String, String>? {
        val raw = prefs().getString(KEY_SAVED, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            keys.associateWith { obj.optString(it, "null") }
        } catch (e: Exception) {
            null
        }
    }

    private fun readCurrent(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        for (key in keys) {
            val result = Shell.cmd("settings get secure $key").exec()
            if (!result.isSuccess) throw IllegalStateException("Could not read $key")
            values[key] = result.out.firstOrNull()?.trim().orEmpty().ifBlank { "null" }
        }
        return values
    }

    fun on(dimLevel: Int, grayscale: Boolean) {
        if (active || pendingOn) return
        if (SystemClock.elapsedRealtime() - lastFailure < RETRY_COOLDOWN_MS) return
        pendingOn = true
        worker.execute {
            try {
                if (!active) {
                    // لا نقرأ القيم الحالية إن كانت هناك قيم أصلية محفوظة من جلسة سابقة.
                    if (!hasPendingRestore()) persist(readCurrent())

                    val commands = mutableListOf(
                        "settings put secure reduce_bright_colors_level ${dimLevel.coerceIn(0, 100)}",
                        "settings put secure reduce_bright_colors_activated 1"
                    )
                    if (grayscale) {
                        commands += "settings put secure accessibility_display_daltonizer 0"
                        commands += "settings put secure accessibility_display_daltonizer_enabled 1"
                    }
                    val result = Shell.cmd(*commands.toTypedArray()).exec()
                    if (!result.isSuccess) throw IllegalStateException("Root settings command failed")
                    active = true
                }
            } catch (t: Throwable) {
                lastFailure = SystemClock.elapsedRealtime()
                Log.e(TAG, "Unable to enable display approximation", t)
            } finally {
                pendingOn = false
            }
        }
    }

    /** يستعيد الإعدادات الأصلية. [onDone] يُستدعى بعد انتهاء المحاولة (نجحت أو فشلت). */
    fun off(onDone: (() -> Unit)? = null) {
        worker.execute {
            try {
                restoreNow()
            } finally {
                onDone?.invoke()
            }
        }
    }

    private fun restoreNow() {
        val original = loadSaved()
        if (original == null) {
            active = false
            return
        }
        try {
            val commands = original.map { (key, value) ->
                if (value == "null" || value.isBlank()) "settings delete secure $key"
                else "settings put secure $key '$value'"
            }
            val result = Shell.cmd(*commands.toTypedArray()).exec()
            if (!result.isSuccess) throw IllegalStateException("Could not restore original settings")
            prefs().edit().remove(KEY_SAVED).commit()
            active = false
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to restore display settings", t)
        }
    }
}
