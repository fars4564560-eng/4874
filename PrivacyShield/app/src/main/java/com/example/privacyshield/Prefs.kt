package com.example.privacyshield

import android.content.Context

/** إعدادات المستخدم: شدة التعتيم وتفعيل الرمادي. */
object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("shield_prefs", Context.MODE_PRIVATE)

    fun dim(c: Context): Int = sp(c).getInt("dim", 90)
    fun setDim(c: Context, value: Int) = sp(c).edit().putInt("dim", value.coerceIn(0, 100)).apply()

    fun gray(c: Context): Boolean = sp(c).getBoolean("gray", true)
    fun setGray(c: Context, value: Boolean) = sp(c).edit().putBoolean("gray", value).apply()

    /** هل فُعّلت الحماية يدوياً (من المربع السريع أو الزر)؟ لمنع الاسترجاع التلقائي لها. */
    fun manual(c: Context): Boolean = sp(c).getBoolean("manual", false)
    fun setManual(c: Context, value: Boolean) = sp(c).edit().putBoolean("manual", value).apply()
}
