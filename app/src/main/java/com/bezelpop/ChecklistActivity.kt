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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

class ChecklistActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        androidx.activity.compose.setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val scheme = when {
                android.os.Build.VERSION.SDK_INT >= 31 && dark -> androidx.compose.material3.dynamicDarkColorScheme(this)
                android.os.Build.VERSION.SDK_INT >= 31 -> androidx.compose.material3.dynamicLightColorScheme(this)
                dark -> androidx.compose.material3.darkColorScheme()
                else -> androidx.compose.material3.lightColorScheme()
            }
            androidx.compose.material3.MaterialTheme(colorScheme = scheme) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                        .background(scheme.background)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
                ) {
                    androidx.compose.material3.Text("FIRST RUN", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = scheme.primary)
                    androidx.compose.material3.Text("Let's get set up", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("You can skip this and return from BezelPop whenever you need.", color = scheme.onSurfaceVariant)
                    SetupStep("1", "Allow restricted settings", "On Android 13+, open App info, tap the three-dot menu and choose Allow restricted settings. This option may appear only after you try enabling the service.", "Open App info") {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                    SetupStep("2", "Enable the accessibility service", "Accessibility → Installed apps → BezelPop → On. BezelPop listens for hardware key events; it does not read screen content.", if (Checks.serviceEnabled(this)) "Enabled" else "Open Accessibility settings", if (Checks.serviceEnabled(this)) null else {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    })
                    SetupStep("3", "Set battery to unrestricted", "Samsung may stop background services. Set BezelPop to Unrestricted in App info → Battery, and remove it from Sleeping apps if needed.", if (Checks.batteryUnrestricted(this)) "Unrestricted" else "Open battery settings", if (Checks.batteryUnrestricted(this)) null else {
                        try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
                        catch (_: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                    })
                    androidx.compose.material3.FilledTonalButton(onClick = { finish() }, modifier = androidx.compose.ui.Modifier.fillMaxWidth()) { androidx.compose.material3.Text("Done / skip for now") }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SetupStep(number: String, title: String, description: String, action: String, onClick: (() -> Unit)?) {
    androidx.compose.material3.ElevatedCard(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.CircleShape, color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) {
                    androidx.compose.material3.Text(number, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                androidx.compose.material3.Text(title, Modifier.padding(start = 12.dp), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            androidx.compose.material3.Text(description, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClick != null) androidx.compose.material3.FilledTonalButton(onClick = onClick) { androidx.compose.material3.Text(action) }
            else androidx.compose.material3.Text(action, color = androidx.compose.material3.MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
    }
}
