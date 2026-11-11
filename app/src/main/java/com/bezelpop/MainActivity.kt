package com.bezelpop

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog

class MainActivity : ComponentActivity() {
    private lateinit var cfg: Config
    private var profile by mutableStateOf(ProfileId.PORTRAIT)
    private var revision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Store.load(this)
        val prefs = Store.prefs(this)
        if (!prefs.getBoolean("checklist_seen", false)) {
            prefs.edit().putBoolean("checklist_seen", true).apply()
            startActivity(Intent(this, ChecklistActivity::class.java))
        }
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                BezelPopScreen(
                    cfg = cfg, profile = profile, revision = revision,
                    onProfile = { profile = it }, onChanged = { save() },
                    onChecklist = { startActivity(Intent(this, ChecklistActivity::class.java)) },
                    onAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    private fun save() { Store.save(this, cfg); revision++ }
}

@Composable
private fun BezelPopScreen(
    cfg: Config, profile: ProfileId, revision: Int,
    onProfile: (ProfileId) -> Unit, onChanged: () -> Unit,
    onChecklist: () -> Unit, onAccessibility: () -> Unit, onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val shape = cfg.profiles.getValue(profile).shape
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var expandedSection by remember { mutableStateOf("Shape") }
    val gradient = Brush.linearGradient(
        listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primary)
    )

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
            val dot = scheme.onSurface.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) .11f else .075f)
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(dot, radius = 0.8.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text("BEZELPOP", fontSize = 12.sp, letterSpacing = 3.sp,
                        color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text("BezelPop", style = TextStyle(
                        brush = gradient, fontSize = 42.sp, lineHeight = 46.sp,
                        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black
                    ))
                    Text("A little extra character for your edges.", color = scheme.onSurfaceVariant)
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (Checks.serviceEnabled(context)) "Overlay service is ready" else "One last setup step", style = MaterialTheme.typography.titleMedium)
                                Text("Currently editing ${profile.label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                            Surface(shape = CircleShape, color = if (Checks.serviceEnabled(context)) scheme.primaryContainer else scheme.errorContainer) {
                                Text(if (Checks.serviceEnabled(context)) "ON" else "OFF", Modifier.padding(horizontal = 13.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onAccessibility, modifier = Modifier.weight(1f)) { Text("Accessibility") }
                            OutlinedButton(onClick = onChecklist, modifier = Modifier.weight(1f)) { Text("Setup guide") }
                        }
                    }
                }
            }
            item {
                Text("ORIENTATION", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, letterSpacing = 1.5.sp)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProfileId.values().forEachIndexed { i, p ->
                        SegmentedButton(selected = p == profile, onClick = { onProfile(p) }, shape = SegmentedButtonDefaults.itemShape(i, ProfileId.values().size)) {
                            Text(when (p) { ProfileId.PORTRAIT -> "Portrait"; ProfileId.LANDSCAPE1 -> "Landscape ↻"; else -> "Landscape ↺" }, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live shape", style = MaterialTheme.typography.titleLarge)
                        Text("The real overlay is shown when you press Test on a button below.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MetricPill("${shape.depth.toInt()} dp", "Depth")
                            MetricPill("${shape.length.toInt()} dp", "Length")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { shape.depth = 9f; shape.length = 90f; onChanged() }) { Text("Reset size") }
                        }
                    }
                }
            }
            item { SectionHeader("Shape geometry", expandedSection == "Shape") { expandedSection = if (expandedSection == "Shape") "" else "Shape" } }
            if (expandedSection == "Shape") {
                item {
                    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ValueSlider("Bulge depth", shape.depth, 1f..40f, "%.1f dp") { shape.depth = it; onChanged() }
                            ValueSlider("Total length", shape.length, 20f..400f, "%.0f dp") { shape.length = it; onChanged() }
                            ValueSlider("Corner smoothing", shape.smoothing, 0f..1f, "%.2f") { shape.smoothing = it; onChanged() }
                            SettingSwitch("Smooth transitions", shape.smooth) { shape.smooth = it; onChanged() }
                            SettingSwitch("Mirror both ends", shape.mirror) { shape.mirror = it; onChanged() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Overlay fill", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Surface(shape = CircleShape, color = Color(shape.color), modifier = Modifier.size(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline)) {}
                                TextButton(onClick = { shape.color = scheme.primary.toArgb(); onChanged() }) { Text("Use system accent") }
                            }
                            HorizontalDivider()
                            Text("First end · bezel transition", style = MaterialTheme.typography.titleSmall)
                            CornerControls("Concave", shape.tConcave, onChanged)
                            CornerControls("Convex", shape.tConvex, onChanged)
                            AnimatedVisibility(!shape.mirror) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    HorizontalDivider()
                                    Text("Second end", style = MaterialTheme.typography.titleSmall)
                                    CornerControls("Concave", shape.bConcave, onChanged)
                                    CornerControls("Convex", shape.bConvex, onChanged)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Hardware buttons", expandedSection == "Buttons") { expandedSection = if (expandedSection == "Buttons") "" else "Buttons" } }
            if (expandedSection == "Buttons") {
                items(cfg.triggers.toList(), key = { it.id }) { trigger ->
                    ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            SettingSwitch(trigger.name, trigger.enabled) { trigger.enabled = it; onChanged() }
                            Text(if (trigger.keyCode == 0) "No key learned yet" else "${KeyEvent.keyCodeToString(trigger.keyCode)} · ${trigger.keyCode}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    Store.prefs(context).edit().putString("learn_id", trigger.id).apply()
                                    onToast("Press the physical button now")
                                }) { Text("Learn key") }
                                FilledTonalButton(onClick = {
                                    val service = KeyService.instance
                                    if (service == null) onToast("Enable the accessibility service first") else service.demo(trigger.id, profile)
                                }) { Text("Test overlay") }
                                if (!trigger.builtin) TextButton(onClick = { cfg.triggers.remove(trigger); onChanged() }) { Text("Remove") }
                            }
                            val placement = cfg.placement(profile, trigger.id)
                            Text("Placement", style = MaterialTheme.typography.labelLarge)
                            var edgeExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { edgeExpanded = true }) { Text("${placement.edge.label} edge · ${placement.pos.toInt()}%") }
                                DropdownMenu(edgeExpanded, { edgeExpanded = false }) {
                                    Edge.values().forEach { edge -> DropdownMenuItem(text = { Text(edge.label) }, onClick = { placement.edge = edge; edgeExpanded = false; onChanged() }) }
                                }
                            }
                            ValueSlider("Position along edge", placement.pos, 0f..100f, "%.0f%%") { placement.pos = it; onChanged() }
                            if (trigger.power) {
                                var modeExpanded by remember { mutableStateOf(false) }
                                Text("Power key is restricted by Android on many devices.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                OutlinedButton(onClick = { modeExpanded = true }) { Text("Power mode: ${cfg.powerMode.label}") }
                                DropdownMenu(modeExpanded, { modeExpanded = false }) {
                                    PowerKeyMode.values().forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { cfg.powerMode = mode; modeExpanded = false; onChanged() }) }
                                }
                                SettingSwitch("Pulse when screen turns on/off", cfg.pulseOnScreen) { cfg.pulseOnScreen = it; onChanged() }
                                SettingSwitch("Watch power menu (experimental)", cfg.menuWatch) { cfg.menuWatch = it; onChanged() }
                            }
                        }
                    }
                }
                item { FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Add custom button") } }
            }
            item { SectionHeader("Motion & timing", expandedSection == "Motion") { expandedSection = if (expandedSection == "Motion") "" else "Motion" } }
            if (expandedSection == "Motion") {
                item {
                    ElevatedCard(shape = RoundedCornerShape(26.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ValueSlider("Expand duration", cfg.anim.expandMs, 30f..1500f, "%.0f ms") { cfg.anim.expandMs = it; onChanged() }
                            ValueSlider("Contract duration", cfg.anim.contractMs, 30f..1500f, "%.0f ms") { cfg.anim.contractMs = it; onChanged() }
                            ValueSlider("Minimum full-size hold after release", cfg.anim.pulseHoldMs, 0f..2000f, "%.0f ms") { cfg.anim.pulseHoldMs = it; onChanged() }
                            ValueSlider("Missed-release safety timeout", cfg.anim.maxHoldSec, 2f..60f, "%.0f sec") { cfg.anim.maxHoldSec = it; onChanged() }
                            Text("Quick taps still complete the expansion before the hold and contraction phases.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            EaseControls("Expand easing", cfg.anim.expandEase, onChanged)
                            EaseControls("Contract easing", cfg.anim.contractEase, onChanged)
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { onChecklist() }, modifier = Modifier.fillMaxWidth()) { Text("Setup checklist & troubleshooting") }
                Text("BezelPop · Built around your system colours", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
    if (showAdd) {
        AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Add a button") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("Button name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val id = "custom_${System.currentTimeMillis()}"
                cfg.triggers.add(Trigger(id, newName.ifBlank { "Custom button" }, 0))
                Store.prefs(context).edit().putString("learn_id", id).apply()
                newName = ""; showAdd = false; onChanged(); onToast("Press the physical button to learn it")
            }) { Text("Add & learn") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } })
    }
}

@Composable private fun MetricPill(value: String, label: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun SectionHeader(title: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "−" else "+", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
        }
    }
}

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(format.format(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
        Slider(value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@Composable private fun CornerControls(name: String, corner: Corner, onChanged: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ValueSlider("Radius", corner.radius, 0f..40f, "%.1f dp") { corner.radius = it; onChanged() }
        ValueSlider("Depth stretch", corner.w, .2f..4f, "%.2fx") { corner.w = it; onChanged() }
        ValueSlider("Length stretch", corner.h, .2f..4f, "%.2fx") { corner.h = it; onChanged() }
    }
}

@Composable private fun EaseControls(title: String, ease: Ease, onChanged: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { open = true }) { Text(ease.type.label) }
        DropdownMenu(open, { open = false }) {
            EaseType.values().forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { ease.type = type; open = false; onChanged() }) }
        }
        if (ease.type.hasStrength) ValueSlider("Strength", ease.strength, .5f..5f, "%.2f") { ease.strength = it; onChanged() }
        if (ease.type == EaseType.BEZIER) {
            listOf("x1", "y1", "x2", "y2").forEachIndexed { i, label ->
                val range = if (i % 2 == 0) 0f..1f else -1f..2f
                ValueSlider(label, ease.bz[i], range, "%.2f") { ease.bz[i] = it; onChanged() }
            }
        }
    }
}
