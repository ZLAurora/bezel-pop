package com.bezelpop

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    fun play(ctx: Context, level: HapticLevel) {
        if (level == HapticLevel.OFF) return
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vib == null || !vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 29) {
                val id = when (level) {
                    HapticLevel.TICK -> VibrationEffect.EFFECT_TICK
                    HapticLevel.CLICK -> VibrationEffect.EFFECT_CLICK
                    else -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                vib.vibrate(VibrationEffect.createPredefined(id))
            } else {
                val ms: Long
                val amp: Int
                when (level) {
                    HapticLevel.TICK -> { ms = 8L; amp = 60 }
                    HapticLevel.CLICK -> { ms = 15L; amp = 120 }
                    else -> { ms = 25L; amp = 255 }
                }
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            }
        } catch (e: Exception) {
        }
    }
}
