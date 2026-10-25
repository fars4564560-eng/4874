package com.example.privacyshield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** بعد إعادة التشغيل: إن بقي التعتيم/الرمادي مفعّلاً من جلسة سابقة، نعيد الإعدادات الأصلية. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!DisplayShield.hasPendingRestore()) return
        Prefs.setManual(context, false)
        val pending = goAsync()
        DisplayShield.off { pending.finish() }
    }
}
