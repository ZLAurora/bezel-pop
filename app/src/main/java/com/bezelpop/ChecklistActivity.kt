
package com.bezelpop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object Checks {
    fun serviceEnabled(c: Context): Boolean {
        val enabled = Settings.Secure.getString(
            c.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val me = ComponentName(c, KeyService::class.java)

        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == me
        }
    }

    fun batteryUnrestricted(c: Context): Boolean =
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(c.packageName)
}

class ChecklistActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()

            val scheme = when {
                android.os.Build.VERSION.SDK_INT >= 31 && dark ->
                    dynamicDarkColorScheme(this@ChecklistActivity)

                android.os.Build.VERSION.SDK_INT >= 31 ->
                    dynamicLightColorScheme(this@ChecklistActivity)

                dark -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = scheme) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.background)
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),

                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    Text(
                        "FIRST RUN",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary
                    )

                    Text(
                        "Let's get set up",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "You can skip this and return from BezelPop whenever you need.",
                        color = scheme.onSurfaceVariant
                    )

                    SetupStep(
                        number = "1",
                        title = "Allow restricted settings",
                        description = "On Android 13+, open App info, tap the three-dot menu and choose Allow restricted settings. This option may appear only after you try enabling the service.",
                        action = "Open App info",
                        onClick = {
                            this@ChecklistActivity.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    )

                    val serviceReady =
                        Checks.serviceEnabled(this@ChecklistActivity)

                    SetupStep(
                        number = "2",
                        title = "Enable the accessibility service",
                        description = "Accessibility → Installed apps → BezelPop → On. BezelPop listens for hardware key events; it does not read screen content.",
                        action = if (serviceReady)
                            "Enabled"
                        else
                            "Open Accessibility settings",

                        onClick = if (serviceReady) {
                            null
                        } else {
                            {
                                this@ChecklistActivity.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                )
                            }
                        }
                    )

                    val batteryReady =
                        Checks.batteryUnrestricted(this@ChecklistActivity)

                    SetupStep(
                        number = "3",
                        title = "Set battery to unrestricted",
                        description = "Samsung may stop background services. Set BezelPop to Unrestricted in App info → Battery, and remove it from Sleeping apps if needed.",
                        action = if (batteryReady)
                            "Unrestricted"
                        else
                            "Open battery settings",

                        onClick = if (batteryReady) {
                            null
                        } else {
                            {
                                try {
                                    this@ChecklistActivity.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                } catch (_: Exception) {
                                    this@ChecklistActivity.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            }
                        }
                    )

                    FilledTonalButton(
                        onClick = { finish() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done / skip for now")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    description: String,
    action: String,
    onClick: (() -> Unit)?
) {

    ElevatedCard(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {

                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {

                    Text(
                        number,
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    title,
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onClick != null) {

                FilledTonalButton(onClick = onClick) {
                    Text(action)
                }

            } else {

                Text(
                    action,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
