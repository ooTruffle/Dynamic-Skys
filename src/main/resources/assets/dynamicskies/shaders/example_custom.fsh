// Dynamic Skies custom shader example.
//
// Copy this file, rename it, and type its name into
// /dynamicskies > Custom Shader > Shader File, then pick "Custom Shader" as the sky.
// With Live Reload on, saving the file updates the sky right away. Errors show
// in chat; line numbers like "1:12" mean line 12 of this file.
//
// Write one function:   vec3 sky(vec3 d)
//   d is the direction being looked at (normalized, y = up). Return a color.
//
// Things you can use (GLSL 1.20):
//   uTime                       seconds, scaled by Animation Speed (and bass / build-ups)
//   uBass uMid uHigh uLevel     audio levels, roughly 0..1 (0 when audio is off)
//   uBeat                       1 on a beat, fades back to 0
//   spectrum(x)                 audio spectrum at x = 0 (low) .. 1 (high)
//   uSunDir                     Minecraft's sun direction
//   uSkyColor                   the vanilla sky color right now
//   noise(vec2/vec3)  fbm(vec2/vec3)  fbm3(vec3)  voronoi(vec3)
//   hash12 hash13 hash22 hash33  hsv2rgb(vec3)  pal(t, a, b, c, d)
//   stars(d, scale, threshold)  equirect(d)  ring(d, r)  elevation(d)
//   PI  TAU
//
// Layers, drops, hue, saturation, tint and fog are applied after this, like
// for the built-in skies.

vec3 sky(vec3 d) {
    // A soft gradient from the horizon up.
    float y = max(d.y, 0.0);
    vec3 col = mix(vec3(0.9, 0.4, 0.6), vec3(0.05, 0.05, 0.2), sqrt(y));

    // Slowly drifting color blobs.
    float n = fbm(d * 2.0 + vec3(0.0, uTime * 0.05, 0.0));
    col += hsv2rgb(vec3(n + uTime * 0.02, 0.6, 0.5)) * smoothstep(0.45, 0.8, n);

    // A ring overhead that grows with the bass.
    float r = acos(clamp(d.y, -1.0, 1.0));
    col += vec3(1.0, 0.8, 1.0) * exp(-abs(r - 0.3 - uBass * 0.15) * 40.0) * (0.3 + uBeat);

    col += stars(d, 200.0, 0.93);
    return col;
}
