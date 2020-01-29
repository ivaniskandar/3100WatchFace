package xyz.ivaniskandar.wear.watchface_3100

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.palette.graphics.Palette
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.support.wearable.watchface.decomposition.ImageComponent
import android.support.wearable.watchface.decomposition.WatchFaceDecomposition
import android.support.wearable.watchface.decompositionface.DecompositionWatchFaceService
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : DecompositionWatchFaceService() {
    companion object {
        /**
         * Updates rate in milliseconds for interactive mode. We update once a second to advance the
         * second hand.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0

        private const val HOUR_STROKE_WIDTH = 5f
        private const val MINUTE_STROKE_WIDTH = 3f
        private const val SECOND_TICK_STROKE_WIDTH = 2f

        private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

        private const val SHADOW_RADIUS = 6f
    }

    private lateinit var mCalendar: Calendar

    private val displayMetrics by lazy {
        application.resources.displayMetrics
    }

    private var mRegisteredTimeZoneReceiver = false
    private var mMuteMode: Boolean = false
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    private var mCenterX: Float = 0F
    private var mCenterY: Float = 0F

    private var mSecondHandLength: Float = 0F
    private var sMinuteHandLength: Float = 0F
    private var sHourHandLength: Float = 0F

    /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
    private var mWatchHandColor: Int = 0
    private var mWatchHandHighlightColor: Int = 0
    private var mWatchHandShadowColor: Int = 0

    private lateinit var mHourPaint: Paint
    private lateinit var mMinutePaint: Paint
    private lateinit var mSecondPaint: Paint
    private lateinit var mTickAndCirclePaint: Paint

    private lateinit var mBackgroundPaint: Paint
    private lateinit var mBackgroundBitmap: Bitmap
    private lateinit var mGrayBackgroundBitmap: Bitmap

    private var mAmbient: Boolean = false
    private var mLowBitAmbient: Boolean = false
    private var mBurnInProtection: Boolean = false

    override fun buildDecomposition(): WatchFaceDecomposition {
        val ambientOffset = applicationContext.resources.getDimensionPixelOffset(R.dimen.decomposed_ambient_offset)
        val ambientWidth = displayMetrics.widthPixels - ambientOffset
        val ambientHeight = displayMetrics.heightPixels - ambientOffset
        val ambientCenterX = ambientWidth / 2f
        val ambientCenterY = ambientHeight / 2f

        // Background
        val bgBitmap = Bitmap.createBitmap(ambientWidth, ambientHeight, Bitmap.Config.ARGB_8888)
        Canvas(bgBitmap).apply {
            drawColor(Color.BLACK)

            val innerTickRadius = ambientCenterX - 10
            val paint = Paint(mTickAndCirclePaint).apply {
                isAntiAlias = false
                clearShadowLayer()
            }
            for (tickIndex in 0..11) {
                val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                val innerX = sin(tickRot.toDouble()).toFloat() * innerTickRadius
                val innerY = (-cos(tickRot.toDouble())).toFloat() * innerTickRadius
                val outerX = sin(tickRot.toDouble()).toFloat() * ambientCenterX
                val outerY = (-cos(tickRot.toDouble())).toFloat() * ambientCenterX
                drawLine(
                        ambientCenterX + innerX, ambientCenterY + innerY,
                        ambientCenterX + outerX, ambientCenterY + outerY, paint
                )
            }
        }
        val bgIcon = Icon.createWithBitmap(bgBitmap)
        val bgComponent = ImageComponent.Builder()
                .setComponentId(0)
                .setZOrder(0)
                .setImage(bgIcon)
                .build()

        // Hour hand
        val hourHeight = (CENTER_GAP_AND_CIRCLE_RADIUS + sHourHandLength).toInt()
        val hourBitmap = Bitmap
                .createBitmap(CENTER_GAP_AND_CIRCLE_RADIUS.toInt(), hourHeight, Bitmap.Config.ARGB_8888)
        Canvas(hourBitmap).apply {
            val paint = Paint(mHourPaint).apply {
                isAntiAlias = false
                clearShadowLayer()
            }
            drawLine(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    paint
            )
        }
        var xOffset = (hourBitmap.width / 2f) / ambientWidth
        var yOffset = hourBitmap.height.toFloat() / ambientHeight
        var offset = RectF(
                0.5f - xOffset,
                0.5f - yOffset,
                0.5f + xOffset,
                0.5f + yOffset
        )
        val hourComponent = ImageComponent.Builder(ImageComponent.Builder.HOUR_HAND)
                .setComponentId(1)
                .setZOrder(1)
                .setImage(Icon.createWithBitmap(hourBitmap))
                .setBounds(offset)
                .build()

        // Minute hand
        val minuteHeight = (CENTER_GAP_AND_CIRCLE_RADIUS + sMinuteHandLength).toInt()
        val minuteBitmap = Bitmap
                .createBitmap(CENTER_GAP_AND_CIRCLE_RADIUS.toInt(), minuteHeight, Bitmap.Config.ARGB_8888)
        Canvas(minuteBitmap).apply {
            val paint = Paint(mMinutePaint).apply {
                isAntiAlias = false
                clearShadowLayer()
            }
            drawLine(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    paint
            )
        }
        xOffset = (minuteBitmap.width / 2f) / ambientWidth
        yOffset = minuteBitmap.height.toFloat() / ambientHeight
        offset = RectF(
                0.5f - xOffset,
                0.5f - yOffset,
                0.5f + xOffset,
                0.5f + yOffset
        )
        val minuteComponent = ImageComponent.Builder(ImageComponent.Builder.MINUTE_HAND)
                .setComponentId(2)
                .setZOrder(2)
                .setImage(Icon.createWithBitmap(minuteBitmap))
                .setBounds(offset)
                .build()

        // Second hand
        val secondHeight = (CENTER_GAP_AND_CIRCLE_RADIUS + mSecondHandLength).toInt()
        val secondBitmap = Bitmap
                .createBitmap(CENTER_GAP_AND_CIRCLE_RADIUS.toInt(), secondHeight, Bitmap.Config.ARGB_8888)
        Canvas(secondBitmap).apply {
            val paint = Paint(mSecondPaint).apply {
                isAntiAlias = false
                clearShadowLayer()
            }
            drawLine(
                    width / 2f,
                    0f,
                    width / 2f,
                    height.toFloat(),
                    paint
            )
        }
        xOffset = (secondBitmap.width / 2f) / ambientWidth
        yOffset = secondBitmap.height.toFloat() / ambientHeight
        offset = RectF(
                0.5f - xOffset,
                0.5f - yOffset,
                0.5f + xOffset,
                0.5f + yOffset
        )
        val secondComponent = ImageComponent.Builder(ImageComponent.Builder.TICKING_SECOND_HAND)
                .setComponentId(3)
                .setZOrder(3)
                .setImage(Icon.createWithBitmap(secondBitmap))
                .setBounds(offset)
                .build()

        // Center circle
        val circleBitmap = Bitmap.createBitmap(
                CENTER_GAP_AND_CIRCLE_RADIUS.toInt() * 4,
                CENTER_GAP_AND_CIRCLE_RADIUS.toInt() * 4,
                Bitmap.Config.ARGB_8888
        )
        Canvas(circleBitmap).apply {
            val paint = Paint(mTickAndCirclePaint).apply {
                isAntiAlias = false
                clearShadowLayer()
            }
            val innerPaint = Paint(paint).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            translate(width / 2f, height / 2f)
            drawCircle(0f, 0f, CENTER_GAP_AND_CIRCLE_RADIUS, innerPaint)
            drawCircle(0f, 0f, CENTER_GAP_AND_CIRCLE_RADIUS, paint)
        }
        xOffset = circleBitmap.width / (ambientWidth * 2f)
        yOffset = circleBitmap.height / (ambientHeight * 2f)
        offset = RectF(
                0.5f - xOffset,
                0.5f - yOffset,
                0.5f + xOffset,
                0.5f + yOffset
        )
        val circleComponent = ImageComponent.Builder()
                .setComponentId(4)
                .setZOrder(4)
                .setImage(Icon.createWithBitmap(circleBitmap))
                .setBounds(offset)
                .build()

        return WatchFaceDecomposition.Builder().apply {
            addImageComponents(bgComponent, hourComponent, minuteComponent, secondComponent, circleComponent)
        }.build()
    }

    override fun onCreateEngine(): Engine {
        mCalendar = Calendar.getInstance()

        initializeWatchFace()
        initializeBackground()
        updateWatchHandStyle()

        return Engine()
    }

    private fun updateWatchHandStyle() {
        if (mAmbient) {
            mHourPaint.color = Color.WHITE
            mMinutePaint.color = Color.WHITE
            mSecondPaint.color = Color.WHITE
            mTickAndCirclePaint.color = Color.WHITE

            mHourPaint.isAntiAlias = false
            mMinutePaint.isAntiAlias = false
            mSecondPaint.isAntiAlias = false
            mTickAndCirclePaint.isAntiAlias = false

            mHourPaint.clearShadowLayer()
            mMinutePaint.clearShadowLayer()
            mSecondPaint.clearShadowLayer()
            mTickAndCirclePaint.clearShadowLayer()
        } else {
            mHourPaint.color = mWatchHandColor
            mMinutePaint.color = mWatchHandColor
            mSecondPaint.color = mWatchHandHighlightColor
            mTickAndCirclePaint.color = mWatchHandColor

            mHourPaint.isAntiAlias = true
            mMinutePaint.isAntiAlias = true
            mSecondPaint.isAntiAlias = true
            mTickAndCirclePaint.isAntiAlias = true

            mHourPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
            mMinutePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
            mSecondPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
            mTickAndCirclePaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
        }
    }

    private fun initializeBackground() {
        mBackgroundPaint = Paint().apply {
            color = Color.BLACK
        }
        mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg)

        /* Extracts colors from background image to improve watchface style. */
        Palette.from(mBackgroundBitmap).generate {
            it?.let {
                mWatchHandHighlightColor = it.getVibrantColor(Color.RED)
                mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
            }
        }
    }

    private fun initializeWatchFace() {
        /* Set defaults for colors */
        mWatchHandColor = Color.WHITE
        mWatchHandHighlightColor = Color.RED
        mWatchHandShadowColor = Color.BLACK

        mHourPaint = Paint().apply {
            color = mWatchHandColor
            strokeWidth = HOUR_STROKE_WIDTH
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(
                SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
        }

        mMinutePaint = Paint().apply {
            color = mWatchHandColor
            strokeWidth = MINUTE_STROKE_WIDTH
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(
                SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
        }

        mSecondPaint = Paint().apply {
            color = mWatchHandHighlightColor
            strokeWidth = SECOND_TICK_STROKE_WIDTH
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(
                SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
        }

        mTickAndCirclePaint = Paint().apply {
            color = mWatchHandColor
            strokeWidth = SECOND_TICK_STROKE_WIDTH
            isAntiAlias = true
            style = Paint.Style.STROKE
            setShadowLayer(
                SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor
            )
        }

        mWidth = displayMetrics.widthPixels
        mHeight = displayMetrics.heightPixels
        mCenterX = mWidth / 2f
        mCenterY = mHeight / 2f

        /*
         * Calculate lengths of different hands based on watch screen size.
         */
        mSecondHandLength = (mCenterX * 0.875).toFloat()
        sMinuteHandLength = (mCenterX * 0.75).toFloat()
        sHourHandLength = (mCenterX * 0.5).toFloat()
    }

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
        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
                        .show()
            }
            invalidate()
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {

            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            val innerTickRadius = mCenterX - 10
            val outerTickRadius = mCenterX
            for (tickIndex in 0..11) {
                val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                val innerX = sin(tickRot.toDouble()).toFloat() * innerTickRadius
                val innerY = (-cos(tickRot.toDouble())).toFloat() * innerTickRadius
                val outerX = sin(tickRot.toDouble()).toFloat() * outerTickRadius
                val outerY = (-cos(tickRot.toDouble())).toFloat() * outerTickRadius
                canvas.drawLine(
                    mCenterX + innerX, mCenterY + innerY,
                    mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint
                )
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds =
                mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save()

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                mCenterX,
                mCenterY - sHourHandLength,
                mHourPaint
            )

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                mCenterX,
                mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                mCenterX,
                mCenterY - sMinuteHandLength,
                mMinutePaint
            )

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - mSecondHandLength,
                    mSecondPaint
                )

            }
            canvas.drawCircle(
                mCenterX,
                mCenterY,
                CENTER_GAP_AND_CIRCLE_RADIUS,
                mTickAndCirclePaint
            )

            /* Restore the canvas' original orientation. */
            canvas.restore()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}


