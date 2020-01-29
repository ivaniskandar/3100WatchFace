package xyz.ivaniskandar.wear.watchface_3100

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.WatchFaceStyle
import android.support.wearable.watchface.decomposition.FontComponent
import android.support.wearable.watchface.decomposition.ImageComponent
import android.support.wearable.watchface.decomposition.NumberComponent
import android.support.wearable.watchface.decomposition.WatchFaceDecomposition
import android.support.wearable.watchface.decompositionface.DecompositionWatchFaceService
import android.view.SurfaceHolder

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone

class MyWatchFaceDigital : DecompositionWatchFaceService() {

    companion object {

        /**
         * Updates rate in milliseconds for interactive mode. We update once a second since seconds
         * are displayed in interactive mode.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    override fun buildDecomposition(): WatchFaceDecomposition = WatchFaceDecomposition.Builder().apply {
        val ambientOffset = application.resources.getDimensionPixelOffset(R.dimen.decomposed_ambient_offset)
        val displaySize = application.resources.displayMetrics.widthPixels - ambientOffset

        val bgBitmap = Bitmap.createBitmap(displaySize, displaySize, Bitmap.Config.ARGB_8888)
        Canvas(bgBitmap).apply {
            drawColor(Color.BLACK)
        }
        val bgComponent = ImageComponent.Builder()
                .setComponentId(0)
                .setZOrder(0)
                .setImage(Icon.createWithBitmap(bgBitmap))
                .build()
        addImageComponents(bgComponent)

        val digitFontIcon = Icon.createWithResource(applicationContext, R.drawable.digits_decomposable)
        val fontComponent = FontComponent.Builder()
                .setComponentId(1)
                .setDigitCount(10)
                .setImage(digitFontIcon)
                .build()
        addFontComponents(fontComponent)

        var fontWidth : Int
        var minuteStartX: Float
        var minuteEndX: Float
        var clockStartY: Float
        var clockEndY: Float
        with(digitFontIcon.loadDrawable(applicationContext)) {
            fontWidth = this.minimumWidth
            val fontHeight = this.minimumHeight / 10

            // Minute
            minuteStartX = (displaySize / 2f) - (fontWidth)
            minuteEndX = (displaySize / 2f) + (fontWidth)
            clockStartY = (displaySize / 2f) - (fontHeight / 2)
            clockEndY = (displaySize / 2f) + (fontHeight / 2)
            addNumberComponents(
                    NumberComponent.Builder(NumberComponent.Builder.MINUTES)
                            .setComponentId(2)
                            .setFontComponent(fontComponent)
                            .setZOrder(1)
                            .setPosition(PointF(minuteStartX / displaySize, clockStartY / displaySize))
                            .build()
            )
        }

        val colonIcon = Icon.createWithResource(applicationContext, R.drawable.separator_decomposable)
        val colonWidth = colonIcon.loadDrawable(applicationContext).minimumWidth

        // Left colon
        val leftColonStartX = minuteStartX - colonWidth
        val leftColonBounds = RectF(
                leftColonStartX / displaySize,
                clockStartY / displaySize,
                minuteStartX / displaySize,
                clockEndY / displaySize
        )
        addImageComponents(
                ImageComponent.Builder()
                        .setComponentId(3)
                        .setZOrder(1)
                        .setImage(colonIcon)
                        .setBounds(leftColonBounds)
                        .build()
        )

        // Right colon
        val rightColonEndX = minuteEndX + colonWidth
        val rightColonBounds = RectF(
                minuteEndX / displaySize,
                clockStartY / displaySize,
                rightColonEndX / displaySize,
                clockEndY / displaySize
        )
        addImageComponents(
                ImageComponent.Builder()
                        .setComponentId(4)
                        .setZOrder(1)
                        .setImage(colonIcon)
                        .setBounds(rightColonBounds)
                        .build()
        )

        // Second
        addNumberComponents(
                NumberComponent.Builder(NumberComponent.Builder.SECONDS)
                        .setComponentId(5)
                        .setFontComponent(fontComponent)
                        .setZOrder(1)
                        .setPosition(PointF(rightColonEndX / displaySize, clockStartY / displaySize))
                        .build()
        )

        // Hour
        val hourStartX = leftColonStartX - (fontWidth * 2)
        addNumberComponents(
                NumberComponent.Builder(NumberComponent.Builder.HOURS_24)
                        .setComponentId(6)
                        .setFontComponent(fontComponent)
                        .setZOrder(1)
                        .setPosition(PointF(hourStartX / displaySize, clockStartY / displaySize))
                        .build()
        )
    }.build()

    private class EngineHandler(reference: Engine) : Handler() {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : DecompositionWatchFaceService.Engine() {

        private lateinit var calendar: Calendar

        private var registeredTimeZoneReceiver = false

        private val textPaint: Paint by lazy {
            Paint().apply {
                isAntiAlias = true
                color = getColor(R.color.digital_text)
                typeface = resources.getFont(R.font.medium)
                textSize = resources.getDimension(R.dimen.digital_text_size)
                textAlign = Paint.Align.CENTER
            }
        }

        private val textYOffset: Float by lazy {
            val text = "00:00:00"
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            textBounds.exactCenterY()
        }

        private var ambient: Boolean = false

        private val updateTimeHandler: Handler = EngineHandler(this)

        private val timeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFaceDigital).build())
            calendar = Calendar.getInstance()
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            ambient = inAmbientMode

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            // Draw the background.
            canvas.drawColor(Color.BLACK)

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            val now = System.currentTimeMillis()
            calendar.timeInMillis = now
            val yOffset = canvas.centerY - textYOffset

            /* All of the codes in this block wouldn't be needed if we have the monospaced version of the font */
            if (ambient) {
                // Separator
                val separatorText = ":"
                val separatorTextBounds = Rect()
                textPaint.getTextBounds(
                        separatorText,
                        0,
                        separatorText.length,
                        separatorTextBounds
                )
                val separatorTextWidth = separatorTextBounds.width()
                canvas.drawText(":", canvas.centerX, yOffset, textPaint)

                // Hour
                canvas.drawText(
                        "%02d".format(calendar.get(Calendar.HOUR_OF_DAY)),
                        canvas.centerX - (separatorTextWidth / 2),
                        yOffset,
                        Paint(textPaint).apply {
                            textAlign = Paint.Align.RIGHT
                        }
                )

                // Minute
                canvas.drawText(
                        "%02d".format(calendar.get(Calendar.MINUTE)),
                        canvas.centerX + (separatorTextWidth / 2),
                        yOffset,
                        Paint(textPaint).apply {
                            textAlign = Paint.Align.LEFT
                        }
                )
            } else {
                // Minute with separators
                val minuteWithSeparatorsText = ":%02d:".format(calendar.get(Calendar.MINUTE))
                val minuteWithSeparatorsTextBounds = Rect()
                textPaint.getTextBounds(
                        minuteWithSeparatorsText,
                        0,
                        minuteWithSeparatorsText.length,
                        minuteWithSeparatorsTextBounds
                )
                val minuteWithSeparatorsTextWidth = minuteWithSeparatorsTextBounds.width()
                canvas.drawText(
                        minuteWithSeparatorsText,
                        canvas.centerX,
                        yOffset,
                        textPaint
                )

                // Hour
                canvas.drawText(
                        "%02d".format(calendar.get(Calendar.HOUR_OF_DAY)),
                        canvas.centerX - (minuteWithSeparatorsTextWidth / 2),
                        yOffset,
                        Paint(textPaint).apply {
                            textAlign = Paint.Align.RIGHT
                        }
                )
                // Minute
                canvas.drawText(
                        "%02d".format(calendar.get(Calendar.SECOND)),
                        canvas.centerX + (minuteWithSeparatorsTextWidth / 2),
                        yOffset,
                        Paint(textPaint).apply {
                            textAlign = Paint.Align.LEFT
                        }
                )
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFaceDigital.registerReceiver(timeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = false
            this@MyWatchFaceDigital.unregisterReceiver(timeZoneReceiver)
        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
