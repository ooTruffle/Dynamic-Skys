package com.ootruffle.dynamicskies.sky;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * One compiled sky program: common.glsl + skies/&lt;id&gt;.fsh + main.fsh,
 * with the shared sky.vsh vertex shader.
 */
public final class SkyShader {

    private static final String ROOT = "/assets/dynamicskies/shaders/";

    private final int program;
    private final Map<String, Integer> uniforms = new HashMap<>();

    private SkyShader(int program) {
        this.program = program;
    }

    /** Compiles a built-in sky, or throws with the GLSL error log. */
    public static SkyShader compile(SkyType type) {
        return compile(type.id, read("skies/" + type.id + ".fsh"));
    }

    /**
     * Compiles a sky from its source (a file defining vec3 sky(vec3 d)).
     * Error line numbers in "1:N" form point at line N of that source.
     */
    public static SkyShader compile(String name, String skySource) {
        return compile(name, skySource, false);
    }

    public static SkyShader compileCore(SkyType type) {
        return compile(type.id, read("skies/" + type.id + ".fsh"), true);
    }

    public static SkyShader compileCore(String name, String source) {
        return compile(name, source, true);
    }

    private static SkyShader compile(String name, String skySource, boolean coreProfile) {
        String vertexPath = coreProfile ? "sky_core.vsh" : "sky.vsh";
        String vertex = read(vertexPath);
        String fragment = (coreProfile ? "#version 150\n" : "#version 120\n") + read("common.glsl")
                + "\n#line 1 1\n" + skySource
                + "\n#line 1 2\n" + read("layers.glsl")
                + "\n#line 1 3\n" + read("main.fsh");

        int vs = compileStage(GL20.GL_VERTEX_SHADER, vertex, vertexPath);
        int fs;
        try {
            fs = compileStage(GL20.GL_FRAGMENT_SHADER, fragment, name);
        } catch (RuntimeException e) {
            GL20.glDeleteShader(vs);
            throw e;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program, 8192);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("Linking sky '" + name + "' failed:\n" + log);
        }
        return new SkyShader(program);
    }

    private static int compileStage(int stage, String source, String name) {
        int shader = GL20.glCreateShader(stage);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 8192);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Compiling sky '" + name + "' failed:\n" + log);
        }
        return shader;
    }

    static String read(String path) {
        try (InputStream in = SkyShader.class.getResourceAsStream(ROOT + path)) {
            if (in == null) {
                throw new IllegalStateException("Missing shader file " + ROOT + path);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read shader " + path, e);
        }
    }

    public void bind() {
        GL20.glUseProgram(program);
    }

    public void delete() {
        GL20.glDeleteProgram(program);
    }

    /** The uniforms set every frame, looked up once instead of by name each frame. */
    public enum U {
        INVERSE_VIEW_PROJECTION("uInverseViewProjection"), TEX("uTex"), TIME("uTime"),
        BASS("uBass"), MID("uMid"), HIGH("uHigh"), LEVEL("uLevel"), BEAT("uBeat"),
        SUN_DIR("uSunDir"), SKY_COLOR("uSkyColor"), FOG_COLOR("uFogColor"), FOG("uFog"),
        TINT("uTint"), BRIGHTNESS("uBrightness"), SATURATION("uSaturation"), HUE("uHue"),
        AUDIO_HUE("uAudioHue"), BEAT_FLASH("uBeatFlash"), IMAGE_ROT("uImageRot"), ALPHA("uAlpha"),
        OVERLAY("uOverlay"), OVERLAY_CUTOFF("uOverlayCutoff"), BUILD("uBuild"), DROP_AGE("uDropAge"),
        SKY_DEPTH("uSkyDepth"), DROP_FLASH("uDropFlash"), DROP_WAVE("uDropWave"), LAYER_STARS("uLayerStars"),
        LAYER_AURORA("uLayerAurora"), LAYER_WISPS("uLayerWisps"),
        // Arrays: the location of element 0; the whole array is uploaded in one call.
        METEORS("uMeteors"), METEOR_DIRS("uMeteorDirs"), SHELLS("uShells"), SHELL_INFO("uShellInfo");

        final String glsl;

        U(String glsl) {
            this.glsl = glsl;
        }
    }

    private int[] cached;

    public int loc(U u) {
        if (cached == null) {
            U[] all = U.values();
            cached = new int[all.length];
            for (U v : all) cached[v.ordinal()] = GL20.glGetUniformLocation(program, v.glsl);
        }
        return cached[u.ordinal()];
    }

    public void set(U u, float v) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform1f(l, v);
    }

    public void set(U u, float x, float y, float z) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform3f(l, x, y, z);
    }

    public void setInt(U u, int v) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform1i(l, v);
    }

    public int loc(String name) {
        Integer l = uniforms.get(name);
        if (l == null) {
            l = GL20.glGetUniformLocation(program, name);
            uniforms.put(name, l);
        }
        return l;
    }

    public void set(String name, float v) {
        int l = loc(name);
        if (l >= 0) GL20.glUniform1f(l, v);
    }

    public void set(String name, float x, float y, float z) {
        int l = loc(name);
        if (l >= 0) GL20.glUniform3f(l, x, y, z);
    }

    public void set(String name, float x, float y, float z, float w) {
        int l = loc(name);
        if (l >= 0) GL20.glUniform4f(l, x, y, z, w);
    }

    public void setInt(String name, int v) {
        int l = loc(name);
        if (l >= 0) GL20.glUniform1i(l, v);
    }
}
