// Custom Image: your own equirectangular picture wrapped around the sky.

vec3 sky(vec3 d) {
    vec2 uv = equirect(d);
    uv.x += uImageRot;
    vec3 col = texture2D(uTex, uv).rgb;
    return col * (1.0 + uBass * 0.25 + uBeat * 0.15);
}
