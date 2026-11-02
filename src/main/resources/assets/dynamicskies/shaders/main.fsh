
uniform vec3 uFogColor;
uniform float uFog;
uniform vec3 uTint;
uniform float uBrightness;
uniform float uSaturation;
uniform float uHue;
uniform float uAudioHue;
uniform float uBeatFlash;
uniform float uAlpha;
uniform float uBuild;      // 0..1 build-up, already scaled by Build-Up Effect
uniform float uDropAge;    // seconds since the last drop
uniform float uDropFlash;  // 0 or 1
uniform float uDropWave;   // 0 or 1
uniform float uOverlay;    // 1 when drawn on top of another sky
uniform float uOverlayCutoff;

vec3 hueRotate(vec3 c, float h) {
    mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
    mat3 toRGB = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
    vec3 yiq = toYIQ * c;
    float a = h * TAU;
    float cs = cos(a);
    float sn = sin(a);
    yiq.yz = mat2(cs, sn, -sn, cs) * yiq.yz;
    return toRGB * yiq;
}

void main() {
    vec3 d = normalize(vDir);
    vec3 c = max(applyLayers(max(sky(d), vec3(0.0)), d), vec3(0.0));

    float h = uHue + uAudioHue * uMid * 0.5;
    if (h != 0.0) {
        c = max(hueRotate(c, h), vec3(0.0));
    }
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = max(mix(vec3(l), c, uSaturation), vec3(0.0));
    c *= uTint * uBrightness * (1.0 + uBeatFlash * uBeat * 0.6);

    // Build-up: brighter, a little washed out, with the horizon glowing.
    if (uBuild > 0.0) {
        float lb = dot(c, vec3(0.299, 0.587, 0.114));
        c = mix(c, vec3(lb), uBuild * 0.3) * (1.0 + uBuild * 0.6);
        c += vec3(1.0, 0.92, 0.85) * uBuild * uBuild * 0.45 * exp(-abs(d.y) * 5.0);
    }

    // Drop: a white flash and a ring of light sweeping up from the horizon.
    if (uDropAge < 4.0) {
        c = mix(c, vec3(1.0), uDropFlash * exp(-uDropAge * 4.0) * 0.75);
        float front = -0.1 + uDropAge * 1.3;
        float ring = exp(-abs(asin(clamp(d.y, -1.0, 1.0)) - front) * 14.0) * exp(-uDropAge * 0.9) * uDropWave;
        c += (c * 1.5 + vec3(0.5, 0.6, 0.8)) * ring;
    }

    float fog = clamp(uFog * pow(1.0 - clamp(d.y + 0.02, 0.0, 1.0), 14.0), 0.0, 1.0);

    if (uOverlay > 0.5) {
        // Drawn on top of another sky: near-black becomes see-through, and the
        // overlay fades out into the fog instead of painting the fog color.
        c = max(c - vec3(uOverlayCutoff), vec3(0.0)) / (1.0 - uOverlayCutoff);
        c *= 1.0 - fog;
        gl_FragColor = vec4(clamp(c, 0.0, 1.0) * uAlpha, 1.0);
        return;
    }

    c = mix(c, uFogColor, fog);

    // A little dithering to hide gradient banding.
    c += (hash12(gl_FragCoord.xy + fract(uTime) * 91.0) - 0.5) / 255.0;
    gl_FragColor = vec4(c, uAlpha);
}
