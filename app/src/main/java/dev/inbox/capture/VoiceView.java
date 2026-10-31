package dev.inbox.capture;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * The capture card's one visual, in one violet-to-cyan gradient throughout:
 * listening shows bars that rise and fall with your voice, thinking folds them into a glowing
 * ring with light chasing around it, done completes the ring and draws a check.
 */
public class VoiceView extends View {

    enum Mode { IDLE, LISTENING, THINKING, DONE }

    private static final int BARS = 21;

    private final float dp;
    private final int startColor;
    private final int endColor;
    private final int idleColor;
    private final Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = stroke();
    private final Paint comet = stroke();
    private final Paint glow = stroke();
    private final Paint check = stroke();
    private final Matrix rotation = new Matrix();
    private final float[] shown = new float[BARS];
    private final Path checkPath = new Path();
    private final Path checkPart = new Path();
    private PathMeasure checkMeasure;
    private SweepGradient cometShader;
    private LinearGradient barShader;

    private Mode mode = Mode.IDLE;
    private long modeSince;
    private float targetLevel;
    private float level;
    private float ringMix; // 0 = bars, 1 = ring

    public VoiceView(Context c, AttributeSet attrs) {
        super(c, attrs);
        dp = getResources().getDisplayMetrics().density;
        startColor = c.getColor(R.color.accent);
        endColor = c.getColor(R.color.accent_end);
        idleColor = c.getColor(R.color.text_secondary);
        glow.setMaskFilter(new BlurMaskFilter(7 * dp, BlurMaskFilter.Blur.NORMAL));
        setLayerType(LAYER_TYPE_SOFTWARE, null); // BlurMaskFilter needs it; the view is small
    }

    private static Paint stroke() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    /** @param level 0 (background noise) .. 1 (loud speech). */
    public void setLevel(float level) {
        targetLevel = level;
    }

    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        modeSince = SystemClock.uptimeMillis();
        if (mode != Mode.LISTENING) targetLevel = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        barShader = new LinearGradient(0, 0, w, 0, startColor, endColor, Shader.TileMode.CLAMP);
        float cx = w / 2f, cy = h / 2f, r = radius();
        LinearGradient diagonal = new LinearGradient(cx - r, cy - r, cx + r, cy + r, startColor, endColor, Shader.TileMode.CLAMP);
        ring.setShader(diagonal);
        check.setShader(diagonal);
        // A comet: transparent tail brightening to a near-white head.
        int clear = startColor & 0x00FFFFFF;
        cometShader = new SweepGradient(cx, cy,
                new int[]{clear, clear, startColor, endColor, Color.WHITE},
                new float[]{0f, 0.45f, 0.75f, 0.97f, 1f});
        comet.setShader(cometShader);
        glow.setShader(cometShader);

        checkPath.reset();
        checkPath.moveTo(cx - r * 0.4f, cy + r * 0.02f);
        checkPath.lineTo(cx - r * 0.1f, cy + r * 0.32f);
        checkPath.lineTo(cx + r * 0.42f, cy - r * 0.3f);
        checkMeasure = new PathMeasure(checkPath, false);
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) * 0.34f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        double t = now / 1000.0;
        float since = (now - modeSince) / 1000f;
        boolean ringMode = mode == Mode.THINKING || mode == Mode.DONE;
        ringMix += ((ringMode ? 1 : 0) - ringMix) * 0.16f;
        boolean animating = mode != Mode.IDLE || Math.abs((ringMode ? 1 : 0) - ringMix) > 0.005f;

        animating |= drawBars(canvas, t);
        if (ringMix > 0.01f) drawRing(canvas, t, since);
        if (animating) postInvalidateOnAnimation();
    }

    /** @return whether bars are still settling. */
    private boolean drawBars(Canvas canvas, double t) {
        float w = getWidth(), h = getHeight();
        float slot = w / BARS;
        float bar = slot * 0.5f;
        float cy = h / 2f;
        float fade = 1 - ringMix;
        if (fade < 0.01f) return false;

        // Rise quickly with the voice, fall back gently.
        level += (targetLevel - level) * (targetLevel > level ? 0.45f : 0.12f);
        boolean idle = mode == Mode.IDLE;
        bars.setShader(idle ? null : barShader);
        bars.setColor(idle ? idleColor : Color.BLACK);
        bars.setAlpha((int) ((idle ? 110 : 255) * fade));

        boolean settling = false;
        for (int i = 0; i < BARS; i++) {
            float envelope = (float) Math.pow(Math.sin(Math.PI * (i + 0.5) / BARS), 0.8);
            float goal = 0;
            if (mode == Mode.LISTENING) {
                float wobble = (float) (0.55 + 0.45 * Math.sin(t * (4.0 + (i % 5) * 0.9) + i * 1.7));
                goal = (0.14f + 0.86f * level) * wobble * envelope * 0.6f; // a gentle sway shows it's listening
            }
            shown[i] += (goal - shown[i]) * 0.35f;
            if (Math.abs(goal - shown[i]) > 0.003f) settling = true;
            float bh = Math.max(bar, shown[i] * h * fade);
            // Bars draw in toward the centre as they hand over to the ring.
            float x = w / 2f + (i * slot + (slot - bar) / 2f - w / 2f) * (0.4f + 0.6f * fade);
            canvas.drawRoundRect(x, cy - bh / 2f, x + bar, cy + bh / 2f, bar / 2f, bar / 2f, bars);
        }
        return settling;
    }

    private void drawRing(Canvas canvas, double t, float since) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        int alpha = (int) (255 * ringMix);
        float r = radius();

        if (mode == Mode.THINKING) {
            r *= 1 + 0.035f * (float) Math.sin(t * 2.4); // breathing
            ring.setStrokeWidth(1.5f * dp);
            ring.setAlpha(alpha / 4);
            canvas.drawCircle(cx, cy, r, ring);

            drawComet(canvas, cx, cy, r, (float) (t * 330 % 360), alpha);
            drawComet(canvas, cx, cy, r, (float) (-t * 200 % 360), alpha / 2);
        } else { // DONE: the ring closes, then the check draws in
            ring.setStrokeWidth(3f * dp);
            ring.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, ring);
            glow.setShader(ring.getShader());
            glow.setStrokeWidth(8 * dp);
            glow.setAlpha(alpha / 3);
            canvas.drawCircle(cx, cy, r, glow);
            glow.setShader(cometShader);

            float progress = Math.min(1f, Math.max(0f, (since - 0.08f) / 0.35f));
            progress = 1 - (1 - progress) * (1 - progress); // ease out
            checkPart.reset();
            checkMeasure.getSegment(0, checkMeasure.getLength() * progress, checkPart, true);
            check.setStrokeWidth(4f * dp);
            check.setAlpha(alpha);
            canvas.drawPath(checkPart, check);
        }
    }

    private void drawComet(Canvas canvas, float cx, float cy, float r, float degrees, int alpha) {
        rotation.setRotate(degrees, cx, cy);
        cometShader.setLocalMatrix(rotation);
        glow.setStrokeWidth(9 * dp);
        glow.setAlpha(alpha * 2 / 3);
        canvas.drawCircle(cx, cy, r, glow);
        comet.setStrokeWidth(3.5f * dp);
        comet.setAlpha(alpha);
        canvas.drawCircle(cx, cy, r, comet);
    }
}
