// Void: near-black, with only a faint glow at the horizon that breathes with the bass.

vec3 sky(vec3 d) {
    float h = exp(-abs(d.y) * 8.0);
    return vec3(0.003) + vec3(0.04, 0.03, 0.07) * h * (0.3 + uBass * 1.2 + uBeat * 0.5);
}
