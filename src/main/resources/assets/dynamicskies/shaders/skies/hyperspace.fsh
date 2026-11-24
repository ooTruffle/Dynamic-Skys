// Hyperspace: star streaks pouring out of a point on the horizon as you fly into it.
// The bass speeds the clock up (uTime) and stretches the streaks; a beat gives a short surge.

vec3 sky(vec3 d) {
    vec3 F = normalize(vec3(0.0, 0.06, -1.0));
    vec3 R = normalize(cross(F, vec3(0.0, 1.0, 0.0)));
    vec3 U = cross(R, F);

    float ct = clamp(dot(d, F), -1.0, 1.0);
    float th = acos(ct);
    float st = sin(th);
    float phi = atan(dot(d, U), dot(d, R));
    // Mercator-style distance from the front point, so streaks keep their look all the way back.
    float u = log(tan(th * 0.5) + 1e-4);

    float surge = uBeat * 0.8 + uBass * 0.35;
    float t = uTime * 1.6;

    vec3 col = vec3(0.004, 0.006, 0.018);
    // Tunnel haze: blue around the front, fading to a dim red behind you.
    col += vec3(0.25, 0.4, 0.9) * exp(-th * 2.2) * (0.25 + surge * 0.3);
    col += vec3(0.2, 0.03, 0.06) * exp(-(PI - th) * 2.5) * 0.4;
    float swirl = fbm(vec3(cos(phi) * 2.0, sin(phi) * 2.0, u * 0.6 - t * 0.5));
    col += vec3(0.08, 0.12, 0.3) * smoothstep(0.45, 0.85, swirl) * exp(-th * 0.9);

    vec3 streaks = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float lanes = 90.0 + fi * 70.0;
        float lp = phi / TAU * lanes + fi * 0.37;
        float lane = floor(lp);
        float h = hash12(vec2(mod(lane, lanes), fi * 13.0));
        float h2 = hash12(vec2(mod(lane, lanes), fi * 7.0 + 3.0));
        float across = (fract(lp) - 0.5 + (h2 - 0.5) * 0.5) * (TAU / lanes) * st;

        float period = 2.2 + h * 1.5;
        float v = (u - t * (0.8 + h * 0.6 + fi * 0.25)) / period + h * 9.0;
        float cell = floor(v);
        float f = fract(v);
        float on = step(0.35, hash12(vec2(mod(lane, lanes) + cell * 0.13, fi)));
        float len = 0.12 + surge * 0.25 + h * 0.08;

        float w = 0.0018 + 0.0008 * fi;
        float core = exp(-across * across / (w * w));
        float body = smoothstep(0.0, len, f) * step(f, len);
        body *= body;
        vec3 tint = mix(vec3(0.7, 0.85, 1.0), vec3(1.0, 0.85, 0.7), h2);
        streaks += tint * core * body * on * (0.9 - fi * 0.2);
    }
    // Streaks blur into a glow near the front point and thin out behind.
    streaks *= smoothstep(0.03, 0.25, th) * (1.0 - smoothstep(2.4, 3.05, th));
    col += streaks * (1.0 + surge);

    // The bright core you are flying into.
    col += vec3(0.8, 0.9, 1.0) * exp(-th * 30.0) * (1.0 + uBeat * 2.0);
    col += vec3(0.4, 0.6, 1.0) * exp(-th * 9.0) * 0.5 * (1.0 + uLevel);
    return col;
}
