#version 120

// Shared by every sky. Each sky file defines: vec3 sky(vec3 d)
// where d is the normalized world-space view direction (y = up).

uniform float uTime;      // seconds, scaled by animation speed (and bass)
uniform float uBass;      // 0..~2 audio levels, already scaled by Reactivity
uniform float uMid;
uniform float uHigh;
uniform float uLevel;
uniform float uBeat;      // 1 on a beat, decays to 0
uniform vec3 uSunDir;     // real Minecraft sun direction
uniform vec3 uSkyColor;   // vanilla sky color this frame
uniform sampler2D uTex;   // 64x1 audio spectrum, or the custom image
uniform float uImageRot;

varying vec3 vDir;

#define PI 3.14159265
#define TAU 6.28318531

float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash12(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * 0.1031); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.x + p3.y) * p3.z); }
float hash13(vec3 p3) { p3 = fract(p3 * 0.1031); p3 += dot(p3, p3.zyx + 31.32); return fract((p3.x + p3.y) * p3.z); }
vec2 hash22(vec2 p) { vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yzx + 33.33); return fract((p3.xx + p3.yz) * p3.zy); }
vec3 hash33(vec3 p3) { p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973)); p3 += dot(p3, p3.yxz + 33.33); return fract((p3.xxy + p3.yxx) * p3.zyx); }

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float e = hash13(i + vec3(1.0, 1.0, 0.0));
    float g = hash13(i + vec3(0.0, 0.0, 1.0));
    float h = hash13(i + vec3(1.0, 0.0, 1.0));
    float k = hash13(i + vec3(0.0, 1.0, 1.0));
    float l = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, u.x), mix(c, e, u.x), u.y),
               mix(mix(g, h, u.x), mix(k, l, u.x), u.y), u.z);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 r = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = r * p * 2.02 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v / 0.875;
}

// x = distance to nearest cell point, y = distance to second nearest.
vec2 voronoi(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int z = -1; z <= 1; z++)
    for (int y = -1; y <= 1; y++)
    for (int x = -1; x <= 1; x++) {
        vec3 g = vec3(float(x), float(y), float(z));
        vec3 r = g + hash33(i + g) - f;
        float dd = dot(r, r);
        if (dd < f1) { f2 = f1; f1 = dd; }
        else if (dd < f2) { f2 = dd; }
    }
    return sqrt(vec2(f1, f2));
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec3 pal(float t, vec3 a, vec3 b, vec3 c, vec3 d) {
    return a + b * cos(TAU * (c * t + d));
}

float spectrum(float x) {
    return texture2D(uTex, vec2(clamp(x, 0.0, 1.0), 0.5)).r;
}

// Twinkling point stars. Higher threshold = fewer stars.
float stars(vec3 d, float scale, float threshold) {
    vec3 p = d * scale;
    vec3 id = floor(p);
    vec3 f = fract(p) - 0.5;
    float h = hash13(id);
    if (h < threshold) return 0.0;
    vec3 o = (hash33(id + 3.7) - 0.5) * 0.6;
    float dist = length(f - o);
    float s = max(0.0, 1.0 - dist * 9.0);
    float tw = 0.65 + 0.35 * sin(uTime * (1.0 + h * 4.0) + h * 60.0);
    tw *= 1.0 + uHigh * 0.8;
    return s * s * s * tw * (0.4 + 0.6 * (h - threshold) / (1.0 - threshold));
}

vec2 equirect(vec3 d) {
    return vec2(atan(d.z, d.x) / TAU + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);
}

// Seamless azimuth input for noise: a point on a circle of radius r.
vec2 ring(vec3 d, float r) {
    return normalize(d.xz + vec2(1e-5)) * r;
}

float elevation(vec3 d) {
    return asin(clamp(d.y, -1.0, 1.0));
}
