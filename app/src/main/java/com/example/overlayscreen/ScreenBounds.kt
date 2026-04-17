package com.example.overlayscreen

import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

fun WindowManager.currentScreenBounds(): Rect {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        currentWindowMetrics.bounds
    } else {
        @Suppress("DEPRECATION")
        Rect(0, 0, defaultDisplay.width, defaultDisplay.height)
    }
}
