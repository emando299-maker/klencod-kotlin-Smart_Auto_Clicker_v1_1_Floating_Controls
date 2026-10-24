package com.smartautoclicker.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var wordInput: EditText
    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences("settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 17, 23)
        window.navigationBarColor = Color.rgb(13, 17, 23)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(13, 17, 23))
        }

        fun addLabel(text: String, size: Float) {
            root.addView(TextView(this).apply {
                this.text = text
                textSize = size
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 12)
            })
        }

        fun addButton(text: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }

        addLabel("النقر التلقائي", 26f)
        addLabel("اكتب الكلمة المطلوب البحث عنها", 16f)
        addLabel("بعد تفعيل خدمة إمكانية الوصول ستظهر نافذة عائمة للتحكم في التشغيل والسرعة.", 13f)

        wordInput = EditText(this).apply {
            hint = "مثال: تخطي"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 18f
            maxLines = 1  // تم التعديل هنا لتتوافق مع الإصدارات الحديثة
            setText(prefs.getString("word", ""))
        }

        root.addView(wordInput)

        status = TextView(this).apply {
            text = "الحالة: متوقف"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 18, 0, 18)
        }

        addButton("حفظ الكلمة") {
            saveWord()
        }

        addButton("إعدادات إمكانية الوصول / تفعيل النافذة العائمة") {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        addButton("تشغيل البحث") {
            if (!saveWord()) return@addButton

            if (isServiceEnabled()) {
                prefs.edit().putBoolean("running", true).apply()
                status.text = "الخدمة مفعّلة — البحث عن الكلمة جارٍ"
            } else {
                status.text =
                    "فعّل خدمة النقر التلقائي من إعدادات إمكانية الوصول"
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        }

        addButton("إيقاف") {
            prefs.edit().putBoolean("running", false).apply()
            status.text = "الحالة: متوقف"
        }

        root.addView(status)

        setContentView(ScrollView(this).apply {
            addView(root)
        })
    }

    private fun saveWord(): Boolean {
        val word = wordInput.text.toString().trim()

        if (word.isEmpty()) {
            wordInput.error = "اكتب الكلمة أولًا"
            return false
        }

        prefs.edit().putString("word", word).apply()
        status.text = "تم حفظ الكلمة: $word"
        return true
    }

    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE)
                as android.view.accessibility.AccessibilityManager

        return manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name.endsWith("AutoClickService")
        }
    }
}