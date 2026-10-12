package com.happyuky7.openvoxelmoding.loader;

import net.openvoxel.engine.VoxelEngine;
import net.openvoxel.engine.rendering.Renderer;
import net.openvoxel.engine.rendering.ShaderProgram;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Field;

/**
 * Replaces the chunk shader with one that adds distance fog blended with the sky, a softer
 * light curve, cooler night tint and slightly stronger ambient occlusion. Same attributes
 * and uniforms as the engine's shader so meshes and uniform caching keep working.
 */
final class Lighting {
    private Lighting() {}

    private static final String VERTEX = "#version 330 core\n"
        + "layout(location = 0) in vec3 position;\nlayout(location = 1) in vec2 texCoord;\nlayout(location = 2) in float ao;\n"
        + "layout(location = 3) in float skylight;\nlayout(location = 4) in float blockLight;\n"
        + "out vec2 vTexCoord;\nout float vAO;\nout float vSkylight;\nout float vBlockLight;\nout float vDist;\nout vec3 vWorld;\n"
        + "uniform mat4 projection;\nuniform mat4 view;\nuniform mat4 model;\n"
        + "void main() {\n  vec4 rel = model * vec4(position, 1.0);\n  vec4 eye = view * rel;\n  gl_Position = projection * eye;\n"
        + "  vTexCoord = texCoord;\n  vAO = ao;\n  vSkylight = skylight;\n  vBlockLight = blockLight;\n  vDist = length(eye.xyz);\n  vWorld = rel.xyz;\n}";

    private static final String FRAGMENT = "#version 330 core\n"
        + "in vec2 vTexCoord;\nin float vAO;\nin float vSkylight;\nin float vBlockLight;\nin float vDist;\nin vec3 vWorld;\n"
        + "out vec4 FragColor;\n"
        + "uniform sampler2D blockTexture;\nuniform float uFrameOffset;\nuniform float uFrameScale;\nuniform float uDayFactor;\n"
        + "uniform float uFogStart;\nuniform float uFogEnd;\nuniform vec3 uFogColor;\n"
        + "void main() {\n"
        + "  vec2 coord = vec2(vTexCoord.x, vTexCoord.y * uFrameScale + uFrameOffset);\n"
        + "  vec4 tex = texture(blockTexture, coord);\n  if (tex.a <= 0.1) discard;\n"
        + "  float ao = pow(clamp(vAO, 0.0, 1.0), 1.35);\n"
        + "  float sky = vSkylight * (0.18 + 0.82 * uDayFactor);\n"
        + "  float brightness = max(sky, vBlockLight) * ao;\n"
        + "  brightness = max(brightness, 0.08);\n"
        + "  brightness = pow(brightness, 0.9);\n"
        + "  vec3 col = tex.rgb * brightness;\n"
        + "  float night = (1.0 - uDayFactor) * vSkylight;\n"
        + "  col = mix(col, col * vec3(0.72, 0.80, 1.05), night * 0.6);\n"
        + "  float warm = vBlockLight * (1.0 - sky);\n"
        + "  col = mix(col, col * vec3(1.08, 0.98, 0.86), clamp(warm, 0.0, 1.0) * 0.5);\n"
        + "  float fog = smoothstep(uFogStart, uFogEnd, vDist);\n"
        + "  col = mix(col, uFogColor, fog);\n"
        + "  FragColor = vec4(col, tex.a);\n}";

    private static Field shaderField;
    private static int uFogStart = -1, uFogEnd = -1, uFogColor = -1, program;
    private static boolean reported;

    static void reportOnce(Throwable error) {
        if (reported) return;
        reported = true;
        System.err.println("[ModLoader] lighting disabled: " + error);
        error.printStackTrace();
    }

    static void install(Renderer renderer) throws Exception {
        if (shaderField == null) { shaderField = Renderer.class.getDeclaredField("shader"); shaderField.setAccessible(true); }
        ShaderProgram old = (ShaderProgram) shaderField.get(renderer);
        ShaderProgram mine = new ShaderProgram(VERTEX, FRAGMENT);
        shaderField.set(renderer, mine);
        if (old != null) old.delete();
        program = mine.getProgramId();
        uFogStart = GL20.glGetUniformLocation(program, "uFogStart");
        uFogEnd = GL20.glGetUniformLocation(program, "uFogEnd");
        uFogColor = GL20.glGetUniformLocation(program, "uFogColor");
        System.out.println("[ModLoader] chunk shader replaced (fog + tone)");
    }

    /** Per-frame uniforms: fog distance from the render distance, fog colour from the day factor. */
    static void beforeRender(Renderer renderer) {
        if (program == 0 || uFogEnd < 0) return;
        VoxelEngine e = VoxelEngine.getEngine();
        if (e == null || e.getCamera() == null) return;
        float blocks = e.getCamera().getRenderDistance() * 16f;
        float day = renderer.getDayFactor();
        float r = 0.03f + (0.62f - 0.03f) * day, g = 0.04f + (0.76f - 0.04f) * day, b = 0.08f + (0.95f - 0.08f) * day;
        GL20.glUseProgram(program);
        if (Settings.fog) { GL20.glUniform1f(uFogStart, blocks * 0.55f); GL20.glUniform1f(uFogEnd, blocks * 0.98f); }
        else { GL20.glUniform1f(uFogStart, 1e6f); GL20.glUniform1f(uFogEnd, 2e6f); }
        GL20.glUniform3f(uFogColor, r, g, b);
        GL20.glUseProgram(0);
    }
}
