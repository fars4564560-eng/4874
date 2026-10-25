package com.example.privacyshield

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell

class MainActivity : Activity() {

    private lateinit var rootStatus: TextView
    private lateinit var monitorStatus: TextView
    private lateinit var shieldStatus: TextView
    private lateinit var message: TextView
    private lateinit var dimLabel: TextView
    private lateinit var toggle: Button

    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        buildUi()
        refreshRootStatus()
        recoverIfNeeded()

        val wanted = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 41)
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    /** إن بقيت إعدادات شاشة معدّلة من إغلاق غير طبيعي، نعيدها تلقائياً. */
    private fun recoverIfNeeded() {
        if (!ShieldService.running && !Prefs.manual(this) && DisplayShield.hasPendingRestore()) {
            DisplayShield.off()
            message.text = "تم استرجاع إعدادات الشاشة بعد إغلاق غير طبيعي سابق."
        }
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(16, 23, 36))
        }

        val title = TextView(this).apply {
            text = "Privacy Shield"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "تقليل وضوح الشاشة بالتعتيم وتدرّج الرمادي عند ظهور وجه ثانٍ"
            textSize = 15f
            setTextColor(Color.rgb(194, 205, 222))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(22))
        }

        rootStatus = makeText("جارٍ فحص صلاحية الروت…", 15f, Color.WHITE)
        monitorStatus = makeText("", 15f, Color.WHITE)
        shieldStatus = makeText("", 15f, Color.WHITE)
        message = makeText("", 14f, Color.rgb(194, 205, 222))

        // شدة التعتيم
        dimLabel = makeText("", 15f, Color.WHITE)
        val seek = SeekBar(this).apply {
            max = 100
            progress = Prefs.dim(this@MainActivity)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    dimLabel.text = "شدة التعتيم: $value%"
                    if (fromUser) Prefs.setDim(this@MainActivity, value)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        dimLabel.text = "شدة التعتيم: ${seek.progress}%"

        val gray = Switch(this).apply {
            text = "تحويل الشاشة إلى رمادي"
            setTextColor(Color.WHITE)
            isChecked = Prefs.gray(this@MainActivity)
            setOnCheckedChangeListener { _, checked -> Prefs.setGray(this@MainActivity, checked) }
        }

        toggle = makeButton("تشغيل مراقبة الوجوه")
        toggle.setOnClickListener { toggleMonitoring() }

        val test = makeButton("اختبار التعتيم لمدة 5 ثوانٍ")
        test.setOnClickListener {
            if (ShieldService.running) {
                message.text = "أوقف المراقبة قبل الاختبار لتجنب تعارض الحالات."
                return@setOnClickListener
            }
            DisplayShield.on(Prefs.dim(this), Prefs.gray(this))
            message.text = "تم طلب التعتيم. إن لم تتغير الشاشة فتحقق من الروت."
            handler.postDelayed({ DisplayShield.off() }, 5000)
        }

        val manual = makeButton("تفعيل / إيقاف الحماية يدوياً")
        manual.setOnClickListener {
            if (DisplayShield.active || DisplayShield.hasPendingRestore()) {
                Prefs.setManual(this, false)
                DisplayShield.off()
                message.text = "تم طلب إيقاف الحماية."
            } else {
                Prefs.setManual(this, true)
                DisplayShield.on(Prefs.dim(this), Prefs.gray(this))
                message.text = "تم طلب تفعيل الحماية يدوياً."
            }
        }

        val restore = makeButton("استعادة إعدادات الشاشة الآن")
        restore.setOnClickListener {
            Prefs.setManual(this, false)
            DisplayShield.off()
            message.text = "تم طلب استعادة الإعدادات الأصلية."
        }

        val note = makeText(
            "مهم: هذا تقريب برمجي وليس Privacy Display حقيقياً. لا يمنع الرؤية من الجوانب، " +
                "وقد يقلل السطوع والألوان لك أيضاً. لا تعتمد عليه لإخفاء معلومات حساسة.\n\n" +
                "نصيحة: أضف مربع Privacy Shield إلى الإعدادات السريعة (اسحب الإشعارات ← أيقونة القلم) لتفعيل الحماية بلمسة واحدة.",
            14f, Color.rgb(255, 213, 145)
        )

        page.addView(title, matchWrap())
        page.addView(subtitle, matchWrap())
        page.addView(rootStatus, matchWrap())
        page.addView(monitorStatus, matchWrap().apply { topMargin = dp(4) })
        page.addView(shieldStatus, matchWrap().apply { topMargin = dp(4) })
        page.addView(message, matchWrap().apply { topMargin = dp(10); bottomMargin = dp(14) })
        page.addView(dimLabel, matchWrap())
        page.addView(seek, matchWrap().apply { bottomMargin = dp(6) })
        page.addView(gray, matchWrap().apply { bottomMargin = dp(18) })
        page.addView(toggle, matchWrap().apply { bottomMargin = dp(10) })
        page.addView(manual, matchWrap().apply { bottomMargin = dp(10) })
        page.addView(test, matchWrap().apply { bottomMargin = dp(10) })
        page.addView(restore, matchWrap())
        page.addView(note, matchWrap().apply { topMargin = dp(22) })

        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun toggleMonitoring() {
        if (!hasCameraPermission()) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
            message.text = "اسمح باستخدام الكاميرا أولاً."
            return
        }
        val intent = Intent(this, ShieldService::class.java)
        if (!ShieldService.running) ContextCompat.startForegroundService(this, intent)
        else stopService(intent)
        handler.postDelayed({ render() }, 300)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            message.text = if (hasCameraPermission()) "إذن الكاميرا متاح. يمكنك تشغيل المراقبة."
            else "إذن الكاميرا مرفوض؛ لن تعمل مراقبة الوجوه."
        }
    }

    private fun refreshRootStatus() {
        Shell.getShell { shell ->
            runOnUiThread {
                rootStatus.text = if (shell.isRoot) "صلاحية الروت: متاحة ✓" else "صلاحية الروت: غير متاحة ✕"
                rootStatus.setTextColor(
                    if (shell.isRoot) Color.rgb(129, 230, 170) else Color.rgb(255, 135, 135)
                )
            }
        }
    }

    private fun render() {
        val watching = ShieldService.running
        toggle.text = if (watching) "إيقاف مراقبة الوجوه" else "تشغيل مراقبة الوجوه"
        monitorStatus.text = if (watching) "المراقبة: تعمل" else "المراقبة: متوقفة"
        shieldStatus.text = if (DisplayShield.active) "الحماية: مفعّلة الآن" else "الحماية: غير مفعّلة"
    }

    private fun makeText(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun makeButton(value: String) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(49, 91, 170))
        minHeight = dp(52)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
