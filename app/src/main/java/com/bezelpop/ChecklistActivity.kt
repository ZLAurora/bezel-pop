package com.bezelpop

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Checks {
    fun serviceEnabled(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(c, KeyService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun batteryUnrestricted(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
}

class ChecklistActivity : Activity() {

    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var s3: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val sv = ScrollView(this).apply { fitsSystemWindows = true; addView(box) }
        setContentView(sv)

        fun title(t: String) = TextView(this).apply {
            text = t; textSize = 17f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(20), 0, dp(2))
        }
        fun body(t: String) = TextView(this).apply { text = t; textSize = 14f }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }

        box.addView(TextView(this).apply { text = "Setup checklist"; textSize = 26f; setTypeface(null, Typeface.BOLD) })
        box.addView(body("You can skip this and come back any time from the main screen."))

        box.addView(title("1. Allow restricted settings"))
        box.addView(body("Android 13+ blocks accessibility for sideloaded apps. Open App info, tap the three dots (top right) and choose Allow restricted settings. The option only appears after you have tried to enable the service once (step 2)."))
        s1 = body("Cannot be detected automatically.")
        box.addView(s1)
        box.addView(btn("Open App info") { startActivity(appInfo()) })

        box.addView(title("2. Turn on the accessibility service"))
        box.addView(body("Accessibility > Installed apps > BezelPop > On. It only listens for key presses and reads no screen content."))
        s2 = body("")
        box.addView(s2)
        box.addView(btn("Open Accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        box.addView(title("3. Battery: Unrestricted"))
        box.addView(body("Samsung can silently kill accessibility services. Set Battery to Unrestricted for BezelPop (App info > Battery), and make sure it is not listed under Settings > Battery > Background usage limits > Sleeping apps."))
        s3 = body("")
        box.addView(s3)
        box.addView(btn("Ask to ignore battery optimisation") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) { startActivity(appInfo()) }
        })
        box.addView(btn("Open App info (Battery)") { startActivity(appInfo()) })

        box.addView(btn("Done / Skip for now") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(28) }
        })
    }

    override fun onResume() {
        super.onResume()
        s2.text = if (Checks.serviceEnabled(this)) "Status: enabled" else "Status: not enabled yet"
        s3.text = if (Checks.batteryUnrestricted(this)) "Status: unrestricted" else "Status: not yet unrestricted"
    }
}
