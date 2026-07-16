package com.eyeofthestorm.client;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;
import java.util.Optional;

/**
 * Fixed-scale storm radar toggled via keybind or Storm Map item.
 * The blue circle stays the same on-screen size; the player icon moves within it.
 */
@EventBusSubscriber(modid = EyeOfTheStormMod.MOD_ID, value = Dist.CLIENT)
public final class StormMapHudOverlay {
    private static final int WIDGET_SIZE = 96;
    private static final int MARGIN = 10;
    private static final int STORM_CIRCLE_RADIUS = 38;
    private static final int PLAYER_ICON_SIZE = 8;
    private static final int OFF_MAP_ICON_SIZE = 8;
    private static final int PATH_LINE_WIDTH = 1;
    private static final int STORM_FILL_COLOR = 0x18FF4444;
    private static final int STORM_OUTLINE_COLOR = 0xB0FF5555;
    private static final int PATH_COLOR = 0xFFFF6666;
    private static final int VELOCITY_ARROW_COLOR = 0xFFFF4040;
    private static final float VELOCITY_ARROW_MAX_LENGTH = STORM_CIRCLE_RADIUS - 3.0F;
    private static final float VELOCITY_ARROW_MIN_LENGTH = 8.0F;
    private static final int CENTER_DOT_COLOR = 0xCCFFFFFF;

    private static final MapDecoration PLAYER_DECORATION =
            new MapDecoration(MapDecorationTypes.PLAYER, (byte) 0, (byte) 0, (byte) 0, Optional.empty());
    private static final MapDecoration PLAYER_OFF_MAP_DECORATION =
            new MapDecoration(MapDecorationTypes.PLAYER_OFF_MAP, (byte) 0, (byte) 0, (byte) 0, Optional.empty());

    private StormMapHudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!StormRadarState.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (!mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int left = graphics.guiWidth() - WIDGET_SIZE - MARGIN;
        int top = MARGIN;
        int centerX = left + WIDGET_SIZE / 2;
        int centerY = top + WIDGET_SIZE / 2;

        if (!ClientStormState.shouldRender() || ClientStormState.radius <= 0.0) {
            graphics.drawString(
                    mc.font,
                    ClientStormState.initialized ? "Paused" : "No storm",
                    left + 6,
                    centerY - 4,
                    0x99AAAAAA,
                    false
            );
            return;
        }

        graphics.enableScissor(left, top, left + WIDGET_SIZE, top + WIDGET_SIZE);
        try {
            fillCircle(graphics, centerX, centerY, STORM_CIRCLE_RADIUS, STORM_FILL_COLOR);
            if (StormRadarConfig.centerIndicator() == StormRadarConfig.CenterIndicator.TRAIL) {
                drawStormPathTrail(graphics, centerX, centerY);
            } else {
                drawStormVelocityArrow(graphics, centerX, centerY);
            }
            drawCircleOutline(graphics, centerX, centerY, STORM_CIRCLE_RADIUS, STORM_OUTLINE_COLOR);
            graphics.fill(centerX, centerY, centerX + 1, centerY + 1, CENTER_DOT_COLOR);

            double relX = player.getX() - ClientStormState.centerX;
            double relZ = player.getZ() - ClientStormState.centerZ;
            float mapX = (float) (relX / ClientStormState.radius * STORM_CIRCLE_RADIUS);
            float mapY = (float) (relZ / ClientStormState.radius * STORM_CIRCLE_RADIUS);
            float iconRadius = (float) Math.hypot(mapX, mapY);
            float offMapMarkerRadius = STORM_CIRCLE_RADIUS + OFF_MAP_ICON_SIZE / 2.0F + 1.0F;

            if (iconRadius > offMapMarkerRadius) {
                float markerX = centerX + mapX / iconRadius * offMapMarkerRadius;
                float markerY = centerY + mapY / iconRadius * offMapMarkerRadius;
                drawOffMapIcon(graphics, mc, markerX, markerY);
            } else {
                drawPlayerIcon(graphics, mc, centerX + mapX, centerY + mapY, player.getYRot());
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawStormVelocityArrow(GuiGraphics graphics, int centerX, int centerY) {
        float blocksPerSecond = (float) (ClientStormState.speed * 20.0);
        if (blocksPerSecond <= 0.0F) {
            return;
        }

        float speedFraction = Mth.clamp(blocksPerSecond / 10.0F, 0.0F, 1.0F);
        float length = Math.max(
                VELOCITY_ARROW_MIN_LENGTH,
                Mth.sqrt(speedFraction) * VELOCITY_ARROW_MAX_LENGTH
        );

        float directionX = -(float) Math.sin(ClientStormState.yawRad);
        float directionY = (float) Math.cos(ClientStormState.yawRad);
        float rotationDeg = (float) Math.toDegrees(Math.atan2(directionX, -directionY));
        drawRotatedPixelArrow(graphics, centerX + 0.5F, centerY + 0.5F, Math.round(length), rotationDeg);
    }

    /**
     * Draws an upward-pointing 1-pixel arrow, then rotates it around its root.
     * Keeping the glyph in local coordinates avoids rasterizing diagonal line segments.
     */
    private static void drawRotatedPixelArrow(
            GuiGraphics graphics,
            float rootX,
            float rootY,
            int length,
            float rotationDeg
    ) {
        int arrowLength = Math.max(1, length);
        int headHeight = Math.min(3, arrowLength);
        int stemTop = -arrowLength + headHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(rootX, rootY, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDeg));
        graphics.pose().translate(-0.5F, -0.5F, 0.0F);

        graphics.fill(0, stemTop, 1, 1, VELOCITY_ARROW_COLOR);
        if (headHeight >= 3) {
            graphics.fill(-2, -arrowLength + 2, 3, -arrowLength + 3, VELOCITY_ARROW_COLOR);
        }
        if (headHeight >= 2) {
            graphics.fill(-1, -arrowLength + 1, 2, -arrowLength + 2, VELOCITY_ARROW_COLOR);
        }
        graphics.fill(0, -arrowLength, 1, -arrowLength + 1, VELOCITY_ARROW_COLOR);

        graphics.pose().popPose();
    }

    private static void drawStormPathTrail(GuiGraphics graphics, int centerX, int centerY) {
        List<StormPathHistory.Sample> path = StormPathHistory.snapshot();
        if (path.isEmpty()) {
            return;
        }

        long now = Util.getMillis();
        long windowMs = StormPathHistory.windowMs();

        float prevX = 0.0F;
        float prevY = 0.0F;
        float prevAlpha = 0.0F;
        boolean hasPrev = false;

        for (StormPathHistory.Sample sample : path) {
            float relX = (float) ((sample.x() - ClientStormState.centerX) / ClientStormState.radius * STORM_CIRCLE_RADIUS);
            float relY = (float) ((sample.z() - ClientStormState.centerZ) / ClientStormState.radius * STORM_CIRCLE_RADIUS);
            float x = centerX + relX;
            float y = centerY + relY;
            float age = (now - sample.timeMs()) / (float) windowMs;
            float alpha = Mth.clamp(1.0F - age, 0.0F, 1.0F) * 0.85F;

            if (hasPrev) {
                float lineAlpha = alpha * 0.5F + prevAlpha * 0.5F;
                drawFatLine(graphics, prevX, prevY, x, y, withAlpha(PATH_COLOR, lineAlpha));
            }

            prevX = x;
            prevY = y;
            prevAlpha = alpha;
            hasPrev = true;
        }

        if (hasPrev) {
            drawFatLine(graphics, prevX, prevY, centerX, centerY, withAlpha(PATH_COLOR, 0.85F));
        }
    }

    private static void drawFatLine(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        int half = PATH_LINE_WIDTH / 2;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(Mth.lerp(t, x0, x1));
            int y = Math.round(Mth.lerp(t, y0, y1));
            graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static float mapDecorationRotation(float yawDeg) {
        float bias = yawDeg < 0.0F ? -8.0F : 8.0F;
        return yawDeg + bias + 180.0F;
    }

    private static void drawMapDecoration(
            GuiGraphics graphics,
            TextureAtlasSprite sprite,
            float x,
            float y,
            float rotationDeg,
            int size
    ) {
        RenderSystem.enableBlend();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotationDeg));
        graphics.pose().translate(-size / 2.0F, -size / 2.0F, 0.0F);
        graphics.blit(0, 0, 200, size, size, sprite);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }

    private static void drawPlayerIcon(GuiGraphics graphics, Minecraft mc, float x, float y, float yRot) {
        TextureAtlasSprite sprite = mc.getMapDecorationTextures().get(PLAYER_DECORATION);
        drawMapDecoration(graphics, sprite, x, y, mapDecorationRotation(yRot), PLAYER_ICON_SIZE);
    }

    private static void drawOffMapIcon(GuiGraphics graphics, Minecraft mc, float x, float y) {
        TextureAtlasSprite sprite = mc.getMapDecorationTextures().get(PLAYER_OFF_MAP_DECORATION);
        drawMapDecoration(graphics, sprite, x, y, 0.0F, OFF_MAP_ICON_SIZE);
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = Mth.floor(Mth.sqrt((float) (radius * radius - (long) dy * dy) + 0.25F));
            graphics.fill(cx - halfWidth, cy + dy, cx + halfWidth + 1, cy + dy + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int x = radius;
        int y = 0;
        int decision = 1 - radius;

        while (x >= y) {
            plotSymmetric(graphics, cx, cy, x, y, color);
            y++;
            if (decision <= 0) {
                decision += 2 * y + 1;
            } else {
                x--;
                decision += 2 * (y - x) + 1;
            }
        }
    }

    private static void plotSymmetric(GuiGraphics graphics, int cx, int cy, int x, int y, int color) {
        fillPixel(graphics, cx + x, cy + y, color);
        fillPixel(graphics, cx - x, cy + y, color);
        fillPixel(graphics, cx + x, cy - y, color);
        fillPixel(graphics, cx - x, cy - y, color);
        if (x != y) {
            fillPixel(graphics, cx + y, cy + x, color);
            fillPixel(graphics, cx - y, cy + x, color);
            fillPixel(graphics, cx + y, cy - x, color);
            fillPixel(graphics, cx - y, cy - x, color);
        }
    }

    private static void fillPixel(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
    }
}
