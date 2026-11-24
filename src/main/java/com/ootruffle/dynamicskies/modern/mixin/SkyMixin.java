package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.modern.ModernClient;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the dynamic sky over the finished vanilla sky. From 1.21.4 the sky is a frame-graph
 * pass, so the hook is the end of that pass's body (a lambda). From 1.21.8 the matrices and
 * fog color are no longer readable inside it, so they're captured when the level render starts.
 */
@Mixin(LevelRenderer.class)
abstract class SkyMixin {

    @Unique private static final org.joml.Matrix4f dynamicskies$view = new org.joml.Matrix4f();
    @Unique private static final org.joml.Matrix4f dynamicskies$projection = new org.joml.Matrix4f();
    @Unique private static final org.joml.Vector4f dynamicskies$fog = new org.joml.Vector4f();

    @Unique
    private static void dynamicskies$draw(float sunAngle, float r, float g, float b, float rain, boolean end) {
        var fog = dynamicskies$fog;
        ModernSkyRenderer.INSTANCE.draw(dynamicskies$view, dynamicskies$projection, sunAngle, r, g, b,
                fog.x, fog.y, fog.z, rain, end);
    }

    //? if >= 26.3 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, boolean outline,
                                      net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                      com.mojang.renderpearl.api.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean a, boolean b, CallbackInfo ci) {
        dynamicskies$view.set(camera.viewRotationMatrix);
        dynamicskies$projection.set(camera.projectionMatrix);
        dynamicskies$fog.set(fogColor);
    }

    @Inject(method = "lambda$addSkyPass$0", at = @At("RETURN"))
    private void dynamicskies$sky(com.mojang.renderpearl.api.buffers.GpuBufferSlice fog,
                                  net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        var sky = state.skyColor;
        dynamicskies$draw(state.sunAngle, sky.x(), sky.y(), sky.z(), 1f - state.rainBrightness,
                state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.END);
    }
    *///?} elif >= 26.2 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                      net.minecraft.client.DeltaTracker delta, boolean outline,
                                      net.minecraft.client.renderer.state.level.CameraRenderState camera, org.joml.Matrix4fc modelView,
                                      com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean foggy, CallbackInfo ci) {
        dynamicskies$view.set(camera.viewRotationMatrix);
        dynamicskies$projection.set(camera.projectionMatrix);
        dynamicskies$fog.set(fogColor);
    }

    @Inject(method = "lambda$addSkyPass$0", at = @At("RETURN"))
    private void dynamicskies$sky(com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                  net.minecraft.client.renderer.state.level.SkyRenderState state, CallbackInfo ci) {
        int sky = state.skyColor;
        dynamicskies$draw(state.sunAngle, net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), 1f - state.rainBrightness,
                state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.END);
    }
    *///?} elif >= 26.1 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                      net.minecraft.client.DeltaTracker delta, boolean outline,
                                      net.minecraft.client.renderer.state.level.CameraRenderState camera, org.joml.Matrix4fc modelView,
                                      com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean foggy, net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks, CallbackInfo ci) {
        dynamicskies$view.set(camera.viewRotationMatrix);
        dynamicskies$projection.set(camera.projectionMatrix);
        dynamicskies$fog.set(fogColor);
    }

    @Inject(method = "lambda$addSkyPass$0", at = @At("RETURN"))
    private static void dynamicskies$sky(com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                         net.minecraft.client.renderer.state.level.SkyRenderState state,
                                         net.minecraft.client.renderer.SkyRenderer renderer, CallbackInfo ci) {
        int sky = state.skyColor;
        dynamicskies$draw(state.sunAngle, net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), 1f - state.rainBrightness,
                state.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.END);
    }
    *///?} elif >= 1.21.11 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                      net.minecraft.client.DeltaTracker delta, boolean outline, net.minecraft.client.Camera camera,
                                      org.joml.Matrix4f view, org.joml.Matrix4f projection, org.joml.Matrix4f cullingProjection,
                                      com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean foggy, CallbackInfo ci) {
        dynamicskies$view.set(view);
        dynamicskies$projection.set(projection);
        dynamicskies$fog.set(fogColor);
    }

    // 1.21.11 made the sky pass body static and passes the sky renderer in.
    @Inject(method = "method_62215", at = @At("RETURN"))
    private static void dynamicskies$sky(com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                         net.minecraft.client.renderer.state.SkyRenderState state,
                                         net.minecraft.client.renderer.SkyRenderer renderer, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        int sky = state.skyColor;
        dynamicskies$draw(state.sunAngle, net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), 1f - state.rainBrightness,
                world != null && world.dimension() == net.minecraft.world.level.Level.END);
    }
    *///?} elif >= 1.21.10 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                      net.minecraft.client.DeltaTracker delta, boolean outline, net.minecraft.client.Camera camera,
                                      org.joml.Matrix4f view, org.joml.Matrix4f projection, org.joml.Matrix4f cullingProjection,
                                      com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean foggy, CallbackInfo ci) {
        dynamicskies$view.set(view);
        dynamicskies$projection.set(projection);
        dynamicskies$fog.set(fogColor);
    }

    @Inject(method = "method_62215", at = @At("RETURN"))
    private void dynamicskies$sky(com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                  net.minecraft.client.renderer.state.SkyRenderState state, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        int sky = state.skyColor;
        dynamicskies$draw(state.sunAngle, net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), 1f - state.rainBrightness,
                world != null && world.dimension() == net.minecraft.world.level.Level.END);
    }
    *///?} elif >= 1.21.8 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$capture(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator,
                                      net.minecraft.client.DeltaTracker delta, boolean outline, net.minecraft.client.Camera camera,
                                      org.joml.Matrix4f view, org.joml.Matrix4f projection,
                                      com.mojang.blaze3d.buffers.GpuBufferSlice fogBuffer, org.joml.Vector4f fogColor,
                                      boolean foggy, CallbackInfo ci) {
        dynamicskies$view.set(view);
        dynamicskies$projection.set(projection);
        dynamicskies$fog.set(fogColor);
    }

    @Inject(method = "method_62215", at = @At("RETURN"))
    private void dynamicskies$sky(com.mojang.blaze3d.buffers.GpuBufferSlice fog,
                                  net.minecraft.client.renderer.DimensionSpecialEffects.SkyType type, float tick,
                                  net.minecraft.client.renderer.DimensionSpecialEffects effects, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) return;
        int sky = world.getSkyColor(client.gameRenderer.getMainCamera().getPosition(), tick);
        dynamicskies$draw(world.getSunAngle(tick), net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), world.getRainLevel(tick),
                type == net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.END);
    }
    *///?} elif >= 1.21.4 {
    // This is the frame-graph sky pass body. Compose over the completed vanilla pass.
    @Inject(method = "method_62215", at = @At("RETURN"))
    private void dynamicskies$sky(net.minecraft.client.renderer.FogParameters fog,
                                  net.minecraft.client.renderer.DimensionSpecialEffects.SkyType type, float tick,
                                  net.minecraft.client.renderer.DimensionSpecialEffects effects, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) return;
        int sky = world.getSkyColor(client.gameRenderer.getMainCamera().getPosition(), tick);
        dynamicskies$view.set(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        dynamicskies$projection.set(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        dynamicskies$fog.set(fog.red(), fog.green(), fog.blue(), 1f);
        dynamicskies$draw(world.getSunAngle(tick), net.minecraft.util.ARGB.redFloat(sky), net.minecraft.util.ARGB.greenFloat(sky),
                net.minecraft.util.ARGB.blueFloat(sky), world.getRainLevel(tick),
                type == net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.END);
    }
    //?} else {
    /*@Inject(method = "renderSky", at = @At("RETURN"))
    private void dynamicskies$sky(org.joml.Matrix4f view, org.joml.Matrix4f projection, float tick,
                                  net.minecraft.client.Camera camera, boolean foggy, Runnable setupFog, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || world == null || foggy
                || (world.dimension() != net.minecraft.world.level.Level.OVERWORLD && world.dimension() != net.minecraft.world.level.Level.END)
                || camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        var color = world.getSkyColor(camera.getPosition(), tick);
        float[] fog = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
        dynamicskies$view.set(view);
        dynamicskies$projection.set(projection);
        dynamicskies$fog.set(fog[0], fog[1], fog[2], 1f);
        dynamicskies$draw(world.getSunAngle(tick), (float) color.x, (float) color.y, (float) color.z,
                world.getRainLevel(tick), world.dimension() == net.minecraft.world.level.Level.END);
    }
    *///?}

    // Right after the solid terrain (before entities and anything see-through): the queued sky is
    // drawn here, depth tested, so it only runs where no terrain covers it. Optional (require = 0):
    // if another mod replaces this code, the sky notices and draws in the sky pass instead.
    //? if >= 26.3 {
    /*@Inject(method = "executeSolid", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V"))
    *///?} elif >= 26.1 {
    /*@Inject(method = "lambda$addMainPass$0", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    *///?} elif >= 1.21.11 {
    /*@Inject(method = "method_62214", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    *///?} elif >= 1.21.8 {
    /*@Inject(method = "method_62214", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 0,
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;)V"))
    *///?} elif >= 1.21.4 {
    // Solid, cutout-mipped, cutout: after the third layer.
    @Inject(method = "method_62214", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 2,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    //?} else {
    /*@Inject(method = "renderLevel", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER, ordinal = 2,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    *///?}
    private void dynamicskies$afterSolidTerrain(CallbackInfo ci) {
        ModernSkyRenderer.INSTANCE.afterOpaqueTerrain();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        ModernSkyRenderer.INSTANCE.close();
    }
}
