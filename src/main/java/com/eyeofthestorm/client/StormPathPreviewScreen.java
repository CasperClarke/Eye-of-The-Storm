package com.eyeofthestorm.client;

import com.eyeofthestorm.StormConfig;
import com.eyeofthestorm.network.ApplyStormPathPreviewPayload;
import com.eyeofthestorm.storm.StormFourierPath;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Local preview of candidate sum-of-circles storm paths. Opened via {@code /storm preview_path}.
 * Geometry is refined recursively in screen space when zooming; speed color uses segment averages.
 */
public class StormPathPreviewScreen extends Screen {
    private static final int PAD = 12;
    /** Outer width of the right panel (inside screen PAD). */
    private static final int SIDEBAR_W = 248;
    /** Padding inside the panel for widgets / text. */
    private static final int SIDE_INNER = 10;
    private static final int FIELD_H = 16;
    private static final int FIELD_STEP = 26;
    private static final int FIRST_FIELD_Y = 36;
    private static final int FIELD_COUNT = 6;
    private static final int SIDE_BTN_H = 20;
    private static final int SIDE_BTN_GAP = 2;
    private static final int SIDE_BTN_COUNT = 6;
    private static final int DWELL_GRID_AXIS = 192;
    /** Hard cap on dwell grid extent per axis (zoom can request finer cells up to here). */
    private static final int DWELL_GRID_MAX_AXIS = 256;
    /** Target on-screen size of one dwell cell in pixels (finer when zoomed in). */
    private static final float DWELL_TARGET_PX_PER_CELL = 4.0F;
    /** Heatmap fill alpha when blitting dwell cells onto the plot. */
    private static final float DWELL_FILL_ALPHA = 0.40F;
    private static final int LUT_SAMPLES = 8192;
    private static final int BOUNDS_SAMPLES = 512;
    private static final int COVERAGE_SAMPLES = 2048;
    private static final int SEED_SEGMENTS = 96;
    /** Hard cap; screen-arc budgeting usually stays near {@link #TARGET_PATH_SAMPLES}. */
    private static final int MAX_ADAPTIVE_POINTS = 1_200;
    /** Target on-screen vertices for the colored path at any zoom. */
    private static final int TARGET_PATH_SAMPLES = 400;
    private static final int STRONGHOLD_COVERED = 0xFF3DDB7A;
    private static final int STRONGHOLD_MISSED = 0xFFE85D5D;
    /** Opaque white into the coverage FBO (alpha applied once on blit). */
    private static final int EYE_FILL_OPAQUE = 0xFFFFFFFF;
    private static final int EYE_EDGE = 0xE8FFFFFF;
    /** Final coverage tint when blitting the FBO onto the plot. */
    private static final float EYE_BLIT_ALPHA = 0.38F;
    /** Wedges per join disc (drawn as radial thick segments — same path as bodies). */
    private static final int EYE_CIRCLE_SEGMENTS = 20;
    private static final int PATH_OUTLINE = 0xFF151A22;
    private static final int SHAPE_COLOR = 0xFF2E3848;
    private static final int GRID_COLOR = 0xFF243040;
    private static final int PANEL_BG = 0xF0101419;
    private static final int PLOT_BG = 0xFF0A0E13;

    private long seed;
    private int circleCount;
    private double pathScale;
    private double peakSpeed;
    private double speedPhaseRatePerTick;
    /** Offset of the speed pulse in turns (0 = peak, 0.5 ≈ opposite). */
    private double speedPhaseOffsetTurns;
    /** Live storm path parameter when the preview was opened (or 0 after identity change). */
    private double markerPathParam;
    private double liveSpeedPhase;
    private double pathOriginX;
    private double pathOriginZ;
    private double eyeRadius;
    private final double[] strongholdX;
    private final double[] strongholdZ;
    /** True when the eye radius ever covers this stronghold along one loop. */
    private boolean[] strongholdCovered = new boolean[0];
    private int strongholdsCoveredCount;
    private final long openedSeed;
    private final int openedCircleCount;
    private final double openedPathScale;
    private final double openedPathParam;
    private final double openedPathOriginX;
    private final double openedPathOriginZ;
    /** Live storm world XZ when the preview opened (candidate paths anchor here). */
    private final double stormWorldX;
    private final double stormWorldZ;

    private double viewWorldX;
    private double viewWorldZ;
    /** Screen pixels per Minecraft block; independent of path bounds. */
    private double pxPerBlock = 1.0;
    /** pxPerBlock when the preview first opened (for zoom readout + reset). */
    private double initialPxPerBlock = 1.0;
    private boolean viewInitialized;
    /** Latest path hover under the cursor (for click-to-set apply start). */
    private HoverInfo lastHover;
    private boolean dragging;
    private boolean plotPressPending;
    private double plotPressX;
    private double plotPressY;
    private double lastMouseX;
    private double lastMouseY;
    /** Union tube vs speed-weighted dwell heatmap. */
    private CoverageMode coverageMode = CoverageMode.UNION;
    /** Precomputed dwell ticks per local-space grid cell (one lap). */
    private DwellGrid dwellGrid;
    private long dwellGridKey = Long.MIN_VALUE;

    private enum CoverageMode {
        UNION,
        DWELL
    }

    private StormFourierPath.Circle[] circles = new StormFourierPath.Circle[0];
    private PathMeta meta;
    private boolean dirty = true;

    private AdaptiveStroke stroke;
    private long strokeKey = Long.MIN_VALUE;
    /** Offscreen mask for eye coverage — opaque union, then one translucent blit. */
    private RenderTarget eyeCoverageTarget;
    /** Scroll offset for sidebar fields + stats (buttons stay pinned). */
    private int sidebarScroll;
    private int sidebarContentHeight;

    private EditBox seedBox;
    private EditBox circlesBox;
    private EditBox scaleBox;
    private EditBox peakBox;
    private EditBox periodBox;
    private EditBox phaseOffsetBox;
    private String fieldError = "";

    public StormPathPreviewScreen(
            long seed,
            int circleCount,
            double pathScale,
            double peakSpeed,
            double speedPhaseRatePerTick,
            double pathParam,
            double speedPhase,
            double pathOriginX,
            double pathOriginZ,
            double eyeRadius,
            double[] strongholdX,
            double[] strongholdZ
    ) {
        super(Component.translatable("screen.eyeofthestorm.path_preview"));
        this.seed = seed;
        this.circleCount = Mth.clamp(circleCount, 1, 64);
        this.pathScale = Math.max(1.0, pathScale);
        this.peakSpeed = peakSpeed > 1e-9 ? peakSpeed : StormConfig.defaultSpeed;
        this.speedPhaseRatePerTick = speedPhaseRatePerTick > 1e-15
                ? speedPhaseRatePerTick
                : StormConfig.speedPhaseRatePerTick;
        this.speedPhaseOffsetTurns = StormFourierPath.offsetTurnsFromSpeedPhase(speedPhase);
        this.markerPathParam = pathParam;
        this.liveSpeedPhase = speedPhase;
        this.pathOriginX = pathOriginX;
        this.pathOriginZ = pathOriginZ;
        this.eyeRadius = Math.max(1.0, eyeRadius);
        this.strongholdX = strongholdX != null ? strongholdX : new double[0];
        this.strongholdZ = strongholdZ != null ? strongholdZ : new double[0];
        this.openedSeed = seed;
        this.openedCircleCount = this.circleCount;
        this.openedPathScale = this.pathScale;
        this.openedPathParam = pathParam;
        this.openedPathOriginX = pathOriginX;
        this.openedPathOriginZ = pathOriginZ;
        StormFourierPath.Circle[] openedCircles = StormFourierPath.generate(
                RandomSource.create(seed), this.circleCount, this.pathScale
        );
        StormFourierPath.Vec2 openedLocal = StormFourierPath.position(openedCircles, pathParam);
        this.stormWorldX = pathOriginX + openedLocal.x();
        this.stormWorldZ = pathOriginZ + openedLocal.z();
    }

    private double phaseMinutes() {
        return speedPhaseRatePerTick > 0.0
                ? (Math.PI * 2.0) / (speedPhaseRatePerTick * 60.0 * 20.0)
                : Double.POSITIVE_INFINITY;
    }

    /** True when preview geometry matches the live storm path that opened this screen. */
    private boolean showingLivePath() {
        return seed == openedSeed
                && circleCount == openedCircleCount
                && Double.compare(pathScale, openedPathScale) == 0;
    }

    private double toWorldX(double localX) {
        return pathOriginX + localX;
    }

    private double toWorldZ(double localZ) {
        return pathOriginZ + localZ;
    }

    /** Pin {@code origin + z(marker)} to the live storm world position. */
    private void rebasePreviewOriginToStorm() {
        if (circles.length == 0) {
            return;
        }
        StormFourierPath.Vec2 local = StormFourierPath.position(circles, markerPathParam);
        pathOriginX = stormWorldX - local.x();
        pathOriginZ = stormWorldZ - local.z();
    }

    private int panelLeft() {
        return width - PAD - SIDEBAR_W;
    }

    private int panelRight() {
        return width - PAD;
    }

    private int contentLeft() {
        return panelLeft() + SIDE_INNER;
    }

    private int contentWidth() {
        return SIDEBAR_W - SIDE_INNER * 2;
    }

    private int sideButtonStackHeight() {
        return SIDE_BTN_COUNT * SIDE_BTN_H + (SIDE_BTN_COUNT - 1) * SIDE_BTN_GAP + SIDE_INNER;
    }

    /** Top of the scrollable stats region (below the fixed EditBoxes). */
    private int sidebarScrollTop() {
        return FIRST_FIELD_Y + FIELD_STEP * (FIELD_COUNT - 1) + FIELD_H + 6;
    }

    private int sidebarScrollBottom() {
        return height - PAD - sideButtonStackHeight();
    }

    private int maxSidebarScroll() {
        int viewH = Math.max(1, sidebarScrollBottom() - sidebarScrollTop());
        return Math.max(0, sidebarContentHeight - viewH);
    }

    private void clampSidebarScroll() {
        sidebarScroll = Mth.clamp(sidebarScroll, 0, maxSidebarScroll());
    }

    @Override
    protected void init() {
        int x = contentLeft();
        int w = contentWidth();
        int y = FIRST_FIELD_Y;

        seedBox = makeField(x, y, w, FIELD_H, Long.toString(seed));
        y += FIELD_STEP;
        circlesBox = makeField(x, y, w, FIELD_H, Integer.toString(circleCount));
        y += FIELD_STEP;
        scaleBox = makeField(x, y, w, FIELD_H, formatInput(pathScale));
        y += FIELD_STEP;
        peakBox = makeField(x, y, w, FIELD_H, formatInput(peakSpeed));
        y += FIELD_STEP;
        periodBox = makeField(x, y, w, FIELD_H, formatInput(phaseMinutes()));
        y += FIELD_STEP;
        phaseOffsetBox = makeField(x, y, w, FIELD_H, formatInput(speedPhaseOffsetTurns));

        int by = height - PAD - SIDE_INNER - SIDE_BTN_H;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, by, w, SIDE_BTN_H).build());
        by -= SIDE_BTN_H + SIDE_BTN_GAP;
        addRenderableWidget(Button.builder(Component.literal("Apply to storm"), b -> {
            if (applyFieldsFromBoxes()) {
                applyToStorm();
            }
        }).bounds(x, by, w, SIDE_BTN_H).build());
        by -= SIDE_BTN_H + SIDE_BTN_GAP;
        addRenderableWidget(Button.builder(Component.literal("Rebuild preview"), b -> applyFieldsFromBoxes())
                .bounds(x, by, w, SIDE_BTN_H).build());
        by -= SIDE_BTN_H + SIDE_BTN_GAP;
        addRenderableWidget(Button.builder(Component.literal("Randomize seed"), b -> {
            seed = RandomSource.create().nextLong();
            seedBox.setValue(Long.toString(seed));
            // New shape through the live storm position; apply starts at t=0 there.
            markerPathParam = 0.0;
            markDirty();
        }).bounds(x, by, w, SIDE_BTN_H).build());
        by -= SIDE_BTN_H + SIDE_BTN_GAP;
        addRenderableWidget(Button.builder(Component.literal("Reset view"), b -> {
            viewWorldX = stormWorldX;
            viewWorldZ = stormWorldZ;
            if (initialPxPerBlock > 0.0) {
                pxPerBlock = initialPxPerBlock;
            }
            strokeKey = Long.MIN_VALUE;
            dwellGridKey = Long.MIN_VALUE;
        }).bounds(x, by, w, SIDE_BTN_H).build());
        by -= SIDE_BTN_H + SIDE_BTN_GAP;
        addRenderableWidget(Button.builder(Component.literal(coverageModeLabel()), b -> {
            coverageMode = coverageMode == CoverageMode.UNION ? CoverageMode.DWELL : CoverageMode.UNION;
            b.setMessage(Component.literal(coverageModeLabel()));
        }).bounds(x, by, w, SIDE_BTN_H).build());
    }

    private void ensureViewInitialized(int plotW, int plotH) {
        if (viewInitialized || meta == null) {
            return;
        }
        viewWorldX = stormWorldX;
        viewWorldZ = stormWorldZ;
        double span = Math.max(64.0, meta.bounds.span());
        initialPxPerBlock = Math.min(plotW, plotH) / (span * 1.12);
        pxPerBlock = initialPxPerBlock;
        viewInitialized = true;
    }

    private double zoomFactor() {
        return initialPxPerBlock > 0.0 ? pxPerBlock / initialPxPerBlock : 1.0;
    }

    private boolean segmentNearViewWorld(
            StormFourierPath.Vec2 p0,
            StormFourierPath.Vec2 p1,
            StormFourierPath.Vec2 pm,
            double minX, double maxX, double minZ, double maxZ
    ) {
        double segMinX = pathOriginX + Math.min(pm.x(), Math.min(p0.x(), p1.x()));
        double segMaxX = pathOriginX + Math.max(pm.x(), Math.max(p0.x(), p1.x()));
        double segMinZ = pathOriginZ + Math.min(pm.z(), Math.min(p0.z(), p1.z()));
        double segMaxZ = pathOriginZ + Math.max(pm.z(), Math.max(p0.z(), p1.z()));
        return segMaxX >= minX && segMinX <= maxX && segMaxZ >= minZ && segMinZ <= maxZ;
    }

    private int localToScreenX(double localX, int plotLeft, int plotW) {
        return worldToScreenX(pathOriginX + localX, plotLeft, plotW, viewWorldX, pxPerBlock);
    }

    private int localToScreenZ(double localZ, int plotTop, int plotH) {
        return worldToScreenZ(pathOriginZ + localZ, plotTop, plotH, viewWorldZ, pxPerBlock);
    }

    private float localToScreenXf(double localX, int plotLeft, int plotW) {
        return worldToScreenXf(pathOriginX + localX, plotLeft, plotW, viewWorldX, pxPerBlock);
    }

    private float localToScreenZf(double localZ, int plotTop, int plotH) {
        return worldToScreenZf(pathOriginZ + localZ, plotTop, plotH, viewWorldZ, pxPerBlock);
    }

    private String coverageModeLabel() {
        return coverageMode == CoverageMode.UNION ? "Coverage: union" : "Coverage: dwell";
    }

    private EditBox makeField(int x, int y, int w, int h, String value) {
        EditBox box = new EditBox(font, x, y, w, h, Component.empty());
        box.setMaxLength(64);
        box.setValue(value);
        box.setBordered(true);
        addRenderableWidget(box);
        return box;
    }

    private static String formatInput(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e12) {
            return Long.toString((long) Math.rint(v));
        }
        return String.format("%.6g", v);
    }

    /** Parse sidebar fields into model values and rebuild. */
    private boolean applyFieldsFromBoxes() {
        try {
            long newSeed = Long.parseLong(seedBox.getValue().trim());
            int newCircles = Integer.parseInt(circlesBox.getValue().trim());
            double newScale = Double.parseDouble(scaleBox.getValue().trim());
            double newPeak = Double.parseDouble(peakBox.getValue().trim());
            double newPeriodMin = Double.parseDouble(periodBox.getValue().trim());
            double newOffsetTurns = Double.parseDouble(phaseOffsetBox.getValue().trim());

            if (newCircles < 1 || newCircles > 64) {
                fieldError = "circles must be 1–64";
                return false;
            }
            if (!(newScale >= 1.0) || Double.isNaN(newScale) || Double.isInfinite(newScale)) {
                fieldError = "path scale must be >= 1";
                return false;
            }
            if (!(newPeak > 0.0) || Double.isNaN(newPeak) || Double.isInfinite(newPeak)) {
                fieldError = "peak speed must be > 0";
                return false;
            }
            if (!(newPeriodMin > 0.0) || Double.isNaN(newPeriodMin) || Double.isInfinite(newPeriodMin)) {
                fieldError = "period minutes must be > 0";
                return false;
            }
            if (Double.isNaN(newOffsetTurns) || Double.isInfinite(newOffsetTurns)) {
                fieldError = "phase offset must be a number";
                return false;
            }

            boolean identityChanged = newSeed != seed
                    || newCircles != circleCount
                    || Double.compare(newScale, pathScale) != 0;
            seed = newSeed;
            circleCount = newCircles;
            pathScale = newScale;
            peakSpeed = newPeak;
            speedPhaseRatePerTick = (Math.PI * 2.0) / (newPeriodMin * 60.0 * 20.0);
            speedPhaseOffsetTurns = newOffsetTurns;
            liveSpeedPhase = StormFourierPath.speedPhaseFromOffsetTurns(speedPhaseOffsetTurns);
            if (identityChanged) {
                boolean sameAsOpened = newSeed == openedSeed
                        && newCircles == openedCircleCount
                        && Double.compare(newScale, openedPathScale) == 0;
                // Live path → restore live marker; new geometry → t=0 at storm world pos.
                markerPathParam = sameAsOpened ? openedPathParam : 0.0;
            }
            fieldError = "";
            markDirty();
            return true;
        } catch (NumberFormatException e) {
            fieldError = "invalid number in fields";
            return false;
        }
    }

    private void markDirty() {
        dirty = true;
        strokeKey = Long.MIN_VALUE;
        dwellGridKey = Long.MIN_VALUE;
    }

    private void applyToStorm() {
        PacketDistributor.sendToServer(new ApplyStormPathPreviewPayload(
                seed,
                circleCount,
                pathScale,
                peakSpeed,
                speedPhaseRatePerTick,
                speedPhaseOffsetTurns,
                markerPathParam
        ));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur / dirt overlay.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int plotLeft = PAD;
        int plotTop = PAD;
        int plotRight = panelLeft() - PAD / 2;
        int plotBottom = height - PAD;
        int plotW = Math.max(32, plotRight - plotLeft);
        int plotH = Math.max(32, plotBottom - plotTop);
        int sideL = panelLeft();
        int sideR = panelRight();
        int contentX = contentLeft();

        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.fill(plotLeft, plotTop, plotRight, plotBottom, PLOT_BG);
        graphics.fill(sideL, PAD, sideR, height - PAD, PANEL_BG);
        graphics.renderOutline(sideL, PAD, sideR - sideL, height - PAD * 2, 0xFF5A6A7A);

        rebuildMetaIfDirty();
        HoverInfo hover = null;
        DwellSample dwellHover = null;
        if (meta == null || circles.length == 0) {
            graphics.drawString(font, "No preview path.", plotLeft + 12, plotTop + 12, 0xFFAAAAAA, false);
            drawFieldLabels(graphics, contentX);
            drawSidebar(graphics, contentX, 0, null, null);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        ensureViewInitialized(plotW, plotH);

        AdaptiveStroke drawStroke = ensureAdaptiveStroke(
                plotLeft, plotTop, plotW, plotH
        );

        // Clip all plot drawing to the map rect
        graphics.enableScissor(plotLeft, plotTop, plotRight, plotBottom);
        try {
            drawGrid(graphics, plotLeft, plotTop, plotW, plotH);

            // Eye coverage under the path.
            StormFourierPath.Vec2 here = StormFourierPath.position(circles, markerPathParam);
            if (coverageMode == CoverageMode.UNION) {
                drawEyeRadiusOverlay(
                        graphics, drawStroke, here.x(), here.z(),
                        plotLeft, plotTop, plotW, plotH
                );
            } else {
                ensureDwellGrid(plotW, plotH);
                drawDwellOverlay(graphics, plotLeft, plotTop, plotW, plotH);
                drawMarkerEyeRing(graphics, here.x(), here.z(), plotLeft, plotTop, plotW, plotH);
            }

            drawAdaptivePath(graphics, drawStroke, plotLeft, plotTop, plotW, plotH);
            drawStrongholds(graphics, plotLeft, plotTop, plotW, plotH);

            // Dim marker: path t=0 (loop anchor in local path space)
            StormFourierPath.Vec2 loopStart = StormFourierPath.position(circles, 0.0);
            int lsx = localToScreenX(loopStart.x(), plotLeft, plotW);
            int lsz = localToScreenZ(loopStart.z(), plotTop, plotH);
            graphics.fill(lsx - 2, lsz - 2, lsx + 3, lsz + 3, 0xFF6A7480);
            graphics.renderOutline(lsx - 3, lsz - 3, 7, 7, 0xFF9AA6B2);

            // Bright marker: live storm position on this path
            int sx = localToScreenX(here.x(), plotLeft, plotW);
            int sz = localToScreenZ(here.z(), plotTop, plotH);
            graphics.fill(sx - 3, sz - 3, sx + 4, sz + 4, 0xFFFF4040);
            graphics.renderOutline(sx - 4, sz - 4, 9, 9, 0xFFFFFFFF);

            drawScaleBar(graphics, plotLeft, plotTop, plotW, plotH);

            hover = null;
            if (mouseX >= plotLeft && mouseX < plotRight && mouseY >= plotTop && mouseY < plotBottom) {
                if (coverageMode == CoverageMode.DWELL) {
                    dwellHover = sampleDwellAtScreen(mouseX, mouseY, plotLeft, plotTop, plotW, plotH);
                }
                hover = findHover(drawStroke, mouseX, mouseY, plotLeft, plotTop, plotW, plotH);
                if (hover != null) {
                    int hx = localToScreenX(hover.localX, plotLeft, plotW);
                    int hz = localToScreenZ(hover.localZ, plotTop, plotH);
                    graphics.renderOutline(hx - 3, hz - 3, 7, 7, 0xFFFFFFFF);
                    graphics.fill(hx - 1, hz - 1, hx + 2, hz + 2, 0xFFFFFF66);
                }
            }
            lastHover = hover;
        } finally {
            graphics.disableScissor();
        }

        graphics.renderOutline(plotLeft, plotTop, plotW, plotH, 0xFF5A6A7A);
        drawFieldLabels(graphics, contentX);
        drawSidebar(graphics, contentX, drawStroke.points, hover, dwellHover);
        if (hover != null || dwellHover != null) {
            drawHoverTooltip(graphics, mouseX, mouseY, plotLeft, plotTop, plotRight, plotBottom, hover, dwellHover);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawFieldLabels(GuiGraphics graphics, int left) {
        // Must match EditBox Y positions from init()
        int y = FIRST_FIELD_Y;
        graphics.drawString(font, "seed", left, y - 10, 0xFF8B9AAB, false);
        y += FIELD_STEP;
        graphics.drawString(font, "circles", left, y - 10, 0xFF8B9AAB, false);
        y += FIELD_STEP;
        graphics.drawString(font, "path scale", left, y - 10, 0xFF8B9AAB, false);
        y += FIELD_STEP;
        graphics.drawString(font, "peak speed (blk/tick)", left, y - 10, 0xFF8B9AAB, false);
        y += FIELD_STEP;
        graphics.drawString(font, "speed pulse (minutes)", left, y - 10, 0xFF8B9AAB, false);
        y += FIELD_STEP;
        graphics.drawString(font, "phase offset (turns)", left, y - 10, 0xFF8B9AAB, false);
    }

    private void rebuildMetaIfDirty() {
        if (!dirty && meta != null) {
            return;
        }
        circles = StormFourierPath.generate(RandomSource.create(seed), circleCount, pathScale);
        rebasePreviewOriginToStorm();
        double startPhase = StormFourierPath.speedPhaseFromOffsetTurns(speedPhaseOffsetTurns);
        meta = buildMeta(circles, peakSpeed, speedPhaseRatePerTick, startPhase);
        dwellGridKey = Long.MIN_VALUE;
        recomputeStrongholdCoverage();
        dirty = false;
        strokeKey = Long.MIN_VALUE;
    }

    private void ensureDwellGrid(int plotW, int plotH) {
        if (meta == null || circles.length == 0 || !(eyeRadius > 0.0)) {
            dwellGrid = null;
            return;
        }
        long key = dwellGridCacheKey(plotW, plotH);
        if (dwellGrid != null && dwellGridKey == key) {
            return;
        }
        rebuildDwellGrid(plotW, plotH);
        dwellGridKey = key;
    }

    private long dwellGridCacheKey(int plotW, int plotH) {
        long qx = Math.round(viewWorldX * pxPerBlock / 4.0);
        long qz = Math.round(viewWorldZ * pxPerBlock / 4.0);
        long qs = Math.round(Math.log(Math.max(pxPerBlock, 1e-12)) * 24.0);
        long path = seed
                ^ ((long) circleCount << 32)
                ^ Double.doubleToLongBits(pathScale)
                ^ Double.doubleToLongBits(peakSpeed)
                ^ Double.doubleToLongBits(speedPhaseOffsetTurns);
        long eye = Double.doubleToLongBits(eyeRadius);
        long origin = Double.doubleToLongBits(pathOriginX) ^ Double.doubleToLongBits(pathOriginZ);
        return path ^ eye ^ origin ^ (qx * 2654435761L) ^ (qz * 1597334677L) ^ qs
                ^ (((long) plotW) << 20) ^ plotH;
    }

    /**
     * Accumulate speed-weighted dwell (ticks inside eye per lap) on a world-space grid
     * sized to the visible viewport and zoom (finer cells when zoomed in).
     */
    private void rebuildDwellGrid(int plotW, int plotH) {
        if (meta == null || circles.length == 0 || !(eyeRadius > 0.0) || pxPerBlock <= 1e-12) {
            dwellGrid = null;
            return;
        }

        double worldPerPixel = 1.0 / pxPerBlock;
        double viewWidth = plotW * worldPerPixel;
        double viewDepth = plotH * worldPerPixel;
        double pad = eyeRadius + Math.max(viewWidth, viewDepth) * 0.05;

        double minX = viewWorldX - viewWidth * 0.5 - pad;
        double maxX = viewWorldX + viewWidth * 0.5 + pad;
        double minZ = viewWorldZ - viewDepth * 0.5 - pad;
        double maxZ = viewWorldZ + viewDepth * 0.5 + pad;

        double boundMinX = pathOriginX + meta.bounds.minX() - eyeRadius;
        double boundMaxX = pathOriginX + meta.bounds.maxX() + eyeRadius;
        double boundMinZ = pathOriginZ + meta.bounds.minZ() - eyeRadius;
        double boundMaxZ = pathOriginZ + meta.bounds.maxZ() + eyeRadius;
        minX = Math.max(minX, boundMinX);
        maxX = Math.min(maxX, boundMaxX);
        minZ = Math.max(minZ, boundMinZ);
        maxZ = Math.min(maxZ, boundMaxZ);

        double width = Math.max(maxX - minX, 1.0);
        double depth = Math.max(maxZ - minZ, 1.0);
        double visibleLonger = Math.max(viewWidth, viewDepth);

        double cellFromZoom = worldPerPixel * DWELL_TARGET_PX_PER_CELL;
        double cellFromView = Math.max(visibleLonger / DWELL_GRID_AXIS, 1.0);
        double cellMaxForEye = eyeRadius / 6.0;
        double cellSize = Math.max(1.0, Math.min(cellFromZoom, Math.min(cellFromView, cellMaxForEye)));

        int cols = Math.max(1, (int) Math.ceil(width / cellSize));
        int rows = Math.max(1, (int) Math.ceil(depth / cellSize));
        while (cols > DWELL_GRID_MAX_AXIS || rows > DWELL_GRID_MAX_AXIS) {
            cellSize *= 1.25;
            cols = Math.max(1, (int) Math.ceil(width / cellSize));
            rows = Math.max(1, (int) Math.ceil(depth / cellSize));
        }
        cols = Math.min(cols, DWELL_GRID_MAX_AXIS);
        rows = Math.min(rows, DWELL_GRID_MAX_AXIS);
        float[] data = new float[cols * rows];

        double period = StormFourierPath.fundamentalPeriod();
        double r2 = eyeRadius * eyeRadius;
        for (int i = 0; i < LUT_SAMPLES; i++) {
            double t0 = period * i / (double) LUT_SAMPLES;
            double t1 = period * (i + 1) / (double) LUT_SAMPLES;
            double tMid = 0.5 * (t0 + t1);
            StormFourierPath.Vec2 p = StormFourierPath.position(circles, tMid);
            double wx = pathOriginX + p.x();
            double wz = pathOriginZ + p.z();
            double dt = meta.timeLut[i + 1] - meta.timeLut[i];
            if (!(dt > 0.0)) {
                continue;
            }

            int ix0 = Mth.clamp((int) Math.floor((wx - eyeRadius - minX) / cellSize), 0, cols - 1);
            int ix1 = Mth.clamp((int) Math.floor((wx + eyeRadius - minX) / cellSize), 0, cols - 1);
            int iz0 = Mth.clamp((int) Math.floor((wz - eyeRadius - minZ) / cellSize), 0, rows - 1);
            int iz1 = Mth.clamp((int) Math.floor((wz + eyeRadius - minZ) / cellSize), 0, rows - 1);
            if (ix0 > ix1 || iz0 > iz1) {
                continue;
            }
            for (int iz = iz0; iz <= iz1; iz++) {
                double cz = minZ + (iz + 0.5) * cellSize;
                double dz = cz - wz;
                for (int ix = ix0; ix <= ix1; ix++) {
                    double cx = minX + (ix + 0.5) * cellSize;
                    double dx = cx - wx;
                    if (dx * dx + dz * dz <= r2) {
                        data[ix + iz * cols] += (float) dt;
                    }
                }
            }
        }

        double max = 0.0;
        double sum = 0.0;
        int count = 0;
        for (float v : data) {
            if (v > 0.0F) {
                max = Math.max(max, v);
                sum += v;
                count++;
            }
        }
        dwellGrid = new DwellGrid(minX, minZ, cellSize, cols, rows, data, max, count > 0 ? sum / count : 0.0);
    }

    /**
     * A stronghold is covered if some sample on the loop lies within {@link #eyeRadius}
     * (world: {@code pathOrigin + z(t)}).
     */
    private void recomputeStrongholdCoverage() {
        int n = Math.min(strongholdX.length, strongholdZ.length);
        strongholdCovered = new boolean[n];
        strongholdsCoveredCount = 0;
        if (n == 0 || circles.length == 0) {
            return;
        }
        double period = StormFourierPath.fundamentalPeriod();
        double r2 = eyeRadius * eyeRadius;
        double[] sampleX = new double[COVERAGE_SAMPLES];
        double[] sampleZ = new double[COVERAGE_SAMPLES];
        for (int i = 0; i < COVERAGE_SAMPLES; i++) {
            StormFourierPath.Vec2 p = StormFourierPath.position(circles, period * i / COVERAGE_SAMPLES);
            sampleX[i] = pathOriginX + p.x();
            sampleZ[i] = pathOriginZ + p.z();
        }
        for (int s = 0; s < n; s++) {
            double sx = strongholdX[s];
            double sz = strongholdZ[s];
            boolean covered = false;
            for (int i = 0; i < COVERAGE_SAMPLES; i++) {
                double dx = sx - sampleX[i];
                double dz = sz - sampleZ[i];
                if (dx * dx + dz * dz <= r2) {
                    covered = true;
                    break;
                }
            }
            strongholdCovered[s] = covered;
            if (covered) {
                strongholdsCoveredCount++;
            }
        }
    }

    /**
     * Eye coverage as a round stroke (polyline ⊕ disk), drawn opaque into an FBO then
     * blitted once with alpha so overlaps don't stack translucency.
     */
    private void drawEyeRadiusOverlay(
            GuiGraphics graphics,
            AdaptiveStroke stroke,
            double markerLocalX,
            double markerLocalZ,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (!(eyeRadius > 0.0) || stroke == null || stroke.points < 2) {
            return;
        }

        float radiusPx = (float) (eyeRadius * pxPerBlock);
        if (radiusPx < 0.5F) {
            return;
        }

        // Pad the FBO by eye radius so coverage near plot edges isn't clipped by the
        // framebuffer (path can sit inside the plot while the tube extends past it).
        int pad = (int) Math.ceil(radiusPx) + 2;
        int fbW = plotW + pad * 2;
        int fbH = plotH + pad * 2;

        graphics.flush();
        ensureEyeCoverageTarget(fbW, fbH);
        Minecraft mc = Minecraft.getInstance();
        // bindWrite(true) resizes the GL viewport to the FBO — must restore the main
        // target with bindWrite(true) afterward or the whole GUI stays FBO-sized.
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            eyeCoverageTarget.setClearColor(0f, 0f, 0f, 0f);
            eyeCoverageTarget.clear(Minecraft.ON_OSX);
            eyeCoverageTarget.bindWrite(true);

            Matrix4f ortho = new Matrix4f().setOrtho(0.0F, fbW, fbH, 0.0F, 1000.0F, 3000.0F);
            RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
            modelView.identity();
            modelView.translate(0.0F, 0.0F, -2000.0F);
            RenderSystem.applyModelViewMatrix();

            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            Matrix4f mat = new Matrix4f();
            BufferBuilder buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR
            );
            // Plot-local → FBO coords: shift by pad so (0,0) plot is at (pad,pad).
            float prevX = worldToScreenXf(pathOriginX + stroke.x[0], 0, plotW, viewWorldX, pxPerBlock) + pad;
            float prevZ = worldToScreenZf(pathOriginZ + stroke.z[0], 0, plotH, viewWorldZ, pxPerBlock) + pad;
            for (int i = 1; i < stroke.points; i++) {
                float sx = worldToScreenXf(pathOriginX + stroke.x[i], 0, plotW, viewWorldX, pxPerBlock) + pad;
                float sz = worldToScreenZf(pathOriginZ + stroke.z[i], 0, plotH, viewWorldZ, pxPerBlock) + pad;
                if (segmentTouchesPlotPadded(
                        (int) prevX, (int) prevZ, (int) sx, (int) sz,
                        0, 0, fbW, fbH, pad
                )) {
                    appendShortenedThickSegment(
                            buffer, mat, prevX, prevZ, sx, sz, radiusPx, EYE_FILL_OPAQUE
                    );
                }
                prevX = sx;
                prevZ = sz;
            }

            float lastJoinX = Float.NaN;
            float lastJoinZ = Float.NaN;
            for (int i = 0; i < stroke.points; i++) {
                float cx = worldToScreenXf(pathOriginX + stroke.x[i], 0, plotW, viewWorldX, pxPerBlock) + pad;
                float cz = worldToScreenZf(pathOriginZ + stroke.z[i], 0, plotH, viewWorldZ, pxPerBlock) + pad;
                if (cx < -pad || cx > fbW + pad || cz < -pad || cz > fbH + pad) {
                    continue;
                }
                if (!Float.isNaN(lastJoinX)
                        && Math.hypot(cx - lastJoinX, cz - lastJoinZ) < 0.75F) {
                    continue;
                }
                appendGuiDisc(buffer, mat, cx, cz, radiusPx, EYE_FILL_OPAQUE);
                lastJoinX = cx;
                lastJoinZ = cz;
            }

            var mesh = buffer.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // true => restore full-window viewport (false leaves the FBO-sized one).
            mc.getMainRenderTarget().bindWrite(true);
        }

        blitEyeCoverageToPlot(graphics, plotLeft, plotTop, plotW, plotH, pad);
        drawMarkerEyeRing(graphics, markerLocalX, markerLocalZ, plotLeft, plotTop, plotW, plotH);
    }

    /** Crisp eye ring at the storm marker (union blit is separate). */
    private void drawMarkerEyeRing(
            GuiGraphics graphics,
            double markerLocalX,
            double markerLocalZ,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (!(eyeRadius > 0.0)) {
            return;
        }
        float radiusPx = (float) (eyeRadius * pxPerBlock);
        if (radiusPx < 0.5F) {
            return;
        }
        Matrix4f guiMat = graphics.pose().last().pose();
        VertexConsumer quads = graphics.bufferSource().getBuffer(RenderType.gui());
        float mx = localToScreenXf(markerLocalX, plotLeft, plotW);
        float mz = localToScreenZf(markerLocalZ, plotTop, plotH);
        appendGuiCircleOutline(quads, guiMat, mx, mz, radiusPx, EYE_EDGE, 1.25F);
        graphics.flush();
    }

    /**
     * Translucent heatmap of per-cell dwell (ticks inside eye over one lap).
     */
    private void drawDwellOverlay(
            GuiGraphics graphics,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (dwellGrid == null || dwellGrid.max <= 0.0 || pxPerBlock <= 1e-12) {
            return;
        }

        double worldPerPixel = 1.0 / pxPerBlock;
        double viewMinX = viewWorldX - plotW * 0.5 * worldPerPixel;
        double viewMaxX = viewWorldX + plotW * 0.5 * worldPerPixel;
        double viewMinZ = viewWorldZ - plotH * 0.5 * worldPerPixel;
        double viewMaxZ = viewWorldZ + plotH * 0.5 * worldPerPixel;

        int ix0 = Mth.clamp((int) Math.floor((viewMinX - dwellGrid.originX) / dwellGrid.cellSize), 0, dwellGrid.cols - 1);
        int ix1 = Mth.clamp((int) Math.floor((viewMaxX - dwellGrid.originX) / dwellGrid.cellSize), 0, dwellGrid.cols - 1);
        int iz0 = Mth.clamp((int) Math.floor((viewMinZ - dwellGrid.originZ) / dwellGrid.cellSize), 0, dwellGrid.rows - 1);
        int iz1 = Mth.clamp((int) Math.floor((viewMaxZ - dwellGrid.originZ) / dwellGrid.cellSize), 0, dwellGrid.rows - 1);

        for (int iz = iz0; iz <= iz1; iz++) {
            double lz0 = dwellGrid.originZ + iz * dwellGrid.cellSize;
            double lz1 = lz0 + dwellGrid.cellSize;
            int sz0 = worldToScreenZ(lz0, plotTop, plotH, viewWorldZ, pxPerBlock);
            int sz1 = worldToScreenZ(lz1, plotTop, plotH, viewWorldZ, pxPerBlock);
            int cellTop = Math.min(sz0, sz1);
            int cellBottom = Math.max(sz0, sz1) + 1;
            for (int ix = ix0; ix <= ix1; ix++) {
                float dwell = dwellGrid.data[ix + iz * dwellGrid.cols];
                if (dwell <= 0.0F) {
                    continue;
                }
                double lx0 = dwellGrid.originX + ix * dwellGrid.cellSize;
                double lx1 = lx0 + dwellGrid.cellSize;
                int sx0 = worldToScreenX(lx0, plotLeft, plotW, viewWorldX, pxPerBlock);
                int sx1 = worldToScreenX(lx1, plotLeft, plotW, viewWorldX, pxPerBlock);
                int cellLeft = Math.min(sx0, sx1);
                int cellRight = Math.max(sx0, sx1) + 1;
                float frac = (float) (dwell / dwellGrid.max);
                graphics.fill(cellLeft, cellTop, cellRight, cellBottom, dwellFractionToColor(frac));
            }
        }
    }

    private DwellSample sampleDwellAtScreen(
            double mouseX, double mouseY,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (dwellGrid == null) {
            return null;
        }
        double worldX = viewWorldX + (mouseX - plotLeft - plotW * 0.5) / pxPerBlock;
        double worldZ = viewWorldZ + (mouseY - plotTop - plotH * 0.5) / pxPerBlock;
        double dwell = dwellGrid.sampleWorld(worldX, worldZ);
        if (!(dwell > 0.0)) {
            return null;
        }
        return new DwellSample(
                worldX - pathOriginX,
                worldZ - pathOriginZ,
                worldX,
                worldZ,
                dwell
        );
    }

    private static int dwellFractionToColor(float fraction) {
        int rgb = speedFractionToColor(fraction) & 0x00FFFFFF;
        int a = Mth.clamp((int) (DWELL_FILL_ALPHA * 255.0F), 0, 255);
        return (a << 24) | rgb;
    }

    private void ensureEyeCoverageTarget(int w, int h) {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (eyeCoverageTarget != null
                && eyeCoverageTarget.width == w
                && eyeCoverageTarget.height == h) {
            return;
        }
        if (eyeCoverageTarget != null) {
            eyeCoverageTarget.destroyBuffers();
        }
        eyeCoverageTarget = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        eyeCoverageTarget.setClearColor(0f, 0f, 0f, 0f);
    }

    /**
     * Translucent blit of the padded coverage mask. Drawn oversized so edge tubes that
     * were rendered into the pad region still show inside the plot scissor.
     */
    private void blitEyeCoverageToPlot(
            GuiGraphics graphics, int plotLeft, int plotTop, int plotW, int plotH, int pad
    ) {
        if (eyeCoverageTarget == null) {
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, eyeCoverageTarget.getColorTextureId());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Matrix4f matrix = graphics.pose().last().pose();
        int a = Mth.clamp((int) (EYE_BLIT_ALPHA * 255.0F), 0, 255);
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR
        );
        float x0 = plotLeft - pad;
        float y0 = plotTop - pad;
        float x1 = plotLeft + plotW + pad;
        float y1 = plotTop + plotH + pad;
        // FBO texture is Y-flipped vs GUI.
        buffer.addVertex(matrix, x0, y0, 0).setUv(0.0F, 1.0F).setColor(255, 255, 255, a);
        buffer.addVertex(matrix, x0, y1, 0).setUv(0.0F, 0.0F).setColor(255, 255, 255, a);
        buffer.addVertex(matrix, x1, y1, 0).setUv(1.0F, 0.0F).setColor(255, 255, 255, a);
        buffer.addVertex(matrix, x1, y0, 0).setUv(1.0F, 1.0F).setColor(255, 255, 255, a);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableDepthTest();
    }

    /**
     * Filled disc via radial {@link #appendThickSegment} wedges — reliable under
     * {@link RenderType#gui()} (unlike center-duplicated pie quads).
     */
    private static void appendGuiDisc(
            VertexConsumer buffer,
            Matrix4f matrix,
            float cx, float cy,
            float radiusPx,
            int fillArgb
    ) {
        int segs = eyeCircleSegments(radiusPx);
        float hw = radiusPx * (float) Math.tan(Math.PI / segs) * 1.08F;
        for (int i = 0; i < segs; i++) {
            double a = Math.PI * 2.0 * (i + 0.5) / segs;
            float ox = cx + (float) (Math.cos(a) * radiusPx);
            float oy = cy + (float) (Math.sin(a) * radiusPx);
            appendThickSegment(buffer, matrix, cx, cy, ox, oy, hw, fillArgb);
        }
    }

    /** Thick segment with ends pulled in so round join discs own the corners. */
    private static void appendShortenedThickSegment(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0, float y0, float x1, float y1,
            float halfWidth,
            int argb
    ) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1.5F) {
            return;
        }
        float inset = Math.min(halfWidth * 0.35F, len * 0.35F);
        float ux = dx / len;
        float uy = dy / len;
        appendThickSegment(
                buffer, matrix,
                x0 + ux * inset, y0 + uy * inset,
                x1 - ux * inset, y1 - uy * inset,
                halfWidth, argb
        );
    }

    private static void appendGuiCircleOutline(
            VertexConsumer buffer,
            Matrix4f matrix,
            float cx, float cy,
            float radiusPx,
            int edgeArgb,
            float edgeHalfPx
    ) {
        int segs = eyeCircleSegments(radiusPx);
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;
            float x0 = cx + (float) (Math.cos(a0) * radiusPx);
            float y0 = cy + (float) (Math.sin(a0) * radiusPx);
            float x1 = cx + (float) (Math.cos(a1) * radiusPx);
            float y1 = cy + (float) (Math.sin(a1) * radiusPx);
            appendThickSegment(buffer, matrix, x0, y0, x1, y1, edgeHalfPx, edgeArgb);
        }
    }

    private static int eyeCircleSegments(float radiusPx) {
        if (radiusPx > 100.0F) {
            return 36;
        }
        if (radiusPx < 6.0F) {
            return 12;
        }
        return EYE_CIRCLE_SEGMENTS;
    }

    private static boolean segmentTouchesPlotPadded(
            int x0, int y0, int x1, int y1,
            int plotLeft, int plotTop, int plotW, int plotH,
            int pad
    ) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        return maxX >= plotLeft - pad && minX < plotLeft + plotW + pad
                && maxY >= plotTop - pad && minY < plotTop + plotH + pad;
    }

    private void drawStrongholds(
            GuiGraphics graphics,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        int n = Math.min(strongholdX.length, strongholdZ.length);
        for (int i = 0; i < n; i++) {
            int sx = worldToScreenX(strongholdX[i], plotLeft, plotW, viewWorldX, pxPerBlock);
            int sz = worldToScreenZ(strongholdZ[i], plotTop, plotH, viewWorldZ, pxPerBlock);
            if (sx < plotLeft - 4 || sx > plotLeft + plotW + 4
                    || sz < plotTop - 4 || sz > plotTop + plotH + 4) {
                continue;
            }
            boolean covered = i < strongholdCovered.length && strongholdCovered[i];
            int color = covered ? STRONGHOLD_COVERED : STRONGHOLD_MISSED;
            // Diamond marker
            graphics.fill(sx - 1, sz - 3, sx + 2, sz + 4, color);
            graphics.fill(sx - 3, sz - 1, sx + 4, sz + 2, color);
            graphics.renderOutline(sx - 2, sz - 2, 5, 5, 0xFF101418);
        }
    }

    /**
     * Bounds + LUTs vs path parameter: speed curve and cumulative travel time from {@code startPhase}.
     */
    private static PathMeta buildMeta(
            StormFourierPath.Circle[] circles,
            double peakSpeed,
            double phaseRate,
            double startPhase
    ) {
        double period = StormFourierPath.fundamentalPeriod();
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        StormFourierPath.Vec2 prev = StormFourierPath.position(circles, 0.0);
        minX = maxX = prev.x();
        minZ = maxZ = prev.z();
        for (int i = 1; i <= BOUNDS_SAMPLES; i++) {
            StormFourierPath.Vec2 p = StormFourierPath.position(circles, period * i / BOUNDS_SAMPLES);
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
            prev = p;
        }
        double loop = Math.max(StormFourierPath.estimateLoopLength(circles), 1.0);

        // LUT indexed by path parameter t ∈ [0, period], starting at startPhase
        // (live storm phase when previewing the current path; peak after Apply/regen).
        float[] speedLut = new float[LUT_SAMPLES + 1];
        double[] timeLut = new double[LUT_SAMPLES + 1];
        double u = startPhase;
        double peak = Math.max(peakSpeed, 1e-9);
        double rate = Math.max(phaseRate, 1e-15);
        double eps = StormConfig.pathDerivEpsilon;
        double cumTicks = 0.0;
        timeLut[0] = 0.0;
        for (int i = 0; i <= LUT_SAMPLES; i++) {
            double t = period * i / (double) LUT_SAMPLES;
            double curve = StormFourierPath.speedScale(u);
            speedLut[i] = (float) curve;
            if (i < LUT_SAMPLES) {
                double tNext = period * (i + 1) / (double) LUT_SAMPLES;
                double mag = Math.max(StormFourierPath.derivative(circles, t).length(), eps);
                double ds = mag * (tNext - t);
                PhaseStep step = advanceLikeStepMotion(ds, u, peak, rate);
                cumTicks += step.ticks();
                u = step.phase();
                timeLut[i + 1] = cumTicks;
            }
        }

        double meanLapTicks = loop / (peak * StormConfig.SPEED_SCALE_MEAN);
        return new PathMeta(
                new Bounds(minX, maxX, minZ, maxZ),
                loop,
                speedLut,
                timeLut,
                cumTicks,
                meanLapTicks
        );
    }

    /**
     * Advance like {@link com.eyeofthestorm.storm.StormLogic} stepMotion: phase first, then move
     * {@code peak · speedScale(u)} blocks per tick (batched for preview performance).
     */
    private static PhaseStep advanceLikeStepMotion(double ds, double phase, double peak, double rate) {
        double remaining = ds;
        double ticks = 0.0;
        double u = phase;
        int guard = 0;
        while (remaining > 1e-9 && guard++ < 512) {
            // Batch so phase does not jump more than ~π/16 (same idea as live, continuous).
            double maxDu = Math.PI / 16.0;
            double stepTicks = Math.max(maxDu / rate, 1e-9);

            // Peek speed after advancing phase (mirrors stepMotion order).
            double uAfter = u + rate * stepTicks;
            double curve = StormFourierPath.speedScale(uAfter);
            if (curve < 1e-4) {
                // Near trough: spend time advancing phase until motion resumes.
                double du = Math.PI / 32.0;
                double wait = du / rate;
                ticks += wait;
                u += du;
                continue;
            }
            double speed = peak * curve;
            stepTicks = Math.min(remaining / speed, stepTicks);
            uAfter = u + rate * stepTicks;
            curve = StormFourierPath.speedScale(uAfter);
            speed = peak * Math.max(curve, 1e-12);
            stepTicks = Math.min(remaining / speed, stepTicks);
            remaining -= speed * stepTicks;
            ticks += stepTicks;
            u = u + rate * stepTicks;
        }
        return new PhaseStep(u, ticks);
    }

    private record PhaseStep(double phase, double ticks) {}

    private AdaptiveStroke ensureAdaptiveStroke(int plotLeft, int plotTop, int plotW, int plotH) {
        long key = strokeCacheKey(plotW, plotH);
        if (stroke != null && strokeKey == key) {
            return stroke;
        }
        stroke = buildAdaptiveStroke(plotLeft, plotTop, plotW, plotH);
        strokeKey = key;
        return stroke;
    }

    private long strokeCacheKey(int plotW, int plotH) {
        long qx = Math.round(viewWorldX * pxPerBlock / 6.0);
        long qz = Math.round(viewWorldZ * pxPerBlock / 6.0);
        long qs = Math.round(Math.log(Math.max(pxPerBlock, 1e-12)) * 32.0);
        long origin = Double.doubleToLongBits(pathOriginX) ^ Double.doubleToLongBits(pathOriginZ);
        return (((long) plotW) << 48) ^ (((long) plotH) << 32) ^ (qx * 1315423911L) ^ (qz * 2654435761L) ^ qs ^ origin;
    }

    private AdaptiveStroke buildAdaptiveStroke(int plotLeft, int plotTop, int plotW, int plotH) {
        double period = StormFourierPath.fundamentalPeriod();
        List<Sample> out = new ArrayList<>(TARGET_PATH_SAMPLES + SEED_SEGMENTS);
        int[] pointBudget = {MAX_ADAPTIVE_POINTS};

        double marginBlocks = 24.0 / Math.max(pxPerBlock, 1e-9);
        double viewMinX = viewWorldX - plotW * 0.5 / pxPerBlock - marginBlocks;
        double viewMaxX = viewWorldX + plotW * 0.5 / pxPerBlock + marginBlocks;
        double viewMinZ = viewWorldZ - plotH * 0.5 / pxPerBlock - marginBlocks;
        double viewMaxZ = viewWorldZ + plotH * 0.5 / pxPerBlock + marginBlocks;

        double visibleArcPx = 0.0;
        for (int i = 0; i < SEED_SEGMENTS; i++) {
            double t0 = period * i / SEED_SEGMENTS;
            double t1 = period * (i + 1) / SEED_SEGMENTS;
            StormFourierPath.Vec2 p0 = StormFourierPath.position(circles, t0);
            StormFourierPath.Vec2 p1 = StormFourierPath.position(circles, t1);
            StormFourierPath.Vec2 pm = StormFourierPath.position(circles, 0.5 * (t0 + t1));
            if (!segmentNearViewWorld(p0, p1, pm, viewMinX, viewMaxX, viewMinZ, viewMaxZ)) {
                continue;
            }
            double sx0 = (pathOriginX + p0.x() - viewWorldX) * pxPerBlock;
            double sz0 = (pathOriginZ + p0.z() - viewWorldZ) * pxPerBlock;
            double sx1 = (pathOriginX + p1.x() - viewWorldX) * pxPerBlock;
            double sz1 = (pathOriginZ + p1.z() - viewWorldZ) * pxPerBlock;
            visibleArcPx += Math.hypot(sx1 - sx0, sz1 - sz0);
        }
        double estArcPx = Math.max(visibleArcPx * 1.35, 1.0);
        double maxSegPx = Mth.clamp(estArcPx / TARGET_PATH_SAMPLES, 2.5, 48.0);
        double pixelTol = Mth.clamp(maxSegPx * 0.12, 0.8, 6.0);

        StormFourierPath.Vec2 pStart = StormFourierPath.position(circles, 0.0);
        out.add(new Sample(0.0, pStart.x(), pStart.z()));

        for (int i = 0; i < SEED_SEGMENTS; i++) {
            double t0 = period * i / SEED_SEGMENTS;
            double t1 = period * (i + 1) / SEED_SEGMENTS;
            StormFourierPath.Vec2 p0 = StormFourierPath.position(circles, t0);
            StormFourierPath.Vec2 p1 = StormFourierPath.position(circles, t1);
            StormFourierPath.Vec2 pm = StormFourierPath.position(circles, 0.5 * (t0 + t1));

            if (!segmentNearViewWorld(p0, p1, pm, viewMinX, viewMaxX, viewMinZ, viewMaxZ)) {
                out.add(new Sample(t1, p1.x(), p1.z()));
                pointBudget[0]--;
                continue;
            }

            refine(
                    t0, t1, p0, p1, 0, out, pointBudget,
                    plotLeft, plotTop, plotW, plotH,
                    viewMinX, viewMaxX, viewMinZ, viewMaxZ,
                    maxSegPx, pixelTol
            );
        }

        int n = out.size();
        double[] t = new double[n];
        double[] x = new double[n];
        double[] z = new double[n];
        int[] segColor = new int[Math.max(0, n - 1)];
        for (int i = 0; i < n; i++) {
            Sample s = out.get(i);
            t[i] = s.t;
            x[i] = s.x;
            z[i] = s.z;
            if (i > 0) {
                segColor[i - 1] = speedFractionToColor(averageSpeed(t[i - 1], t[i]));
            }
        }
        return new AdaptiveStroke(t, x, z, segColor, n);
    }

    private void refine(
            double t0, double t1,
            StormFourierPath.Vec2 p0, StormFourierPath.Vec2 p1,
            int depth,
            List<Sample> out,
            int[] pointBudget,
            int plotLeft, int plotTop, int plotW, int plotH,
            double viewMinX, double viewMaxX, double viewMinZ, double viewMaxZ,
            double maxSegPx,
            double pixelTol
    ) {
        if (pointBudget[0] <= 0) {
            out.add(new Sample(t1, p1.x(), p1.z()));
            return;
        }

        double tm = 0.5 * (t0 + t1);
        StormFourierPath.Vec2 pm = StormFourierPath.position(circles, tm);

        if (!segmentNearViewWorld(p0, p1, pm, viewMinX, viewMaxX, viewMinZ, viewMaxZ)) {
            out.add(new Sample(t1, p1.x(), p1.z()));
            pointBudget[0]--;
            return;
        }

        double sx0 = localToScreenX(p0.x(), plotLeft, plotW);
        double sz0 = localToScreenZ(p0.z(), plotTop, plotH);
        double sx1 = localToScreenX(p1.x(), plotLeft, plotW);
        double sz1 = localToScreenZ(p1.z(), plotTop, plotH);
        double sxm = localToScreenX(pm.x(), plotLeft, plotW);
        double szm = localToScreenZ(pm.z(), plotTop, plotH);

        double segPx = Math.hypot(sx1 - sx0, sz1 - sz0);
        double errPx = pointToSegmentDistance(sxm, szm, sx0, sz0, sx1, sz1);

        int maxDepth = 14;

        if (depth >= maxDepth || (errPx <= pixelTol && segPx <= maxSegPx)) {
            out.add(new Sample(t1, p1.x(), p1.z()));
            pointBudget[0]--;
            return;
        }

        refine(t0, tm, p0, pm, depth + 1, out, pointBudget,
                plotLeft, plotTop, plotW, plotH,
                viewMinX, viewMaxX, viewMinZ, viewMaxZ, maxSegPx, pixelTol);
        refine(tm, t1, pm, p1, depth + 1, out, pointBudget,
                plotLeft, plotTop, plotW, plotH,
                viewMinX, viewMaxX, viewMinZ, viewMaxZ, maxSegPx, pixelTol);
    }

    private static double pointToSegmentDistance(
            double px, double py, double x0, double y0, double x1, double y1
    ) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-12) {
            return Math.hypot(px - x0, py - y0);
        }
        double t = Mth.clamp(((px - x0) * dx + (py - y0) * dy) / lenSq, 0.0, 1.0);
        return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy));
    }

    /** Mean speed-curve value over path-parameter interval (anti-aliases far zooms). */
    private float averageSpeed(double t0, double t1) {
        double period = StormFourierPath.fundamentalPeriod();
        double a = positiveMod(t0, period) / period;
        double b = positiveMod(t1, period) / period;
        if (b < a) {
            b += 1.0;
        }
        double span = b - a;
        if (span < 1e-9) {
            return sampleSpeedLut(a);
        }
        // Integrate LUT over [a,b] in normalized parameter units
        int steps = Math.max(2, (int) Math.ceil(span * LUT_SAMPLES));
        steps = Math.min(steps, 256);
        double sum = 0.0;
        for (int i = 0; i <= steps; i++) {
            double u = a + span * i / steps;
            sum += sampleSpeedLut(u - Math.floor(u));
        }
        return (float) (sum / (steps + 1));
    }

    private float sampleSpeedLut(double u01) {
        double idx = Mth.clamp(u01, 0.0, 1.0) * LUT_SAMPLES;
        int i0 = Mth.clamp((int) Math.floor(idx), 0, LUT_SAMPLES - 1);
        int i1 = Math.min(LUT_SAMPLES, i0 + 1);
        float frac = (float) (idx - i0);
        return Mth.lerp(frac, meta.speedLut[i0], meta.speedLut[i1]);
    }

    private double timeAtT(double t) {
        double period = StormFourierPath.fundamentalPeriod();
        double u = positiveMod(t, period) / period;
        double idx = u * LUT_SAMPLES;
        int i0 = Mth.clamp((int) Math.floor(idx), 0, LUT_SAMPLES - 1);
        int i1 = Math.min(LUT_SAMPLES, i0 + 1);
        float frac = (float) (idx - i0);
        return Mth.lerp(frac, meta.timeLut[i0], meta.timeLut[i1]);
    }

    private void drawAdaptivePath(
            GuiGraphics graphics,
            AdaptiveStroke s,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (s.points < 2) {
            return;
        }
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer quads = graphics.bufferSource().getBuffer(RenderType.gui());

        float prevX = localToScreenXf(s.x[0], plotLeft, plotW);
        float prevZ = localToScreenZf(s.z[0], plotTop, plotH);
        for (int i = 1; i < s.points; i++) {
            float sx = localToScreenXf(s.x[i], plotLeft, plotW);
            float sz = localToScreenZf(s.z[i], plotTop, plotH);
            if (segmentTouchesPlot(
                    (int) prevX, (int) prevZ, (int) sx, (int) sz,
                    plotLeft, plotTop, plotW, plotH
            )) {
                appendThickSegment(quads, matrix, prevX, prevZ, sx, sz, 1.6F, PATH_OUTLINE);
                appendThickSegment(quads, matrix, prevX, prevZ, sx, sz, 1.05F, s.segColor[i - 1]);
            }
            prevX = sx;
            prevZ = sz;
        }
        graphics.flush();
    }

    /** Thick segment as a screen-space quad (Y-down GUI winding). */
    private static void appendThickSegment(
            VertexConsumer buffer,
            Matrix4f matrix,
            float x0, float y0, float x1, float y1,
            float halfWidth,
            int argb
    ) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4F) {
            return;
        }
        float nx = -dy / len * halfWidth;
        float ny = dx / len * halfWidth;
        // GuiGraphics fill() uses packed ARGB ints on the consumer.
        buffer.addVertex(matrix, x0 + nx, y0 + ny, 0).setColor(argb);
        buffer.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(argb);
        buffer.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(argb);
        buffer.addVertex(matrix, x0 - nx, y0 - ny, 0).setColor(argb);
    }

    private static float worldToScreenXf(double worldX, int plotLeft, int plotW, double viewCx, double scale) {
        return (float) (plotLeft + plotW * 0.5 + (worldX - viewCx) * scale);
    }

    private static float worldToScreenZf(double worldZ, int plotTop, int plotH, double viewCz, double scale) {
        return (float) (plotTop + plotH * 0.5 + (worldZ - viewCz) * scale);
    }

    private static boolean segmentTouchesPlot(
            int x0, int y0, int x1, int y1,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        int minX = Math.min(x0, x1);
        int maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1);
        int maxY = Math.max(y0, y1);
        return maxX >= plotLeft && minX < plotLeft + plotW
                && maxY >= plotTop && minY < plotTop + plotH;
    }

    private HoverInfo findHover(
            AdaptiveStroke s,
            double mouseX, double mouseY,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (s.points < 2) {
            return null;
        }
        double bestDist = 14.0;
        int bestI = -1;
        double bestT = 0;
        double bestLocalX = 0;
        double bestLocalZ = 0;
        for (int i = 1; i < s.points; i++) {
            double sx0 = localToScreenX(s.x[i - 1], plotLeft, plotW);
            double sz0 = localToScreenZ(s.z[i - 1], plotTop, plotH);
            double sx1 = localToScreenX(s.x[i], plotLeft, plotW);
            double sz1 = localToScreenZ(s.z[i], plotTop, plotH);
            double dist = pointToSegmentDistance(mouseX, mouseY, sx0, sz0, sx1, sz1);
            if (dist < bestDist) {
                bestDist = dist;
                bestI = i;
                // Project mouse onto the drawn segment (matches the polyline on screen).
                double dx = sx1 - sx0;
                double dy = sz1 - sz0;
                double lenSq = dx * dx + dy * dy;
                double u = lenSq < 1e-12 ? 0.0 : Mth.clamp(((mouseX - sx0) * dx + (mouseY - sz0) * dy) / lenSq, 0.0, 1.0);
                bestT = Mth.lerp(u, s.t[i - 1], s.t[i]);
                bestLocalX = Mth.lerp(u, s.x[i - 1], s.x[i]);
                bestLocalZ = Mth.lerp(u, s.z[i - 1], s.z[i]);
            }
        }
        if (bestI < 0) {
            return null;
        }
        double ticksFromMarker = etaTicksFromMarker(bestT);
        float speedNow = averageSpeed(bestT, bestT);
        return new HoverInfo(
                bestLocalX,
                bestLocalZ,
                toWorldX(bestLocalX),
                toWorldZ(bestLocalZ),
                bestT,
                ticksFromMarker,
                speedNow * peakSpeed,
                speedNow
        );
    }

    /** Travel time from the storm marker to {@code t}, wrapping one loop if needed. */
    private double etaTicksFromMarker(double t) {
        if (meta == null) {
            return 0.0;
        }
        double period = StormFourierPath.fundamentalPeriod();
        double from = timeAtT(markerPathParam);
        double to = timeAtT(t);
        double eta = to - from;
        // If target is "behind" on the loop parameter, go the long way once.
        double markerU = positiveMod(markerPathParam, period);
        double targetU = positiveMod(t, period);
        if (targetU < markerU - 1e-12) {
            eta = (meta.lapTicks - from) + to;
        } else if (eta < 0.0) {
            eta += meta.lapTicks;
        }
        return Math.max(0.0, eta);
    }

    private void drawHoverTooltip(
            GuiGraphics graphics,
            int mouseX, int mouseY,
            int plotLeft, int plotTop, int plotRight, int plotBottom,
            HoverInfo hover,
            DwellSample dwellHover
    ) {
        List<String> lines = new ArrayList<>();
        if (hover != null) {
            lines.add(String.format(
                    "%s %.0f, %.0f",
                    showingLivePath() ? "world" : "local",
                    hover.displayX,
                    hover.displayZ
            ));
            lines.add(String.format("path t=%.4f", hover.t));
            lines.add(String.format("ETA %s", formatDuration(hover.ticksFromStart)));
            lines.add(String.format("speed %.4f (curve %.3f)", hover.instSpeed, hover.curve));
        }
        if (dwellHover != null) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add(String.format(
                    "%s %.0f, %.0f",
                    showingLivePath() ? "world" : "local",
                    dwellHover.displayX,
                    dwellHover.displayZ
            ));
            lines.add(String.format("dwell %s / lap", formatDuration(dwellHover.dwellTicks)));
        }
        if (lines.isEmpty()) {
            return;
        }
        int boxW = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                boxW = Math.max(boxW, font.width(line));
            }
        }
        boxW += 10;
        int boxH = lines.size() * 11 + 8;
        int tx = mouseX + 14;
        int ty = mouseY + 14;
        if (tx + boxW > plotRight - 2) {
            tx = mouseX - boxW - 8;
        }
        if (ty + boxH > plotBottom - 2) {
            ty = mouseY - boxH - 8;
        }
        tx = Math.max(plotLeft + 2, tx);
        ty = Math.max(plotTop + 2, ty);

        graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xE0101419);
        graphics.renderOutline(tx, ty, boxW, boxH, 0xFF8B9AAB);
        int y = ty + 5;
        for (String line : lines) {
            if (!line.isEmpty()) {
                graphics.drawString(font, line, tx + 5, y, 0xFFE8EEF4, false);
            }
            y += 11;
        }
    }

    private static String formatDuration(double ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 60.0) {
            return String.format("%.1fs", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 60.0) {
            return String.format("%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        if (hours < 48.0) {
            return String.format("%.2f h", hours);
        }
        return String.format("%.2f d", hours / 24.0);
    }

    private void drawScaleBar(
            GuiGraphics graphics,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        if (pxPerBlock <= 1e-12) {
            return;
        }
        double targetPx = Math.min(120.0, plotW * 0.28);
        double blocks = niceGrid(targetPx / pxPerBlock);
        int barPx = Math.max(8, Math.min(plotW - 24, (int) Math.round(blocks * pxPerBlock)));
        int x0 = plotLeft + 10;
        int y0 = plotTop + plotH - 18;
        int x1 = x0 + barPx;
        graphics.fill(x0, y0, x1 + 1, y0 + 2, 0xFFE8EEF4);
        graphics.fill(x0, y0 - 4, x0 + 2, y0 + 6, 0xFFE8EEF4);
        graphics.fill(x1 - 1, y0 - 4, x1 + 1, y0 + 6, 0xFFE8EEF4);
        graphics.drawString(font, formatBlocks(blocks), x0, y0 - 12, 0xFFE8EEF4, true);
    }

    private static String formatBlocks(double blocks) {
        if (blocks >= 1_000_000.0) {
            double m = blocks / 1_000_000.0;
            return (Math.abs(m - Math.rint(m)) < 1e-6)
                    ? String.format("%.0fM blocks", m)
                    : String.format("%.1fM blocks", m);
        }
        if (blocks >= 1_000.0) {
            double k = blocks / 1_000.0;
            return (Math.abs(k - Math.rint(k)) < 1e-6)
                    ? String.format("%.0fk blocks", k)
                    : String.format("%.1fk blocks", k);
        }
        return String.format("%.0f blocks", blocks);
    }

    private void drawSidebar(GuiGraphics graphics, int left, int sampleCount, HoverInfo hover, DwellSample dwellHover) {
        graphics.drawString(font, "Storm Path Preview", left, PAD + 4, 0xFFE8EEF4, false);

        int scrollTop = sidebarScrollTop();
        int scrollBottom = sidebarScrollBottom();
        int contentOriginY = scrollTop;

        // Measure content height without clipping, then draw clipped + scrolled.
        int contentEndY = measureSidebarStats(left, contentOriginY, sampleCount, hover, dwellHover);
        sidebarContentHeight = Math.max(0, contentEndY - contentOriginY);
        clampSidebarScroll();

        if (scrollBottom <= scrollTop) {
            return;
        }

        graphics.enableScissor(panelLeft() + 1, scrollTop, panelRight() - 1, scrollBottom);
        try {
            drawSidebarStats(graphics, left, contentOriginY - sidebarScroll, sampleCount, hover, dwellHover);
            drawSidebarScrollBar(graphics, scrollTop, scrollBottom);
        } finally {
            graphics.disableScissor();
        }
    }

    /** Returns the Y just past the last stats line (unscrolled document coords). */
    private int measureSidebarStats(int left, int y, int sampleCount, HoverInfo hover, DwellSample dwellHover) {
        return drawSidebarStats(null, left, y, sampleCount, hover, dwellHover);
    }

    private int drawSidebarStats(
            GuiGraphics graphics,
            int left,
            int y,
            int sampleCount,
            HoverInfo hover,
            DwellSample dwellHover
    ) {
        if (!fieldError.isEmpty()) {
            if (graphics != null) {
                graphics.drawString(font, fieldError, left, y, 0xFFFF6666, false);
            }
            y += 12;
        }
        if (meta != null) {
            double bw = meta.bounds.maxX() - meta.bounds.minX();
            double bd = meta.bounds.maxZ() - meta.bounds.minZ();
            y = stat(graphics, left, y, "loop len", formatBlocks(meta.loopLength));
            y = stat(graphics, left, y, "width X", formatBlocks(bw));
            y = stat(graphics, left, y, "depth Z", formatBlocks(bd));
            y += 4;
            // Mean uses cosine² average 0.375 — stable when you tweak pulse length.
            y = stat(graphics, left, y, "lap mean", formatDuration(meta.meanLapTicks));
            // Simulated from starting phase; varies with pulse alignment.
            y = stat(graphics, left, y, "lap sim", formatDuration(meta.lapTicks));
            y = stat(graphics, left, y, "pulse", String.format("%.2f min", phaseMinutes()));
            y = stat(graphics, left, y, "phase", String.format(
                    "%.3f (%.2f)",
                    StormFourierPath.offsetTurnsFromSpeedPhase(
                            StormFourierPath.speedPhaseFromOffsetTurns(speedPhaseOffsetTurns)
                    ),
                    StormFourierPath.speedScale(
                            StormFourierPath.speedPhaseFromOffsetTurns(speedPhaseOffsetTurns)
                    )
            ));
        }
        StormFourierPath.Vec2 markerLocal = circles.length > 0
                ? StormFourierPath.position(circles, markerPathParam)
                : new StormFourierPath.Vec2(0, 0);
        y = stat(graphics, left, y, showingLivePath() ? "storm" : "marker", String.format(
                "%.0f, %.0f",
                toWorldX(markerLocal.x()),
                toWorldZ(markerLocal.z())
        ));
        y = stat(graphics, left, y, "path t", String.format("%.3f", markerPathParam));
        y = stat(graphics, left, y, "eye r", formatBlocks(eyeRadius));
        int shTotal = Math.min(strongholdX.length, strongholdZ.length);
        y = stat(graphics, left, y, "strongholds", String.format(
                "%d / %d", strongholdsCoveredCount, shTotal
        ));
        y = stat(graphics, left, y, "zoom", String.format("%.2fx", zoomFactor()));
        y = stat(graphics, left, y, "samples", Integer.toString(sampleCount));
        y += 4;
        y = stat(graphics, left, y, "coverage", coverageMode == CoverageMode.UNION ? "union" : "dwell");
        if (coverageMode == CoverageMode.DWELL && dwellGrid != null && dwellGrid.max > 0.0) {
            y = stat(graphics, left, y, "dwell max", formatDuration(dwellGrid.max));
            y = stat(graphics, left, y, "dwell mean", formatDuration(dwellGrid.mean));
        }
        y += 2;
        if (graphics != null) {
            graphics.drawString(font, "Speed (seg avg)", left, y, 0xFF8B9AAB, false);
        }
        y += 11;
        y = drawLegend(graphics, left, y);
        y += 2;
        if (hover != null) {
            if (graphics != null) {
                graphics.drawString(font, "Hover (click = start)", left, y, 0xFFFFE08A, false);
            }
            y += 11;
            y = stat(graphics, left, y, "pos", String.format("%.0f, %.0f", hover.displayX, hover.displayZ));
            y = stat(graphics, left, y, "ETA", formatDuration(hover.ticksFromStart));
            y = stat(graphics, left, y, "speed", String.format("%.4f", hover.instSpeed));
            if (coverageMode == CoverageMode.DWELL && dwellHover != null) {
                y = stat(graphics, left, y, "dwell", formatDuration(dwellHover.dwellTicks));
            }
        } else if (coverageMode == CoverageMode.DWELL && dwellHover != null) {
            if (graphics != null) {
                graphics.drawString(font, "Cursor dwell", left, y, 0xFFFFE08A, false);
            }
            y += 11;
            y = stat(graphics, left, y, "pos", String.format("%.0f, %.0f", dwellHover.displayX, dwellHover.displayZ));
            y = stat(graphics, left, y, "dwell", formatDuration(dwellHover.dwellTicks));
        } else {
            if (graphics != null) {
                graphics.drawString(font, "Hover path for ETA", left, y, 0xFF8B9AAB, false);
            }
            y += 11;
            if (graphics != null) {
                graphics.drawString(font, "Click = set apply start", left, y, 0xFF8B9AAB, false);
            }
            y += 11;
        }
        y += 4;
        if (graphics != null) {
            graphics.drawString(font, "White = eye radius stroke", left, y, 0xFF8B9AAB, false);
        }
        y += 11;
        if (graphics != null) {
            String shHint = coverageMode == CoverageMode.DWELL
                    ? "Heat = dwell / lap (slow = hot)"
                    : "SH green = covered";
            graphics.drawString(font, shHint, left, y, 0xFF8B9AAB, false);
        }
        y += 11;
        return y;
    }

    private void drawSidebarScrollBar(GuiGraphics graphics, int scrollTop, int scrollBottom) {
        int maxScroll = maxSidebarScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackX = panelRight() - 5;
        int trackH = scrollBottom - scrollTop;
        int thumbH = Math.max(16, (int) (trackH * (trackH / (double) (trackH + maxScroll))));
        int thumbY = scrollTop + (int) ((trackH - thumbH) * (sidebarScroll / (double) maxScroll));
        graphics.fill(trackX, scrollTop, trackX + 3, scrollBottom, 0x66000000);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF8B9AAB);
    }

    private int drawLegend(GuiGraphics graphics, int left, int y) {
        int barW = contentWidth();
        if (graphics != null) {
            for (int i = 0; i < barW; i++) {
                float t = i / (float) Math.max(1, barW - 1);
                graphics.fill(left + i, y, left + i + 1, y + 8, speedFractionToColor(t));
            }
            y += 10;
            graphics.drawString(font, "0", left, y, 0xFF8B9AAB, false);
            graphics.drawString(font, "peak", left + barW - font.width("peak"), y, 0xFF8B9AAB, false);
        } else {
            y += 10;
        }
        return y + 12;
    }

    private int stat(GuiGraphics graphics, int left, int y, String key, String value) {
        if (graphics != null) {
            graphics.drawString(font, key, left, y, 0xFF8B9AAB, false);
            graphics.drawString(font, value, left + 78, y, 0xFFE8EEF4, false);
        }
        return y + 11;
    }

    private void drawGrid(
            GuiGraphics graphics,
            int plotLeft, int plotTop, int plotW, int plotH
    ) {
        double worldPerPixel = 1.0 / pxPerBlock;
        double spacing = niceGrid(90.0 * worldPerPixel);
        double startX = Math.floor((viewWorldX - plotW * 0.5 * worldPerPixel) / spacing) * spacing;
        double endX = viewWorldX + plotW * 0.5 * worldPerPixel;
        double startZ = Math.floor((viewWorldZ - plotH * 0.5 * worldPerPixel) / spacing) * spacing;
        double endZ = viewWorldZ + plotH * 0.5 * worldPerPixel;
        for (double x = startX; x <= endX; x += spacing) {
            int sx = worldToScreenX(x, plotLeft, plotW, viewWorldX, pxPerBlock);
            graphics.fill(sx, plotTop, sx + 1, plotTop + plotH, GRID_COLOR);
        }
        for (double z = startZ; z <= endZ; z += spacing) {
            int sz = worldToScreenZ(z, plotTop, plotH, viewWorldZ, pxPerBlock);
            graphics.fill(plotLeft, sz, plotLeft + plotW, sz + 1, GRID_COLOR);
        }
    }

    private static int speedFractionToColor(float fraction) {
        float t = Mth.clamp(fraction, 0.0F, 1.0F);
        float r, g, b;
        if (t < 0.5F) {
            float u = t / 0.5F;
            r = 0.15F;
            g = Mth.lerp(u, 0.35F, 0.85F);
            b = Mth.lerp(u, 0.95F, 0.35F);
        } else {
            float u = (t - 0.5F) / 0.5F;
            r = Mth.lerp(u, 0.15F, 1.0F);
            g = Mth.lerp(u, 0.85F, 0.25F);
            b = Mth.lerp(u, 0.35F, 0.15F);
        }
        return 0xFF000000
                | (Mth.clamp((int) (r * 255), 0, 255) << 16)
                | (Mth.clamp((int) (g * 255), 0, 255) << 8)
                | Mth.clamp((int) (b * 255), 0, 255);
    }

    private static int worldToScreenX(double worldX, int plotLeft, int plotW, double viewCx, double scale) {
        return plotLeft + (int) Math.round(plotW * 0.5 + (worldX - viewCx) * scale);
    }

    private static int worldToScreenZ(double worldZ, int plotTop, int plotH, double viewCz, double scale) {
        return plotTop + (int) Math.round(plotH * 0.5 + (worldZ - viewCz) * scale);
    }

    private static double positiveMod(double value, double mod) {
        double r = value % mod;
        return r < 0.0 ? r + mod : r;
    }

    private static double niceGrid(double approx) {
        double pow = Math.pow(10.0, Math.floor(Math.log10(Math.max(approx, 1e-6))));
        double n = approx / pow;
        double m = n >= 5 ? 5 : (n >= 2 ? 2 : 1);
        return m * pow;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / Numpad Enter
            if (applyFieldsFromBoxes()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft()) {
            if (maxSidebarScroll() > 0) {
                sidebarScroll = Mth.clamp(sidebarScroll - (int) Math.round(scrollY * 12), 0, maxSidebarScroll());
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double minScale = initialPxPerBlock > 0.0 ? initialPxPerBlock * 0.05 : 1e-6;
        double maxScale = initialPxPerBlock > 0.0 ? initialPxPerBlock * 64.0 : 1e6;
        pxPerBlock = scrollY > 0
                ? Math.min(maxScale, pxPerBlock * 1.15)
                : Math.max(minScale, pxPerBlock / 1.15);
        strokeKey = Long.MIN_VALUE;
        dwellGridKey = Long.MIN_VALUE;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX < panelLeft()) {
            plotPressPending = true;
            plotPressX = mouseX;
            plotPressY = mouseY;
            dragging = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (plotPressPending && !dragging) {
                // Click (no pan): set apply start from path hover under the cursor.
                if (lastHover != null
                        && Math.hypot(mouseX - plotPressX, mouseY - plotPressY) <= 6.0) {
                    setApplyStartFromHover(lastHover);
                }
            }
            plotPressPending = false;
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mouseX < panelLeft() && meta != null) {
            if (plotPressPending && !dragging) {
                if (Math.hypot(mouseX - plotPressX, mouseY - plotPressY) > 4.0) {
                    dragging = true;
                    plotPressPending = false;
                }
            }
            if (dragging) {
                viewWorldX -= (mouseX - lastMouseX) / pxPerBlock;
                viewWorldZ -= (mouseY - lastMouseY) / pxPerBlock;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                strokeKey = Long.MIN_VALUE;
                dwellGridKey = Long.MIN_VALUE;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Set apply-start to the hovered path parameter while keeping the storm fixed in
     * world/screen space (path slides under it). Same for live and candidate geometry.
     */
    private void setApplyStartFromHover(HoverInfo hover) {
        if (circles.length == 0) {
            return;
        }
        markerPathParam = hover.t;
        StormFourierPath.Vec2 local = StormFourierPath.position(circles, markerPathParam);
        pathOriginX = stormWorldX - local.x();
        pathOriginZ = stormWorldZ - local.z();
        recomputeStrongholdCoverage();
        strokeKey = Long.MIN_VALUE;
        dwellGridKey = Long.MIN_VALUE;
        fieldError = "";
    }

    @Override
    public void removed() {
        if (eyeCoverageTarget != null) {
            eyeCoverageTarget.destroyBuffers();
            eyeCoverageTarget = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {
        double centerX() {
            return (minX + maxX) * 0.5;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5;
        }

        double span() {
            return Math.max(maxX - minX, maxZ - minZ);
        }
    }

    private record PathMeta(
            Bounds bounds,
            double loopLength,
            float[] speedLut,
            double[] timeLut,
            double lapTicks,
            double meanLapTicks
    ) {}

    private record AdaptiveStroke(double[] t, double[] x, double[] z, int[] segColor, int points) {}

    private record Sample(double t, double x, double z) {}

    private record HoverInfo(
            double localX,
            double localZ,
            double displayX,
            double displayZ,
            double t,
            double ticksFromStart,
            double instSpeed,
            float curve
    ) {}

    private record DwellGrid(
            double originX,
            double originZ,
            double cellSize,
            int cols,
            int rows,
            float[] data,
            double max,
            double mean
    ) {
        double sampleWorld(double worldX, double worldZ) {
            int ix = (int) Math.floor((worldX - originX) / cellSize);
            int iz = (int) Math.floor((worldZ - originZ) / cellSize);
            if (ix < 0 || ix >= cols || iz < 0 || iz >= rows) {
                return 0.0;
            }
            return data[ix + iz * cols];
        }
    }

    private record DwellSample(
            double localX,
            double localZ,
            double displayX,
            double displayZ,
            double dwellTicks
    ) {}
}
