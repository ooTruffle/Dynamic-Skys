// Oscilloscope: green phosphor waveforms built from the audio spectrum, on an old lab screen.
// A trace runs around the horizon, a polar trace circles overhead, all on a glowing graticule.

// A waveform around the horizon: each spectrum band drives one harmonic.
// Returns the wave height and its slope (per radian of azimuth).
vec2 wave(float az, float t) {
    float idle = 1.0 - smoothstep(0.02, 0.15, uLevel);
    float y = 0.0;
    float dy = 0.0;
    for (int k = 1; k <= 8; k++) {
        float fk = float(k);
        float a = spectrum((fk - 0.5) / 9.0) * (1.2 / fk + 0.15) + idle * 0.25 / (fk * fk);
        float n = fk * 3.0;
        float ph = t * (0.6 + fk * 0.35) + fk * 1.7;
        y += a * sin(n * az + ph);
        dy += a * n * cos(n * az + ph);
    }
    return vec2(y, dy) * 0.08;
}

float trace(float dist) {
    return exp(-dist * dist * 400000.0) * 1.2 + exp(-dist * 140.0) * 0.18;
}

vec3 sky(vec3 d) {
    float az = atan(d.z, d.x);
    float el = elevation(d);
    float ce = max(cos(el), 0.05);
    vec3 green = vec3(0.35, 1.0, 0.45);

    // Dark glass with a faint green cast, a little brighter near the beam.
    vec3 col = vec3(0.004, 0.018, 0.008) + vec3(0.01, 0.035, 0.015) * exp(-abs(el - 0.25) * 3.0);

    // Graticule: major and minor divisions in azimuth and elevation.
    float ga = az / TAU * 48.0;
    float ge = el / (PI * 0.5) * 12.0;
    vec2 g = abs(fract(vec2(ga, ge) - 0.5) - 0.5) / max(fwidth(vec2(ga, ge)), vec2(1e-4));
    float grid = 1.0 - min(min(g.x, g.y), 1.0);
    float ticks = 1.0 - min(abs(fract(ga * 5.0 - 0.5) - 0.5) / max(fwidth(ga * 5.0), 1e-4), 1.0);
    ticks *= step(abs(el - 0.25), 0.012);
    col += green * (grid * 0.05 + ticks * 0.06) * step(0.0, el);

    // Main trace around the horizon, plus a fading afterimage (phosphor persistence).
    float t = uTime;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec2 w = wave(az, t - fi * 0.06);
        float dist = abs(el - 0.25 - w.x) / sqrt(1.0 + (w.y / ce) * (w.y / ce));
        col += green * trace(dist) * (i == 0 ? 1.0 + uBeat * 0.6 : 0.35 / max(fi, 1.0));
    }

    // Second channel: the raw spectrum as a line, higher up and dimmer.
    float x = abs(az) / PI;
    float s = spectrum(x);
    float sx = (spectrum(x + 0.01) - spectrum(x - 0.01)) / (0.02 * PI);
    float dist2 = abs(el - (0.62 + s * 0.18)) / sqrt(1.0 + (sx * 0.18 / ce) * (sx * 0.18 / ce));
    col += vec3(0.5, 1.0, 0.6) * trace(dist2) * 0.55;

    // Overhead: a polar trace, a ring pushed outward by the spectrum.
    float r = acos(clamp(d.y, -1.0, 1.0));
    float pr = 0.32 + spectrum(x) * 0.18 + uBass * 0.03 + 0.015 * sin(az * 6.0 + t * 2.0);
    col += green * trace(abs(r - pr)) * 0.8;
    col += green * exp(-r * 30.0) * (0.15 + uBeat * 0.4);

    // Below the horizon the screen bezel, with a dim reflection of the beam.
    if (el < 0.0) {
        col = vec3(0.012, 0.014, 0.013) + green * 0.04 * exp(el * 12.0);
        vec2 w = wave(az, t);
        float rd = abs(-el * 1.6 - 0.25 - w.x) / sqrt(1.0 + (w.y / ce) * (w.y / ce));
        col += green * trace(rd * 0.5) * 0.08 * exp(el * 5.0);
    }

    // Scanlines and a slight flicker.
    col *= 0.9 + 0.1 * sin(el * 900.0);
    col *= 0.97 + 0.03 * hash11(floor(uTime * 30.0));
    return col;
}
