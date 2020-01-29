package xyz.ivaniskandar.wear.watchface_3100

import android.graphics.Canvas

val Canvas.centerX: Float
    get() {
        return width / 2F
    }

val Canvas.centerY: Float
    get() {
        return height / 2F
    }
