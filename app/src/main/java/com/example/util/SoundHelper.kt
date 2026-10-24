package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object SoundHelper {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    /**
     * Plays a pleasant celebratory chime when an installment is marked paid
     */
    fun playPaymentSuccess(context: Context, soundEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 180)
            } catch (_: Exception) {}
        }
        vibrate(context, 60)
    }

    /**
     * Soft click sound for tab changes and buttons
     */
    fun playClick(context: Context, soundEnabled: Boolean = true) {
        if (soundEnabled) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 50)
            } catch (_: Exception) {}
        }
        vibrate(context, 25)
    }

    fun vibrate(context: Context, millis: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(millis)
                }
            }
        } catch (_: Exception) {}
    }
}
