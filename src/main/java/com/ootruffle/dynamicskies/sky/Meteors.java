package com.ootruffle.dynamicskies.sky;

import java.util.Random;

/** Up to four shooting stars at a time, spawned randomly, on beats and on drops. */
final class Meteors {

    static final int MAX = 4;
    private static final float LIFE = 1.1f;

    private final Random random = new Random();
    // start direction xyz + age, travel direction xyz + brightness
    final float[][] start = new float[MAX][4];
    final float[][] dir = new float[MAX][4];

    Meteors() {
        for (float[] m : start) m[3] = -1f;
    }

    void update(float dt) {
        for (float[] m : start) {
            if (m[3] >= 0f) {
                m[3] += dt;
                if (m[3] > LIFE) m[3] = -1f;
            }
        }
    }

    /** Spawns one shooting star in a free slot, if there is one. */
    void spawn(float brightness) {
        for (int i = 0; i < MAX; i++) {
            if (start[i][3] < 0f) {
                launch(i, brightness);
                return;
            }
        }
    }

    private void launch(int i, float brightness) {
        // Somewhere between low and high in the sky, any direction.
        double az = random.nextDouble() * Math.PI * 2;
        double el = Math.toRadians(20 + random.nextDouble() * 50);
        float sx = (float) (Math.cos(el) * Math.cos(az));
        float sy = (float) Math.sin(el);
        float sz = (float) (Math.cos(el) * Math.sin(az));

        // Travel direction: tangent to the sky, angled mostly sideways and down.
        float ex = (float) -Math.sin(az), ez = (float) Math.cos(az); // "east" at the start point
        float nx = -sy * (float) Math.cos(az), ny = (float) Math.cos(el), nz = -sy * (float) Math.sin(az); // "up"
        double ang = (random.nextBoolean() ? 1 : -1) * (0.2 + random.nextDouble() * 0.6);
        float side = (float) Math.cos(ang), down = (float) -Math.abs(Math.sin(ang));
        float dx = ex * side + nx * down, dy = ny * down, dz = ez * side + nz * down;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        start[i][0] = sx;
        start[i][1] = sy;
        start[i][2] = sz;
        start[i][3] = 0f;
        dir[i][0] = dx / len;
        dir[i][1] = dy / len;
        dir[i][2] = dz / len;
        dir[i][3] = brightness;
    }

    boolean chance(float perMinute, float dt) {
        return random.nextFloat() < perMinute / 60f * dt;
    }
}
