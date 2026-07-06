package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CleanView;
import com.lume.client.module.modules.render.Aspect;
import com.lume.client.module.modules.render.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clean View + FreeLook camera rendering. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Unique private float lumeRealYaw, lumeRealPitch;

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CleanView.noHurtCam()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CleanView.noBob()) ci.cancel();
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$aspect(float fov, CallbackInfoReturnable<Matrix4f> cir) {
        Module m = LumeClient.MODULES.getByName("Aspect");
        if (!(m instanceof Aspect a) || !a.isEnabled()) return;
        float target = a.target();
        if (target <= 0) return;
        var win = MinecraftClient.getInstance().getWindow();
        if (win.getFramebufferHeight() == 0) return;
        float real = (float) win.getFramebufferWidth() / win.getFramebufferHeight();
        Matrix4f mat = cir.getReturnValue();
        mat.scale(real / target, 1f, 1f);
        cir.setReturnValue(mat);
    }

    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void lume$freeLookBefore(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!FreeLook.active) return;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        lumeRealYaw   = mc.player.getYaw();
        lumeRealPitch = mc.player.getPitch();
        mc.player.setYaw(FreeLook.freeYaw);
        mc.player.setPitch(FreeLook.freePitch);
    }

    @Inject(method = "renderWorld", at = @At("RETURN"), require = 0)
    private void lume$freeLookAfter(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!FreeLook.active) return;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.setYaw(lumeRealYaw);
        mc.player.setPitch(lumeRealPitch);
    }
}
