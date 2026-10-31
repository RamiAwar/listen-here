package dev.inbox.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The capture card's only visual: a row of bars.
 * Listening, they rise and fall with your voice; thinking, they hand over to three hopping dots;
 * otherwise they rest as dots.
 */
public class VoiceView extends View {

    enum Mode { IDLE, LISTENING, THINKING, DONE }

    private static final int BARS = 21;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] shown = new float[BARS];
    private Mode mode = Mode.IDLE;
    private float targetLevel;
    private float level;
    private float dotsMix; // 0 = bars, 1 = the three thinking dots

    public VoiceView(Context c, AttributeSet attrs) {
        super(c, attrs);
        paint.setColor(c.getColor(R.color.accent));
    }

    /** @param level 0 (background noise) .. 1 (loud speech). */
    public void setLevel(float level) {
        targetLevel = level;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode != Mode.LISTENING) targetLevel = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float slot = w / BARS;
        float bar = slot * 0.5f;
        float cy = h / 2f;
        double t = SystemClock.uptimeMillis() / 1000.0;

        // Rise quickly with the voice, fall back gently.
        level += (targetLevel - level) * (targetLevel > level ? 0.45f : 0.12f);

        boolean thinking = mode == Mode.THINKING;
        dotsMix += ((thinking ? 1 : 0) - dotsMix) * 0.15f;
        boolean moving = mode == Mode.LISTENING || thinking || Math.abs((thinking ? 1 : 0) - dotsMix) > 0.005f;
        paint.setAlpha((int) (255 * (1 - dotsMix)));
        for (int i = 0; i < BARS; i++) {
            // Middle bars reach highest, so the row reads as one shape.
            float envelope = (float) Math.pow(Math.sin(Math.PI * (i + 0.5) / BARS), 0.8);
            float goal = 0;
            if (mode == Mode.LISTENING) {
                float wobble = (float) (0.55 + 0.45 * Math.sin(t * (4.0 + (i % 5) * 0.9) + i * 1.7));
                goal = (0.1f + 0.9f * level) * wobble * envelope;
            }
            shown[i] += (goal - shown[i]) * 0.3f;
            if (Math.abs(goal - shown[i]) > 0.003f) moving = true;
            float bh = Math.max(bar, shown[i] * h);
            float x = i * slot + (slot - bar) / 2f;
            canvas.drawRoundRect(x, cy - bh / 2f, x + bar, cy + bh / 2f, bar / 2f, bar / 2f, paint);
        }
        if (dotsMix > 0.01f) drawDots(canvas, t, bar * 2.2f);
        if (moving) postInvalidateOnAnimation();
    }

    /** Three dots hopping one after another, like a "typing…" indicator. */
    private void drawDots(Canvas canvas, double t, float size) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        paint.setAlpha((int) (255 * dotsMix));
        for (int i = 0; i < 3; i++) {
            float hop = (float) Math.max(0, Math.sin(t * 6 - i * 0.9)) * size * 1.2f;
            canvas.drawCircle(cx + (i - 1) * size * 2.2f, cy - hop, size / 2f, paint);
        }
    }
}
