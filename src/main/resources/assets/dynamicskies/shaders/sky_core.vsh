#version 150

uniform mat4 uInverseViewProjection;
// The far plane in clip space: 1.0 normally, 0.0 / -1.0 with reversed depth (26.3+).
// The world sky is depth tested against the terrain, so it must sit exactly on it.
uniform float uSkyDepth;
out vec3 vDir;

void main() {
    vec2 clip = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2.0 - 1.0;
    // Modern projections carry view-bobbing translation; using the near-to-far
    // difference cancels it so walking doesn't swing the sky around.
    vec4 nearPoint = uInverseViewProjection * vec4(clip, -1.0, 1.0);
    vec4 farPoint = uInverseViewProjection * vec4(clip, 1.0, 1.0);
    vDir = farPoint.xyz / farPoint.w - nearPoint.xyz / nearPoint.w;
    gl_Position = vec4(clip, uSkyDepth, 1.0);
}
