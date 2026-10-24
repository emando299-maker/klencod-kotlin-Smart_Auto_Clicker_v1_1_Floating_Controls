package com.smartautoclicker.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class AutoClickService : AccessibilityService() {
    private var lastClickTime = 0L
    private var overlayView: View? = null
    private var speedLabel: TextView? = null
    private var statusLabel: TextView? = null
    private var windowManager: WindowManager? = null
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val clickDelay: Long
        get() = prefs.getInt("click_delay_ms", 1000).toLong().coerceIn(100L, 5000L)

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < clickDelay) return
        if (!prefs.getBoolean("running", false)) {
            updateStatus("متوقف")
            return
        }
        val word = prefs.getString("word", "") ?: ""
        if (word.isBlank()) {
            updateStatus("اكتب كلمة البحث")
            return
        }
        val root = rootInActiveWindow ?: return
        if (searchAndClick(root, word)) {
            lastClickTime = now
            updateStatus("يعمل • ${clickDelay}ms")
        }
    }

    private fun searchAndClick(node: AccessibilityNodeInfo, word: String): Boolean {
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        if (text.contains(word, ignoreCase = true) || description.contains(word, ignoreCase = true)) {
            var target: AccessibilityNodeInfo? = node
            var depth = 0
            while (target != null && depth < 10) {
                if (target.isClickable) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                target = target.parent
                depth++
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (searchAndClick(child, word)) return true
        }
        return false
    }

    private fun showFloatingWindow() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(28, 35, 45))
        }
        val heading = TextView(this).apply {
            text = "النقر التلقائي"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        panel.addView(heading)
        statusLabel = TextView(this).apply {
            text = if (prefs.getBoolean("running", false)) "يعمل" else "متوقف"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 13f
        }
        panel.addView(statusLabel)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(Button(this).apply {
            text = "تشغيل"; isAllCaps = false
            setOnClickListener {
                if (prefs.getString("word", "").isNullOrBlank()) {
                    Toast.makeText(this@AutoClickService, "افتح التطبيق واحفظ كلمة البحث أولًا", Toast.LENGTH_SHORT).show()
                    updateStatus("لا توجد كلمة بحث")
                } else {
                    prefs.edit().putBoolean("running", true).apply()
                    updateStatus("يعمل")
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "إيقاف"; isAllCaps = false
            setOnClickListener {
                prefs.edit().putBoolean("running", false).apply()
                updateStatus("متوقف")
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        panel.addView(controls)

        val delay = clickDelay.toInt()
        speedLabel = TextView(this).apply {
            text = "الفاصل: ${delay}ms"
            setTextColor(Color.WHITE); textSize = 13f; gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        panel.addView(speedLabel)
        panel.addView(SeekBar(this).apply {
            max = 4900
            progress = delay - 100
            contentDescription = "سرعة الضغط من 100 إلى 5000 ملي ثانية"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val ms = progress + 100
                    prefs.edit().putInt("click_delay_ms", ms).apply()
                    speedLabel?.text = "الفاصل: ${ms}ms"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        panel.addView(TextView(this).apply {
            text = "اسحب العنوان لتحريك النافذة"
            setTextColor(Color.LTGRAY); textSize = 11f; gravity = Gravity.CENTER
        })

        val params = WindowManager.LayoutParams(
            dp(250), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(100) }

        heading.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        params.x = startX - (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        try { windowManager?.updateViewLayout(panel, params) } catch (_: Exception) {}
                        return true
                    }
                }
                return false
            }
        })
        try {
            windowManager?.addView(panel, params)
            overlayView = panel
        } catch (_: Exception) { overlayView = null }
    }

    private fun updateStatus(message: String) { statusLabel?.text = message }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() {
        prefs.edit().putBoolean("running", false).apply()
        updateStatus("متوقف")
    }

    override fun onDestroy() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null
        super.onDestroy()
    }
}
