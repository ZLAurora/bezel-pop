@file:OptIn(ExperimentalMaterial3Api::class)

package com.bezelpop

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

// ======================= state + undo/redo =======================

class UiState(private val app: Context) {
    var cfg by mutableStateOf(Store.load(app))
    var tick by mutableIntStateOf(0)
    var profile by mutableStateOf(currentProfileId(app))
    var editing by mutableStateOf<String?>(null)
    var showChecklist by mutableStateOf(false)
    var resumeTick by mutableIntStateOf(0)
    var learningId by mutableStateOf<String?>(Store.prefs(app).getString("learn_id", null))
    var addDialog by mutableStateOf(false)
    var replayTick by mutableIntStateOf(0)
    var litId by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    private val undoS = ArrayList<String>()
    private val redoS = ArrayList<String>()
    private var burstBase: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val flush = Runnable { commitBurst() }

    /** Every change goes through here. A burst of changes (e.g. one slider drag) is one undo step. */
    fun edit(block: () -> Unit) {
        if (burstBase == null) burstBase = cfg.toJson().toString()
        handler.removeCallbacks(flush)
        block()
        Store.save(app, cfg)
        tick++
        canUndo = true
        handler.postDelayed(flush, 700)
    }

    private fun commitBurst() {
        burstBase?.let {
            undoS.add(it)
            if (undoS.size > 60) undoS.removeAt(0)
            redoS.clear()
        }
        burstBase = null
        syncFlags()
    }

    private fun syncFlags() {
        canUndo = undoS.isNotEmpty() || burstBase != null
        canRedo = redoS.isNotEmpty()
    }

    fun undo() {
        handler.removeCallbacks(flush)
        commitBurst()
        if (undoS.isEmpty()) return
        redoS.add(cfg.toJson().toString())
        applyJson(undoS.removeAt(undoS.size - 1))
    }

    fun redo() {
        handler.removeCallbacks(flush)
        commitBurst()
        if (redoS.isEmpty()) return
        undoS.add(cfg.toJson().toString())
        applyJson(redoS.removeAt(redoS.size - 1))
    }

    private fun applyJson(json: String) {
        Config.parse(json)?.let {
            cfg = it
            Store.save(app, it)
            tick++
        }
        syncFlags()
    }

    fun reloadFromStore() {
        cfg = Store.load(app)
        learningId = Store.prefs(app).getString("learn_id", null)
        tick++
    }
}

// ======================= components =======================

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun <T> Picker(label: String, items: List<T>, selected: T, name: (T) -> String, onSel: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            GBtn(onClick = { open = true }) { Text(name(selected)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(name(item)) }, onClick = { open = false; onSel(item) })
                }
            }
        }
    }
}

@Composable
fun <T> ChipRow(
    items: List<T>, selected: T, name: (T) -> String,
    enabledFor: (T) -> Boolean = { true }, onSel: (T) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected, onClick = { onSel(item) },
                enabled = enabledFor(item), label = { Text(name(item)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@Composable
fun NumberDialog(title: String, value: Float, onDismiss: () -> Unit, onOk: (Float) -> Unit) {
    var text by remember { mutableStateOf(String.format("%.3f", value).trimEnd('0').trimEnd('.', ',')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.replace(',', '.').toFloatOrNull()
                if (v != null) onOk(v) else onDismiss()
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun trimNum(x: Float): String = if (x == x.toInt().toFloat()) x.toInt().toString() else x.toString()

@Composable
private fun StepBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    GBtn(onClick = onClick, modifier = modifier.height(38.dp), pad = 2.dp) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

/** Slider + −/+ fine and coarse buttons + tap-to-type number. Optional "All" button. */
@Composable
fun Stepper(
    label: String, value: Float, min: Float, max: Float, unit: String = "",
    fine: Float = 0.1f, coarse: Float = 1f, decimals: Int = 1,
    onAll: (() -> Unit)? = null, onChange: (Float) -> Unit
) {
    var typing by remember { mutableStateOf(false) }
    fun fmt(v: Float) = String.format("%.${decimals}f", v) + unit
    fun set(v: Float) {
        val c = v.coerceIn(min, max)
        onChange(Math.round(c * 1000f) / 1000f)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                fmt(value), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { typing = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Slider(value = value.coerceIn(min, max), onValueChange = { set(it) }, valueRange = min..max)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StepBtn("−" + trimNum(coarse), Modifier.weight(1f)) { set(value - coarse) }
            StepBtn("−" + trimNum(fine), Modifier.weight(1f)) { set(value - fine) }
            StepBtn("+" + trimNum(fine), Modifier.weight(1f)) { set(value + fine) }
            StepBtn("+" + trimNum(coarse), Modifier.weight(1f)) { set(value + coarse) }
            if (onAll != null) StepBtn("All", Modifier.weight(1f)) { onAll() }
        }
    }
    if (typing) NumberDialog(label, value, { typing = false }) { set(it); typing = false }
}

@Composable
fun EaseEditor(title: String, e: Ease, onEdit: (Ease.() -> Unit) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Picker("Curve", EaseType.values().toList(), e.type, { it.label }) { picked -> onEdit { type = picked } }
    if (e.type.hasStrength) {
        Stepper("Strength", e.strength, 0.5f, 5f, "", 0.05f, 0.5f, 2) { v -> onEdit { strength = v } }
    }
    if (e.type == EaseType.BEZIER) {
        Stepper("x1", e.bz[0], 0f, 1f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[0] = v } }
        Stepper("y1", e.bz[1], -1f, 2f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[1] = v } }
        Stepper("x2", e.bz[2], 0f, 1f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[2] = v } }
        Stepper("y2", e.bz[3], -1f, 2f, "", 0.01f, 0.1f, 2) { v -> onEdit { bz[3] = v } }
    }
}

// ---------- colour wheel ----------

private val hueColors = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }

@Composable
fun ColorWheel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val cb by rememberUpdatedState(onChange)
    fun pick(p: Offset, w: Float) {
        val c = w / 2f
        val dx = p.x - c
        val dy = p.y - c
        val s = (hypot(dx, dy) / c).coerceIn(0f, 1f)
        var h = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (h < 0f) h += 360f
        cb(h, s)
    }
    Canvas(
        Modifier
            .size(240.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pick(it, size.width.toFloat()) },
                    onDrag = { change, _ -> pick(change.position, size.width.toFloat()) }
                )
            }
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Brush.sweepGradient(hueColors, c), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = r), radius = r, center = c)
        drawCircle(Color.Black.copy(alpha = 1f - value), radius = r, center = c)
        val a = Math.toRadians(hue.toDouble())
        val p = Offset(c.x + (cos(a) * sat * r).toFloat(), c.y + (sin(a) * sat * r).toFloat())
        drawCircle(Color.Black.copy(alpha = 0.5f), 13.dp.toPx(), p, style = Stroke(5.dp.toPx()))
        drawCircle(Color.White, 12.dp.toPx(), p, style = Stroke(3.dp.toPx()))
    }
}

@Composable
fun ColorDialog(title: String, initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv0 = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    val result = Color.hsv(hue, sat, value, alpha)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ColorWheel(hue, sat, value) { h, s -> hue = h; sat = s }
                Text("Brightness")
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
                Text("Opacity")
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0.05f..1f)
                Box(
                    Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp)).background(result)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                )
                Text(String.format("#%08X", result.toArgb()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(result.toArgb()) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ColorRow(label: String, color: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        GBtn(onClick = { open = true }) { Text("Wheel…") }
    }
    if (open) ColorDialog(label, color, { open = false }) { open = false; onPick(it) }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GBtn(onClick = onClick, modifier = modifier, primary = true) { Text(text) }
}
