package com.example.model

import android.app.ActivityManager
import android.content.Context

/**
 * How many ONNX Runtime intra-op threads it's safe to hand each STT/TTS inference
 * session on this device. Defaults to 1 — the safe choice for weak/low-RAM
 * hardware, where a second worker thread just adds scratch-buffer memory and
 * scheduling contention for no real parallelism gain — and only goes to 2 when the
 * device both reports itself as NOT low-RAM (Android's own heuristic, which factors
 * in installed RAM and the device's declared minimum-memory class, not just a raw
 * byte count) and has enough cores to actually benefit from a second worker.
 */
fun recommendedOrtThreads(context: Context): Int {
    return try {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRamDevice = activityManager?.isLowRamDevice ?: true
        val cores = Runtime.getRuntime().availableProcessors()
        if (!isLowRamDevice && cores >= 4) 2 else 1
    } catch (e: Exception) {
        1
    }
}
