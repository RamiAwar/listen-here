package dev.inbox.capture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The capture card's only visual: a row of bars.
 * Listening, they rise and fall with your voice. Thinking, they slide together into three dots
 * that hop like a "typing…" indicator. Done, the dots come to rest.
 */
public class VoiceView extends View {

    enum Mode { IDLE, LISTENING, THINKING, DONE }

    private static final int BARS = 11;
    private static final long MORPH_MS = 650;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] shown = new float[BARS];
    private Mode mode = Mode.IDLE;
    private float targetLevel;
    private float level;
    private float hop; // eases in while thinking, out when done

    // Bars -> dots morph, driven by time so its speed doesn't depend on the frame rate.
    private float morphFrom;
    private float morphTo;
    private long morphStart;

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
        float to = mode == Mode.THINKING || mode == Mode.DONE ? 1 : 0;
        if (to != morphTo) {
            morphFrom = morph(SystemClock.uptimeMillis());
            morphTo = to;
            morphStart = SystemClock.uptimeMillis();
        }
        postInvalidateOnAnimation();
    }

    private float morph(long now) {
        float p = Math.min(1f, (now - morphStart) / (float) MORPH_MS);
        float eased = p < 0.5f ? 4 * p * p * p : 1 - (float) Math.pow(-2 * p + 2, 3) / 2; // ease in-out
        return morphFrom + (morphTo - morphFrom) * eased;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        double t = now / 1000.0;
        float w = getWidth(), h = getHeight();
        float slot = w / BARS;
        float bar = slot * 0.62f;
        float dot = w * 0.052f;
        float cx = w / 2f, cy = h / 2f;
        float m = morph(now);

        // Rise quickly with the voice, fall back gently.
        level += (targetLevel - level) * (targetLevel > level ? 0.45f : 0.12f);
        hop += ((mode == Mode.THINKING ? 1 : 0) - hop) * 0.12f;

        boolean moving = mode == Mode.LISTENING || mode == Mode.THINKING
                || now - morphStart < MORPH_MS || hop > 0.005f;
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

            // Each bar belongs to one of three dots and slides into it, shrinking as it goes.
            int k = i * 3 / BARS;
            float dotX = cx + (k - 1) * dot * 2.2f;
            float dotY = cy - (float) Math.max(0, Math.sin(t * 6 - k * 0.9)) * dot * 1.2f * hop;
            float x = lerp(i * slot + slot / 2f, dotX, m);
            float y = lerp(cy, dotY, m);
            float bw = lerp(bar, dot, m);
            float bh = lerp(Math.max(bar, shown[i] * h), dot, m);
            float r = Math.min(bw, bh) / 2f;
            canvas.drawRoundRect(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f, r, r, paint);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private static float lerp(float a, float b, float p) {
        return a + (b - a) * p;
    }
}
