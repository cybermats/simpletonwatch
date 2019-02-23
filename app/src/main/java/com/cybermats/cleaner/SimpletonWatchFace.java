package com.cybermats.cleaner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class SimpletonWatchFace extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = //TimeUnit.SECONDS.toMillis(1);
            TimeUnit.MILLISECONDS.toMillis(33);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SimpletonWatchFace.Engine> mWeakReference;

        EngineHandler(SimpletonWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SimpletonWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float FACE_DIAMETER = 29f;
        private static final float FACE_RADIUS = FACE_DIAMETER / 2f;
        private static final float OUTER_TICK_RADIUS = FACE_RADIUS - 1f;
        private static final float INNER_SMALL_TICK_RADIUS = OUTER_TICK_RADIUS - 1f;
        private static final float INNER_LARGE_TICK_RADIUS = OUTER_TICK_RADIUS - 4f;
        private static final float LARGE_TICK_WIDTH = 0.5f;

        private static final float SECOND_BACK_RADIUS = 2.5f;
        private static final float SECOND_FRONT_RADIUS = 13f;
        private static final float SECOND_WIDTH = 0.5f;
        private static final float MINUTE_BACK_RADIUS = 2.5f;
        private static final float MINUTE_FRONT_RADIUS = 12f;
        private static final float MINUTE_HIGHLIGHT_RADIUS = 6f;
        private static final float MINUTE_WIDTH = .5f;
        private static final float HOUR_BACK_RADIUS = 2.5f;
        private static final float HOUR_FRONT_RADIUS = 8f;
        private static final float HOUR_HIGHLIGHT_RADIUS = 4f;
        private static final float HOUR_WIDTH = .5f;
        private static final float HOUR_CIRCLE = 1f;

        private static final float DATE_TEXT_LEFT = 8.5f;
        private static final float DATE_TEXT_WIDTH = 4f;
        private static final float DATE_TEXT_HEIGHT = 3f;

        private static final int SHADOW_RADIUS = 6;


        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;

        private float mOuterTickRadius;
        private float mInnerSmallTickRadius;
        private float mInnerLargeTickRadius;
        private float mLargeTickWidth;
        private float mSecondHandFrontLength;
        private float mSecondHandBackLength;
        private float mSecondHandWidth;
        private float mMinuteHandFrontLength;
        private float mMinuteHandBackLength;
        private float mMinuteHandHighlightLength;
        private float mMinuteHandWidth;
        private float mHourHandFrontLength;
        private float mHourHandBackLength;
        private float mHourHandHighlightLength;
        private float mHourHandWidth;
        private float mHourHandCircle;

        private RectF mDateTextRect;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */

        private int mWatchTickColor;
        private int mWatchBackgroundColor;
        private int mWatchMainColor;
        private int mWatchSecondaryColor;
        private int mWatchHandShadowColor;
        private int mWatchSecondColor;

        private Paint mMainPaint;
        private Paint mSecondaryPaint;
        private Paint mSecondPaint;
        private Paint mBackgroundPaint;
        private Paint mSmallTickPaint;
        private Paint mDateTextPaint;
        private Paint mDateBoxPaint;
        private boolean mAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SimpletonWatchFace.this)
                    .setAcceptsTapEvents(false)
                    .setStatusBarGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground();
            initializeWatchFace();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
        }

        private void initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandShadowColor = Color.BLACK;

            mWatchBackgroundColor = Color.BLACK;
            mWatchTickColor = Color.LTGRAY;
            mWatchMainColor = Color.WHITE;
            mWatchSecondaryColor = Color.DKGRAY;
            mWatchSecondColor = Color.RED;

            mMainPaint = new Paint();
            mMainPaint.setColor(mWatchMainColor);
            mMainPaint.setAntiAlias(true);
            mMainPaint.setStrokeCap(Paint.Cap.BUTT);
            mMainPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            mMainPaint.setTextSize(20);
            mMainPaint.setTextAlign(Paint.Align.LEFT);

            mSecondaryPaint = new Paint();
            mSecondaryPaint.setColor(mWatchSecondaryColor);
            mSecondaryPaint.setAntiAlias(true);
            mSecondaryPaint.setStrokeCap(Paint.Cap.BUTT);


            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchSecondColor);
            mSecondPaint.setStrokeWidth(1f);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.BUTT);
            mSecondPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSmallTickPaint = new Paint();
            mSmallTickPaint.setColor(mWatchTickColor);
            mSmallTickPaint.setAntiAlias(true);

            mDateTextPaint = new Paint();
            mDateTextPaint.setColor(mWatchMainColor);
            mDateTextPaint.setAntiAlias(true);
            mDateTextPaint.setTextSize(20);
            mDateTextPaint.setTextAlign(Paint.Align.LEFT);

            mDateBoxPaint = new Paint();
            mDateBoxPaint.setColor(mWatchSecondaryColor);
            mDateBoxPaint.setAntiAlias(true);
            mDateBoxPaint.setStrokeWidth(0);
            mDateBoxPaint.setStyle(Paint.Style.STROKE);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle() {
            if (mAmbient) {
                mMainPaint.setColor(Color.WHITE);
                mSecondaryPaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mSmallTickPaint.setColor(Color.WHITE);
                mDateTextPaint.setColor(Color.WHITE);

                mMainPaint.setAntiAlias(false);
                mSecondaryPaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mSmallTickPaint.setAntiAlias(false);
                mDateTextPaint.setAntiAlias(false);

                mMainPaint.setStyle(Paint.Style.STROKE);
                mSecondaryPaint.setStyle(Paint.Style.STROKE);
                mSecondPaint.setStyle(Paint.Style.STROKE);
                mSmallTickPaint.setStyle(Paint.Style.STROKE);

            } else {
                mMainPaint.setColor(mWatchMainColor);
                mSecondaryPaint.setColor(mWatchSecondaryColor);
                mSecondPaint.setColor(mWatchSecondColor);
                mSmallTickPaint.setColor(mWatchTickColor);
                mDateTextPaint.setColor(mWatchMainColor);

                mMainPaint.setAntiAlias(true);
                mSecondaryPaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mSmallTickPaint.setAntiAlias(true);
                mDateTextPaint.setAntiAlias(true);

                mMainPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mSecondaryPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mSecondPaint.setStyle(Paint.Style.FILL_AND_STROKE);
                mSmallTickPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mMainPaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondaryPaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            float radius = width / 2f;
            mOuterTickRadius = radius * (OUTER_TICK_RADIUS / FACE_RADIUS);
            mInnerSmallTickRadius = radius * (INNER_SMALL_TICK_RADIUS / FACE_RADIUS);
            mInnerLargeTickRadius = radius * (INNER_LARGE_TICK_RADIUS / FACE_RADIUS);
            mLargeTickWidth = radius * LARGE_TICK_WIDTH / FACE_RADIUS;

            mSecondHandFrontLength = radius * (SECOND_FRONT_RADIUS / FACE_RADIUS);
            mSecondHandBackLength = radius * (SECOND_BACK_RADIUS / FACE_RADIUS);
            mSecondHandWidth = radius * (SECOND_WIDTH / FACE_RADIUS);

            mMinuteHandFrontLength = radius * (MINUTE_FRONT_RADIUS / FACE_RADIUS);
            mMinuteHandBackLength = radius * (MINUTE_BACK_RADIUS / FACE_RADIUS);
            mMinuteHandHighlightLength = radius * (MINUTE_HIGHLIGHT_RADIUS / FACE_RADIUS);
            mMinuteHandWidth = radius * (MINUTE_WIDTH / FACE_RADIUS);

            mHourHandFrontLength = radius * (HOUR_FRONT_RADIUS / FACE_RADIUS);
            mHourHandBackLength = radius * (HOUR_BACK_RADIUS / FACE_RADIUS);
            mHourHandHighlightLength = radius * (HOUR_HIGHLIGHT_RADIUS / FACE_RADIUS);
            mHourHandWidth = radius * (HOUR_WIDTH / FACE_RADIUS);
            mHourHandCircle = radius * (HOUR_CIRCLE / FACE_RADIUS);

            final float dateTextLeft = radius * (DATE_TEXT_LEFT / FACE_RADIUS);
            final float dateTextWidth = radius * (DATE_TEXT_WIDTH / FACE_RADIUS);
            final float dateTextHeight = radius * (DATE_TEXT_HEIGHT / FACE_RADIUS);

            mDateTextRect = new RectF(mCenterX + dateTextLeft,
                    mCenterY - dateTextHeight / 2f,
                    mCenterX + dateTextLeft + dateTextWidth,
                    mCenterY + dateTextHeight / 2f);
        }

        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            canvas.drawColor(mWatchBackgroundColor);
            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            int ticks = 60;
            int skipTicks = 5;
            for (int tickIndex = 0; tickIndex < ticks; tickIndex++) {
                if ((tickIndex % skipTicks == 0) && (tickIndex != 15))
                    continue;
                float tickRot = (float) (tickIndex * Math.PI * 2 / ticks);
                float innerX = (float) Math.sin(tickRot) * mInnerSmallTickRadius;
                float innerY = (float) -Math.cos(tickRot) * mInnerSmallTickRadius;
                float outerX = (float) Math.sin(tickRot) * mOuterTickRadius;
                float outerY = (float) -Math.cos(tickRot) * mOuterTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mSmallTickPaint);
            }

            if (!mAmbient) {
                float width = mLargeTickWidth * 5 / 3f;
                RectF rect = new RectF(mCenterX - (width / 2f),
                        mCenterY - mOuterTickRadius,
                        mCenterX + (width / 2f),
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mMainPaint);

                rect = new RectF(mCenterX - (width * 3 / 5f) / 2f,
                        mCenterY - mOuterTickRadius,
                        mCenterX - (width * 1 / 5f) / 2f,
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mSecondaryPaint);

                rect = new RectF(mCenterX + (width * 1 / 5f) / 2f,
                        mCenterY - mOuterTickRadius,
                        mCenterX + (width * 3 / 5f) / 2f,
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mSecondaryPaint);
            } else {
                float width = mLargeTickWidth * 5 / 3f;
                RectF rect = new RectF(mCenterX - (width / 2f),
                        mCenterY - mOuterTickRadius,
                        mCenterX + (width / 2f),
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mMainPaint);
                rect = new RectF(mCenterX - (width / 6f),
                        mCenterY - mOuterTickRadius,
                        mCenterX + (width / 6f),
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mMainPaint);

            }


            ticks = 12;
            for (int tickIndex = 1; tickIndex < ticks; tickIndex++) {
                if (tickIndex == 3)
                    continue;
                canvas.save();
                canvas.rotate(tickIndex * (360f / ticks), mCenterX, mCenterY);
                RectF rect = new RectF(mCenterX - (mLargeTickWidth / 2f),
                        mCenterY - mOuterTickRadius,
                        mCenterX + (mLargeTickWidth / 2f),
                        mCenterY - mInnerLargeTickRadius);
                canvas.drawRect(
                        rect, mMainPaint);
                if (!mAmbient) {
                    rect = new RectF(mCenterX - (mLargeTickWidth / 6f),
                            mCenterY - mOuterTickRadius,
                            mCenterX + (mLargeTickWidth / 6f),
                            mCenterY - mInnerLargeTickRadius);
                    canvas.drawRect(
                            rect, mSecondaryPaint);
                }
                canvas.restore();
            }

        }

        private void drawWatchFace(Canvas canvas) {

            final int date = mCalendar.get(Calendar.DAY_OF_MONTH);
            final String dateStr = String.format(Locale.getDefault(), "%d", date);
            final Rect textBound = new Rect();
            mMainPaint.getTextBounds(dateStr, 0, dateStr.length(), textBound);
            canvas.drawText(dateStr,
                    mCenterX + mInnerLargeTickRadius,
                    mCenterY + textBound.height() / 2f,
                    mDateTextPaint);
            if (!mAmbient) {
                canvas.drawRect(mDateTextRect, mDateBoxPaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesOffset = mCalendar.get(Calendar.SECOND) / 10f;
            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f + minutesOffset;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f +
                    mCalendar.get(Calendar.SECOND) / 120f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;


            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            if (!mAmbient) {
                canvas.drawCircle(mCenterX, mCenterY, mHourHandCircle, mMainPaint);
            }

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            RectF rect = new RectF(
                    mCenterX - (mHourHandWidth / 2f),
                    mCenterY + mHourHandBackLength,
                    mCenterX + (mHourHandWidth / 2f),
                    mCenterY - mHourHandFrontLength);
            canvas.drawRect(rect, mMainPaint);

            if (!mAmbient) {
                rect = new RectF(
                        mCenterX - (mHourHandWidth / 6f),
                        mCenterY + mHourHandBackLength,
                        mCenterX + (mHourHandWidth / 6f),
                        mCenterY - mHourHandHighlightLength);
                canvas.drawRect(rect, mSecondaryPaint);

            }

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);

            rect = new RectF(
                    mCenterX - (mMinuteHandWidth / 2f),
                    mCenterY + mMinuteHandBackLength,
                    mCenterX + (mMinuteHandWidth / 2f),
                    mCenterY - mMinuteHandFrontLength);
            canvas.drawRect(rect, mMainPaint);

            if (!mAmbient) {
                rect = new RectF(
                        mCenterX - (mMinuteHandWidth / 6f),
                        mCenterY + mMinuteHandBackLength,
                        mCenterX + (mMinuteHandWidth / 6f),
                        mCenterY - mMinuteHandHighlightLength);
                canvas.drawRect(rect, mSecondaryPaint);
            }


            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                Path path = new Path();
                path.moveTo(mCenterX, mCenterY - mSecondHandFrontLength);
                path.lineTo(mCenterX - (mSecondHandWidth / 2), mCenterY + mSecondHandBackLength);
                path.lineTo(mCenterX + (mSecondHandWidth / 2), mCenterY + mSecondHandBackLength);
                path.lineTo(mCenterX, mCenterY - mSecondHandFrontLength);
                canvas.drawPath(path, mSecondPaint);

            }
            canvas.restore();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SimpletonWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SimpletonWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
