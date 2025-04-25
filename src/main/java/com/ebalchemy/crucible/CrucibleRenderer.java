package com.ebalchemy.crucible;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

public class CrucibleRenderer implements BlockEntityRenderer<CrucibleTile> {
    private static final ResourceLocation WATER_STILL =
        new ResourceLocation("minecraft", "block/water_still");

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) {
        // no initialization needed
    }

    @Override
    public void render(CrucibleTile tile, float partialTicks,
                       PoseStack stack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        int level = tile.getBlockState().getValue(BlockCrucible.LEVEL);
        if (level <= 0) return;

        // Grab the still-water sprite from the blocks atlas
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(WATER_STILL);  // :contentReference[oaicite:0]{index=0}

        // Use a plain translucent quad against the atlas (no “beam” warp)
        VertexConsumer builder = buffer.getBuffer(
            RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));

        // Static UV bounds (full 16×16 tile)
        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();           // :contentReference[oaicite:1]{index=1}

        // Tint by potion color
        long col = tile.getPotionColor();
        float r = ((col >> 16) & 0xFF) / 255f;
        float g = ((col >>  8) & 0xFF) / 255f;
        float b = ( col        & 0xFF) / 255f;

        // Height inside crucible
        float y = 0.2f + 0.25f * level;

        Matrix4f mat = stack.last().pose();
        addVertex(builder, mat, 1f, y, 0f, maxU, minV, r, g, b, combinedLight);
        addVertex(builder, mat, 0f, y, 0f, minU, minV, r, g, b, combinedLight);
        addVertex(builder, mat, 0f, y, 1f, minU, maxV, r, g, b, combinedLight);
        addVertex(builder, mat, 1f, y, 1f, maxU, maxV, r, g, b, combinedLight);
    }

    private static void addVertex(VertexConsumer b, Matrix4f pos,
                                  float x, float y, float z,
                                  float u, float v,
                                  float r, float g, float bCol,
                                  int light) {
        b.vertex(pos, x, y, z)
         .color(r, g, bCol, 1f)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(0, 1, 0)
         .endVertex();
    }
}
