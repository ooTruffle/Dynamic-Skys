package com.ootruffle.dynamicskies.sky;

import java.util.Random;

/**
 * Firework shells for the Fireworks sky. One launches on each beat, sized by the bass.
 * With no music (or a quiet stretch) they launch on their own every so often.
 */
final class Fireworks {

    static final int MAX = 8;
    /** Seconds a shell lives: about 0.7 rising, the rest bursting and fading. */
    private static final float LIFE = 3.2f;
    /** With no beat for this long, shells launch on their own. */
    private static final float IDLE_AFTER = 1.5f;
    private static final float MIN_GAP = 0.12f;

    private final Random random = new Random();
    // burst direction xyz + age (< 0 = unused), hue + size + seed + rise time
    final float[][] shell = new float[MAX][4];
    final float[][] info = new float[MAX][4];

    private float sinceBeat = 100f;
    private float sinceLaunch = 100f;
    private float idleTimer;

    Fireworks() {
        for (float[] s : shell) s[3] = -1f;
    }

    void update(float dt, boolean beat, float bass) {
        for (float[] s : shell) {
            if (s[3] >= 0f) {
                s[3] += dt;
                if (s[3] > LIFE) s[3] = -1f;
            }
        }
        sinceBeat += dt;
        sinceLaunch += dt;

        if (beat) {
            sinceBeat = 0f;
            if (sinceLaunch > MIN_GAP) {
                launch(0.55f + Math.min(bass, 2f) * 0.45f);
            }
        } else if (sinceBeat > IDLE_AFTER) {
            idleTimer -= dt;
            if (idleTimer <= 0f) {
                idleTimer = 0.35f + random.nextFloat() * 0.9f;
                launch(0.6f + random.nextFloat() * 0.5f);
            }
        }
    }

    private void launch(float size) {
        int slot = -1;
        float oldest = -1f;
        for (int i = 0; i < MAX; i++) {
            if (shell[i][3] < 0f) {
                slot = i;
                break;
            }
            if (shell[i][3] > oldest) {
                oldest = shell[i][3];
                slot = i;
            }
        }
        sinceLaunch = 0f;

        double az = random.nextDouble() * Math.PI * 2;
        double el = Math.toRadians(22 + random.nextDouble() * 38);
        shell[slot][0] = (float) (Math.cos(el) * Math.cos(az));
        shell[slot][1] = (float) Math.sin(el);
        shell[slot][2] = (float) (Math.cos(el) * Math.sin(az));
        shell[slot][3] = 0f;
        info[slot][0] = random.nextFloat();
        info[slot][1] = size;
        info[slot][2] = random.nextFloat() * 100f;
        info[slot][3] = 0.55f + random.nextFloat() * 0.25f;
    }
}
