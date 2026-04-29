// Copyright 2024 ATV Cursor Enhancement for Kiwi Browser
// Provides a software mouse cursor overlay for Android TV devices.

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.chromium.base.BuildInfo;

/**
 * A custom overlay View that displays a software mouse cursor on Android TV devices.
 * This view intercepts D-pad key events from the TV remote and translates them into
 * cursor movement on screen. Pressing the center/select button on the remote
 * simulates a mouse click at the cursor's current position.
 *
 * The cursor is rendered as a classic arrow pointer using Canvas drawing.
 */
public class TvCursorOverlay extends View {
    private static final String TAG = "TvCursorOverlay";

    // Cursor movement speed in pixels per step
    private static final float CURSOR_SPEED = 8.0f;
    // How fast the cursor accelerates when holding a direction
    private static final float CURSOR_ACCELERATION = 1.5f;
    // Maximum cursor speed when holding a direction
    private static final float CURSOR_MAX_SPEED = 40.0f;
    // Delay in ms between repeat movements when holding a direction
    private static final int REPEAT_DELAY_MS = 16; // ~60fps

    private float mCursorX;
    private float mCursorY;
    private float mCurrentSpeed = CURSOR_SPEED;

    private Paint mCursorFillPaint;
    private Paint mCursorBorderPaint;
    private Path mCursorPath;

    private boolean mIsVisible = true;
    private Activity mActivity;
    private Handler mHandler;

    // Track which directions are currently pressed
    private boolean mUpPressed;
    private boolean mDownPressed;
    private boolean mLeftPressed;
    private boolean mRightPressed;

    private Runnable mMovementRunnable;

    public TvCursorOverlay(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        mHandler = new Handler(Looper.getMainLooper());

        // Initialize cursor position to center of screen
        mCursorX = context.getResources().getDisplayMetrics().widthPixels / 2.0f;
        mCursorY = context.getResources().getDisplayMetrics().heightPixels / 2.0f;

        // Setup cursor fill paint (white with slight transparency)
        mCursorFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCursorFillPaint.setColor(Color.WHITE);
        mCursorFillPaint.setStyle(Paint.Style.FILL);

        // Setup cursor border paint (dark outline)
        mCursorBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCursorBorderPaint.setColor(Color.BLACK);
        mCursorBorderPaint.setStyle(Paint.Style.STROKE);
        mCursorBorderPaint.setStrokeWidth(2.0f);

        // Build cursor arrow path (classic arrow shape, ~24x24 dp)
        mCursorPath = new Path();
        buildCursorPath();

        // Movement runnable for continuous movement when holding D-pad
        mMovementRunnable = new Runnable() {
            @Override
            public void run() {
                boolean moved = false;
                if (mUpPressed) {
                    mCursorY -= mCurrentSpeed;
                    moved = true;
                }
                if (mDownPressed) {
                    mCursorY += mCurrentSpeed;
                    moved = true;
                }
                if (mLeftPressed) {
                    mCursorX -= mCurrentSpeed;
                    moved = true;
                }
                if (mRightPressed) {
                    mCursorX += mCurrentSpeed;
                    moved = true;
                }

                if (moved) {
                    // Accelerate
                    mCurrentSpeed = Math.min(mCurrentSpeed * CURSOR_ACCELERATION, CURSOR_MAX_SPEED);
                    // Clamp to screen bounds
                    clampCursorPosition();
                    invalidate();
                    mHandler.postDelayed(this, REPEAT_DELAY_MS);
                }
            }
        };

        // Make this view not focusable and clickable to not interfere with normal navigation
        setFocusable(false);
        setClickable(false);
        setLongClickable(false);
    }

    private void buildCursorPath() {
        float scale = getContext().getResources().getDisplayMetrics().density;
        // Scale the cursor to be visible on TV (larger than phone)
        float s = scale * 1.5f;

        mCursorPath.reset();
        // Classic arrow cursor shape
        mCursorPath.moveTo(0, 0);           // Tip of arrow
        mCursorPath.lineTo(0, 21 * s);      // Down-left edge
        mCursorPath.lineTo(4.5f * s, 17 * s); // Notch for stem
        mCursorPath.lineTo(8.5f * s, 24 * s); // Bottom of stem
        mCursorPath.lineTo(12 * s, 22 * s);   // Right side of stem
        mCursorPath.lineTo(8 * s, 15.5f * s); // Top of stem (back to arrow)
        mCursorPath.lineTo(14.5f * s, 15.5f * s); // Right side of arrow head
        mCursorPath.close();
    }

    private void clampCursorPosition() {
        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;

        if (mCursorX < 0) mCursorX = 0;
        if (mCursorY < 0) mCursorY = 0;
        if (mCursorX > screenWidth) mCursorX = screenWidth;
        if (mCursorY > screenHeight) mCursorY = screenHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mIsVisible) return;

        canvas.save();
        canvas.translate(mCursorX, mCursorY);

        // Draw shadow for better visibility
        canvas.save();
        canvas.translate(2, 2);
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(80, 0, 0, 0));
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(mCursorPath, shadowPaint);
        canvas.restore();

        // Draw filled cursor
        canvas.drawPath(mCursorPath, mCursorFillPaint);
        // Draw cursor border
        canvas.drawPath(mCursorPath, mCursorBorderPaint);

        canvas.restore();
    }

    /**
     * Handles D-pad key events to move the cursor.
     * @return true if the event was consumed by the cursor overlay.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (!mIsVisible) return false;

        int keyCode = event.getKeyCode();
        int action = event.getAction();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (!mUpPressed) {
                        mUpPressed = true;
                        mCurrentSpeed = CURSOR_SPEED;
                        startMovement();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    mUpPressed = false;
                    if (!anyDirectionPressed()) stopMovement();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (!mDownPressed) {
                        mDownPressed = true;
                        mCurrentSpeed = CURSOR_SPEED;
                        startMovement();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    mDownPressed = false;
                    if (!anyDirectionPressed()) stopMovement();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (!mLeftPressed) {
                        mLeftPressed = true;
                        mCurrentSpeed = CURSOR_SPEED;
                        startMovement();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    mLeftPressed = false;
                    if (!anyDirectionPressed()) stopMovement();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (!mRightPressed) {
                        mRightPressed = true;
                        mCurrentSpeed = CURSOR_SPEED;
                        startMovement();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    mRightPressed = false;
                    if (!anyDirectionPressed()) stopMovement();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    simulateClick();
                }
                return true;
        }

        return false;
    }

    private boolean anyDirectionPressed() {
        return mUpPressed || mDownPressed || mLeftPressed || mRightPressed;
    }

    private void startMovement() {
        mHandler.removeCallbacks(mMovementRunnable);
        mHandler.post(mMovementRunnable);
    }

    private void stopMovement() {
        mHandler.removeCallbacks(mMovementRunnable);
        mCurrentSpeed = CURSOR_SPEED;
    }

    /**
     * Simulates a touch click at the current cursor position by dispatching
     * MotionEvent.ACTION_DOWN and ACTION_UP to the activity's root view.
     */
    private void simulateClick() {
        if (mActivity == null) return;

        View rootView = mActivity.getWindow().getDecorView();
        long downTime = SystemClock.uptimeMillis();

        // Dispatch ACTION_DOWN
        MotionEvent downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                mCursorX, mCursorY, 0);
        downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        rootView.dispatchTouchEvent(downEvent);
        downEvent.recycle();

        // Dispatch ACTION_UP after a short delay to simulate a tap
        long upTime = SystemClock.uptimeMillis() + 50;
        MotionEvent upEvent = MotionEvent.obtain(
                downTime, upTime, MotionEvent.ACTION_UP,
                mCursorX, mCursorY, 0);
        upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        mHandler.postDelayed(() -> {
            rootView.dispatchTouchEvent(upEvent);
            upEvent.recycle();
        }, 50);
    }

    /**
     * Sets the host activity for dispatching simulated touch events.
     */
    public void setActivity(Activity activity) {
        mActivity = activity;
    }

    /**
     * Shows or hides the cursor overlay.
     */
    public void setCursorVisible(boolean visible) {
        mIsVisible = visible;
        invalidate();
    }

    /**
     * Call this to clean up when the activity is destroyed.
     */
    public void destroy() {
        stopMovement();
        mActivity = null;
    }

    // ---- Static helper to set up the overlay on an Activity ----

    /**
     * Attaches the TV cursor overlay to the given activity if we're running on a TV device.
     * @param activity The activity to attach the cursor overlay to.
     * @return The TvCursorOverlay instance if attached, or null if not a TV.
     */
    public static TvCursorOverlay attachToActivityIfTV(Activity activity) {
        if (!BuildInfo.getInstance().isTV) {
            return null;
        }

        TvCursorOverlay overlay = new TvCursorOverlay(activity);
        overlay.setActivity(activity);

        // Add the overlay on top of the activity's content view
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        decorView.addView(overlay, params);

        // Make sure the overlay doesn't consume focus
        overlay.setFocusable(false);
        overlay.setFocusableInTouchMode(false);

        return overlay;
    }
}
