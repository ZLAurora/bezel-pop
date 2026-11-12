@file:OptIn(ExperimentalMaterial3Api::class)

package com.bezelpop

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.tan

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

private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

private fun contrastOf(c: Int): Int {
    val l = (0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) +
        0.114f * android.graphics.Color.blue(c)) / 255f
    return if (l > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

private fun angleOf(w: Float, h: Float): Float = Math.toDegrees(atan2(w.toDouble(), h.toDouble())).toFloat()
private fun runFromAngle(w: Float, deg: Float): Float = (w / tan(Math.toRadians(deg.toDouble()))).toFloat()

// ======================= top bar =======================

@Composable
private fun TopBar(ui: UiState, title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") }
        if (onBack == null) GradientText("BezelPop", 44.sp, Modifier.weight(1f))
        else Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = { ui.undo() }, enabled = ui.canUndo) { Text("Undo") }
        TextButton(onClick = { ui.redo() }, enabled = ui.canRedo) { Text("Redo") }
    }
}

// ======================= home =======================

@Composable
fun HomeScreen(ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    @Suppress("UNUSED_VARIABLE") val rt = ui.resumeTick
    val cfg = ui.cfg
    val ctx = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    val clipboard = LocalClipboardManager.current
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(ui.cfg.toJson().toString(2).toByteArray()) }
                toast(ctx, "Exported")
            } catch (e: Exception) { toast(ctx, "Export failed") }
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val parsed = Config.parse(text)
                if (parsed == null) toast(ctx, "That file isn't a BezelPop export")
                else { ui.edit { ui.cfg = parsed }; toast(ctx, "Imported (undo available)") }
            } catch (e: Exception) { toast(ctx, "Import failed") }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(
                start = bars.calculateStartPadding(ld) + 16.dp, end = bars.calculateEndPadding(ld) + 16.dp,
                top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 40.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopBar(ui, "BezelPop", null)

        // ---- status ----
        Section("Status") {
            val on = Checks.serviceEnabled(ctx)
            Text(if (on) "Service is ON" else "Service is OFF — turn it on in Accessibility settings")
            Text("Phone orientation right now: ${ui.profile.label}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                FilledTonalButton(onClick = { ui.showChecklist = true }) { Text("Setup checklist") }
            }
        }

        // ---- profiles ----
        Section("Profiles") {
            ChipRow(ProfileId.values().toList(), ui.profile, { it.label }, enabledFor = { it == ui.profile }) { }
            Hint("You can only edit the profile that matches how your phone is held right now. Rotate the phone to edit another one.")
            FilledTonalButton(onClick = {
                confirm = "Copy this profile's shape (corners, diagonals, colour) to all other profiles? Positions are not touched." to {
                    ui.edit {
                        val src = cfg.profiles.getValue(ui.profile).shape
                        ProfileId.values().filter { it != ui.profile }.forEach { cfg.profiles.getValue(it).shape = src.copy() }
                    }
                }
            }) { Text("Copy ${ui.profile.label} shape to all others") }
        }

        // ---- buttons ----
        for (t in cfg.triggers.toList()) {
            Section(t.name) {
                SwitchRow("Enabled", t.enabled) { v -> ui.edit { t.enabled = v } }
                val kc = if (t.keyCode == 0) "not set yet" else "${KeyEvent.keyCodeToString(t.keyCode)} (${t.keyCode})"
                Text("Key: $kc", style = MaterialTheme.typography.bodySmall)
                val learning = ui.learningId == t.id
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Edit") { ui.editing = t.id }
                    FilledTonalButton(onClick = {
                        if (learning) {
                            Store.prefs(ctx).edit().remove("learn_id").apply(); ui.learningId = null
                        } else {
                            if (KeyService.instance == null) toast(ctx, "Turn on the accessibility service first")
                            Store.prefs(ctx).edit().putString("learn_id", t.id).apply(); ui.learningId = t.id
                            toast(ctx, "Press the physical button now")
                        }
                    }) { Text(if (learning) "Cancel" else "Learn key") }
                    FilledTonalButton(onClick = {
                        val s = KeyService.instance
                        if (s == null) toast(ctx, "Turn on the accessibility service first") else s.demo(t.id, ui.profile)
                    }) { Text("Test") }
                    if (!t.builtin) FilledTonalButton(onClick = { ui.edit { cfg.triggers.remove(t) } }) { Text("Remove") }
                }
                if (t.power) {
                    Hint("Android rarely passes the power key to accessibility services, so hold detection may be impossible. Fallbacks:")
                    Picker("Power key mode", PowerKeyMode.values().toList(), cfg.powerMode, { it.label }) { m -> ui.edit { cfg.powerMode = m } }
                    SwitchRow("Also pulse when screen turns on/off", cfg.pulseOnScreen) { v -> ui.edit { cfg.pulseOnScreen = v } }
                    SwitchRow("Experimental: show while power menu is open", cfg.menuWatch) { v -> ui.edit { cfg.menuWatch = v } }
                }
            }
        }
        Section("Add a button") {
            Hint("Custom buttons (Bixby, Essential key, gaming triggers…) only work if the phone sends a normal key event.")
            PrimaryButton("Add custom button…") { addDialog = true }
        }

        // ---- animation ----
        val a = cfg.anim
        Section("Animation") {
            Stepper("Expand time", a.expandMs, 30f, 1500f, " ms", 1f, 10f, 0, onAll = { ui.edit { a.contractMs = a.expandMs } }) { v -> ui.edit { a.expandMs = v } }
            Stepper("Contract time", a.contractMs, 30f, 1500f, " ms", 1f, 10f, 0) { v -> ui.edit { a.contractMs = v } }
            Stepper("Latency compensation", a.latencyMs, 0f, 80f, " ms", 1f, 5f, 0) { v -> ui.edit { a.latencyMs = v } }
            Hint("Starts the expand animation this far in, to hide the small delay Android adds before apps see a key press.")
            SwitchRow("Low-latency mode (keep invisible windows ready)", a.lowLatency) { v -> ui.edit { a.lowLatency = v } }
            Stepper("Pulse hold time", a.pulseHoldMs, 0f, 2000f, " ms", 1f, 10f, 0) { v -> ui.edit { a.pulseHoldMs = v } }
            Stepper("Safety timeout", a.maxHoldSec, 2f, 60f, " s", 1f, 5f, 0) { v -> ui.edit { a.maxHoldSec = v } }
            EaseEditor("Expand easing", a.expandEase) { f -> ui.edit { a.expandEase.f() } }
            FilledTonalButton(onClick = { ui.edit { a.contractEase = a.expandEase.copy() } }) { Text("Use expand easing for contract") }
            EaseEditor("Contract easing", a.contractEase) { f -> ui.edit { a.contractEase.f() } }
        }

        Section("Haptics") {
            Picker("On expand", HapticLevel.values().toList(), a.hapticExpand, { it.label }) { h -> ui.edit { a.hapticExpand = h } }
            Picker("On contract", HapticLevel.values().toList(), a.hapticContract, { it.label }) { h -> ui.edit { a.hapticContract = h } }
            FilledTonalButton(onClick = { ui.edit { a.hapticContract = a.hapticExpand } }) { Text("Use expand haptic for contract") }
            Hint("Per-button haptics can be set in each button's overrides. System vibration settings can mute these.")
        }

        Section("Editing appearance") {
            SwitchRow("Invert bulge colour while editing", cfg.ui.editInvert) { v -> ui.edit { cfg.ui.editInvert = v } }
            if (!cfg.ui.editInvert) ColorRow("Editing colour", cfg.ui.editColor) { c -> ui.edit { cfg.ui.editColor = c } }
            Hint("This only changes the live overlay while you are in an editor. Your real bulge colour is untouched.")
        }

        Section("Backup & reset") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { exportL.launch("bezelpop-settings.json") }) { Text("Export file") }
                FilledTonalButton(onClick = { importL.launch(arrayOf("*/*")) }) { Text("Import file") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    clipboard.setText(AnnotatedString(cfg.toJson().toString(2))); toast(ctx, "Copied settings text")
                }) { Text("Copy as text") }
                FilledTonalButton(onClick = {
                    val parsed = clipboard.getText()?.text?.let { Config.parse(it) }
                    if (parsed == null) toast(ctx, "Clipboard has no BezelPop settings")
                    else { ui.edit { ui.cfg = parsed }; toast(ctx, "Pasted (undo available)") }
                }) { Text("Paste text") }
            }
            Hint("Resets are undoable.")
            OutlinedButton(onClick = {
                confirm = "Reset ${ui.profile.label} shape to default?" to { ui.edit { cfg.profiles.getValue(ui.profile).shape = Shape() } }
            }) { Text("Reset this profile's shape") }
            OutlinedButton(onClick = {
                confirm = "Reset button positions for ${ui.profile.label}?" to { ui.edit { cfg.profiles.getValue(ui.profile).placements.clear() } }
            }) { Text("Reset positions (this profile)") }
            OutlinedButton(onClick = {
                confirm = "Reset all animation and haptic settings?" to { ui.edit { cfg.anim = Anim() } }
            }) { Text("Reset animation settings") }
            OutlinedButton(onClick = {
                confirm = "Remove all per-button overrides?" to { ui.edit { cfg.triggers.forEach { it.ov = Overrides() } } }
            }) { Text("Reset all button overrides") }
            OutlinedButton(onClick = {
                confirm = "Reset EVERYTHING to defaults? (You can still undo.)" to { ui.edit { ui.cfg = Config.default() } }
            }) { Text("Reset everything") }
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
    if (addDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("New button") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    addDialog = false
                    val t = Trigger("custom_" + System.currentTimeMillis(), name.trim().ifEmpty { "Custom button" }, 0)
                    ui.edit { cfg.triggers.add(t) }
                    Store.prefs(ctx).edit().putString("learn_id", t.id).apply()
                    ui.learningId = t.id
                    toast(ctx, "Press the physical button now (service must be on)")
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } }
        )
    }
}

// ======================= editor =======================

@Composable
fun EditorScreen(ui: UiState, id: String) {
    @Suppress("UNUSED_VARIABLE") val tick = ui.tick
    val cfg = ui.cfg
    val t = cfg.trigger(id)
    if (t == null) {
        LaunchedEffect(Unit) { ui.editing = null }
        return
    }
    val ctx = LocalContext.current
    val prof = cfg.profiles.getValue(ui.profile)
    val s = prof.shape
    val pl = cfg.placement(ui.profile, id)
    val effColor = t.ov.color ?: s.color
    val previewColor: Int = if (cfg.ui.editInvert) contrastOf(effColor) else cfg.ui.editColor

    LaunchedEffect(id, ui.profile, previewColor, ui.resumeTick) {
        KeyService.instance?.pin(id, ui.profile, previewColor)
    }
    DisposableEffect(Unit) { onDispose { KeyService.instance?.unpin() } }

    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    val depthDp = maxOf(s.wTop, s.wBot) * t.ov.scale
    val margin = (depthDp + 28f).dp
    val strip = (depthDp + 22f).dp
    val extraStart = if (pl.edge == Edge.LEFT) margin else 0.dp
    val extraEnd = if (pl.edge == Edge.RIGHT) margin else 0.dp
    val extraTop = if (pl.edge == Edge.TOP) strip else 0.dp
    val extraBottom = if (pl.edge == Edge.BOTTOM) strip else 0.dp
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(
                    start = bars.calculateStartPadding(ld) + 16.dp + extraStart,
                    end = bars.calculateEndPadding(ld) + 16.dp + extraEnd,
                    top = bars.calculateTopPadding() + 8.dp + extraTop,
                    bottom = bars.calculateBottomPadding() + 40.dp + extraBottom
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TopBar(ui, t.name, { ui.editing = null })

            if (!Checks.serviceEnabled(ctx)) {
                Section("Live overlay is off") {
                    Hint("Turn on the accessibility service to see the real bulge while you edit.")
                    FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility settings") }
                }
            }

            Section("Position · ${ui.profile.label}") {
                Text("Edge", style = MaterialTheme.typography.bodyMedium)
                ChipRow(Edge.values().toList(), pl.edge, { it.label }) { e -> ui.edit { pl.edge = e } }
                Stepper("Position along edge", pl.pos, 0f, 100f, "%", 0.1f, 1f, 1) { v -> ui.edit { pl.pos = v } }
                Hint("Landscape positions are set separately in each landscape orientation.")
            }

            Section("Shape · ${ui.profile.label}") {
                Stepper("Length (bezel contact)", s.length, 20f, 400f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.length = v } }

                SwitchRow("Link both diagonal widths (depth)", s.linkWidths) { v -> ui.edit { s.linkWidths = v; if (v) s.wBot = s.wTop } }
                Stepper(if (s.linkWidths) "Depth (diagonal width)" else "Top diagonal width", s.wTop, 0.5f, 40f, " dp", 0.1f, 1f, 1) { v ->
                    ui.edit { s.wTop = v; if (s.linkWidths) s.wBot = v }
                }
                if (!s.linkWidths) {
                    Stepper("Bottom diagonal width", s.wBot, 0.5f, 40f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.wBot = v } }
                }

                SwitchRow("Set diagonals by angle (off = by length along edge)", cfg.ui.angleMode) { v -> ui.edit { cfg.ui.angleMode = v } }
                SwitchRow("Link both diagonal heights", s.linkHeights) { v -> ui.edit { s.linkHeights = v; if (v) s.hBot = s.hTop } }
                if (cfg.ui.angleMode) {
                    Stepper("Top diagonal angle", angleOf(s.wTop, s.hTop), 0.5f, 85f, "°", 0.1f, 1f, 1) { d ->
                        ui.edit { s.hTop = runFromAngle(s.wTop, d); if (s.linkHeights) s.hBot = runFromAngle(s.wBot, d) }
                    }
                    if (!s.linkHeights) {
                        Stepper("Bottom diagonal angle", angleOf(s.wBot, s.hBot), 0.5f, 85f, "°", 0.1f, 1f, 1) { d ->
                            ui.edit { s.hBot = runFromAngle(s.wBot, d) }
                        }
                    }
                } else {
                    Stepper(if (s.linkHeights) "Diagonal height (along edge)" else "Top diagonal height", s.hTop, 0.5f, 250f, " dp", 0.1f, 1f, 1) { v ->
                        ui.edit { s.hTop = v; if (s.linkHeights) s.hBot = v }
                    }
                    if (!s.linkHeights) {
                        Stepper("Bottom diagonal height", s.hBot, 0.5f, 250f, " dp", 0.1f, 1f, 1) { v -> ui.edit { s.hBot = v } }
                    }
                }

                SwitchRow("Smooth (Apple-style) corners", s.smooth) { v -> ui.edit { s.smooth = v } }
                if (s.smooth) Stepper("Smoothing (control point)", s.smoothing, 0f, 1f, "", 0.01f, 0.1f, 2) { v -> ui.edit { s.smoothing = v } }
                else Hint("Plain corners: straight, circular arc, straight.")

                SwitchRow("Mirror ends (D copies A, C copies B)", s.mirrorCorners) { v -> ui.edit { s.mirrorCorners = v } }
                CornerEditor("Corner A · bezel → diagonal", s.cA, ui, s)
                CornerEditor("Corner B · diagonal → flat edge", s.cB, ui, s)
                if (!s.mirrorCorners) {
                    CornerEditor("Corner C · flat edge → diagonal", s.cC, ui, s)
                    CornerEditor("Corner D · diagonal → bezel", s.cD, ui, s)
                }

                ColorRow("Shape colour", s.color) { c -> ui.edit { s.color = c } }

                CopyShapeMenu(ui)
            }

            Section("Overrides · ${t.name}") {
                Hint("Anything left on inherit follows the global settings.")
                SwitchRow("Own colour", t.ov.color != null) { on -> ui.edit { t.ov.color = if (on) s.color else null } }
                t.ov.color?.let { c -> ColorRow("Colour", c) { v -> ui.edit { t.ov.color = v } } }
                Stepper("Size scale", t.ov.scale, 0.3f, 3f, "×", 0.01f, 0.1f, 2) { v -> ui.edit { t.ov.scale = v } }

                SwitchRow("Own expand time", t.ov.expandMs != null) { on -> ui.edit { t.ov.expandMs = if (on) cfg.anim.expandMs else null } }
                t.ov.expandMs?.let { v -> Stepper("Expand time", v, 30f, 1500f, " ms", 1f, 10f, 0) { x -> ui.edit { t.ov.expandMs = x } } }
                SwitchRow("Own contract time", t.ov.contractMs != null) { on -> ui.edit { t.ov.contractMs = if (on) cfg.anim.contractMs else null } }
                t.ov.contractMs?.let { v -> Stepper("Contract time", v, 30f, 1500f, " ms", 1f, 10f, 0) { x -> ui.edit { t.ov.contractMs = x } } }

                SwitchRow("Own expand easing", t.ov.expandEase != null) { on -> ui.edit { t.ov.expandEase = if (on) cfg.anim.expandEase.copy() else null } }
                t.ov.expandEase?.let { e -> EaseEditor("Expand easing", e) { f -> ui.edit { e.f() } } }
                SwitchRow("Own contract easing", t.ov.contractEase != null) { on -> ui.edit { t.ov.contractEase = if (on) cfg.anim.contractEase.copy() else null } }
                t.ov.contractEase?.let { e -> EaseEditor("Contract easing", e) { f -> ui.edit { e.f() } } }

                val hapticItems = listOf<HapticLevel?>(null) + HapticLevel.values().toList()
                Picker("Expand haptic", hapticItems, t.ov.hapticExpand, { it?.label ?: "Inherit" }) { h -> ui.edit { t.ov.hapticExpand = h } }
                Picker("Contract haptic", hapticItems, t.ov.hapticContract, { it?.label ?: "Inherit" }) { h -> ui.edit { t.ov.hapticContract = h } }

                OutlinedButton(onClick = { ui.edit { t.ov = Overrides() } }) { Text("Reset this button's overrides") }
            }

            PrimaryButton("Replay animation") { KeyService.instance?.replay(id, ui.profile, previewColor) }
        }

        // landscape: a slim strip the content scrolls under, so it never collides with the bulge
        if (pl.edge == Edge.TOP) {
            Box(
                Modifier.fillMaxWidth().height(strip + bars.calculateTopPadding()).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.85f), Color.Transparent)))
            )
        }
        if (pl.edge == Edge.BOTTOM) {
            Box(
                Modifier.fillMaxWidth().height(strip + bars.calculateBottomPadding()).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.85f), bg)))
            )
        }
    }
}

@Composable
private fun CornerEditor(title: String, c: Corner, ui: UiState, s: Shape) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val all = listOf(s.cA, s.cB, s.cC, s.cD)
    Stepper("Radius", c.radius, 0f, 800f, " dp", 1f, 10f, 0, onAll = { ui.edit { all.forEach { it.radius = c.radius } } }) { v -> ui.edit { c.radius = v } }
    Stepper("Stretch width (depth direction)", c.w, 0.1f, 4f, "×", 0.01f, 0.1f, 2, onAll = { ui.edit { all.forEach { it.w = c.w } } }) { v -> ui.edit { c.w = v } }
    Stepper("Stretch height (along edge)", c.h, 0.1f, 4f, "×", 0.01f, 0.1f, 2, onAll = { ui.edit { all.forEach { it.h = c.h } } }) { v -> ui.edit { c.h = v } }
}

@Composable
private fun CopyShapeMenu(ui: UiState) {
    var open by remember { mutableStateOf(false) }
    val others = ProfileId.values().filter { it != ui.profile }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilledTonalButton(onClick = { open = true }) { Text("Copy shape from…") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                others.forEach { p ->
                    DropdownMenuItem(text = { Text(p.label) }, onClick = {
                        open = false
                        ui.edit { ui.cfg.profiles.getValue(ui.profile).shape = ui.cfg.profiles.getValue(p).shape.copy() }
                    })
                }
            }
        }
        FilledTonalButton(onClick = {
            ui.edit {
                val src = ui.cfg.profiles.getValue(ui.profile).shape
                others.forEach { ui.cfg.profiles.getValue(it).shape = src.copy() }
            }
        }) { Text("Copy to all others") }
    }
    Hint("Copies corners, diagonals and colour. Positions are never copied.")
}

// ======================= checklist =======================

@Composable
fun ChecklistScreen(ui: UiState) {
    @Suppress("UNUSED_VARIABLE") val rt = ui.resumeTick
    val ctx = LocalContext.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val ld = LayoutDirection.Ltr
    fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(
                start = bars.calculateStartPadding(ld) + 16.dp, end = bars.calculateEndPadding(ld) + 16.dp,
                top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 40.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GradientText("Setup", 44.sp)
        Hint("You can skip this and come back any time from the main screen.")

        Section("1 · Allow restricted settings") {
            Text("Android 13+ blocks accessibility for sideloaded apps. Open App info, tap ⋮ (top right) and choose Allow restricted settings. The option appears after you have tried to enable the service once (step 2).")
            Hint("Can't be detected automatically.")
            FilledTonalButton(onClick = { ctx.startActivity(appInfo()) }) { Text("Open App info") }
        }
        Section("2 · Turn on the accessibility service") {
            Text("Accessibility > Installed apps > BezelPop > On. It only listens for key presses and reads no screen content.")
            Text(if (Checks.serviceEnabled(ctx)) "Status: enabled ✓" else "Status: not enabled yet")
            FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open Accessibility settings") }
        }
        Section("3 · Battery: Unrestricted") {
            Text("Samsung can silently kill accessibility services. Set Battery to Unrestricted (App info > Battery) and make sure BezelPop isn't under Settings > Battery > Background usage limits > Sleeping apps.")
            Text(if (Checks.batteryUnrestricted(ctx)) "Status: unrestricted ✓" else "Status: not yet unrestricted")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    } catch (e: Exception) { ctx.startActivity(appInfo()) }
                }) { Text("Ask to ignore") }
                FilledTonalButton(onClick = { ctx.startActivity(appInfo()) }) { Text("App info") }
            }
        }
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Done / Skip for now", Modifier.fillMaxWidth()) { ui.showChecklist = false }
    }
}
