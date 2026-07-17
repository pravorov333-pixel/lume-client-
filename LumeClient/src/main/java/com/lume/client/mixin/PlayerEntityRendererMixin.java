package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.visual.CustomNametags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Nametags — recolours/restyles other players' nametags. Cancels vanilla's own draw and
 * paints ours instead, reusing the SAME matrix context vanilla already translated to the label's
 * world position (no manual camera math needed, unlike {@code SelfName}, since this hook fires
 * from inside vanilla's own label placement).
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;"
            + "Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customNametag(PlayerEntityRenderState state, Text text, MatrixStack matrices,
                                     VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Custom Nametags");
        if (!(m instanceof CustomNametags cn) || !cn.isEnabled()) return;
        ci.cancel();

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        String name = state.name != null ? state.name : text.getString();
        int rgb = cn.color.accent ? Theme.accentRgb() : cn.color.rgb();
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);

        matrices.push();
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);
        float w = -tr.getWidth(name) / 2f;
        Matrix4f mat = matrices.peek().getPositionMatrix();
        tr.draw(name, w, 0, argb, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, light);
        matrices.pop();
    }
}
