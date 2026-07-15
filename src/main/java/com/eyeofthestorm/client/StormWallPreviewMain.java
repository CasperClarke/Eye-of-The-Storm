package com.eyeofthestorm.client;

import com.eyeofthestorm.StormConfig;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Offline baker using the same Voronoi code as the game.
 * Writes the runtime atlas asset plus preview GIF/PNGs.
 *
 * <pre>
 *   gradlew.bat previewStormWall
 *   gradlew.bat previewStormWall --args="--size 512 --slices 64 --frames 64"
 * </pre>
 */
public final class StormWallPreviewMain {
    private static final Path DEFAULT_ASSET =
            Path.of("src", "main", "resources", "assets", "eyeofthestorm", "textures", "storm", "wall_morph_atlas.png");

    private StormWallPreviewMain() {}

    public static void main(String[] args) throws IOException {
        int size = StormConfig.wallTextureSize;
        int slices = Math.max(2, StormConfig.wallVoronoiMorphSliceCount);
        int frames = slices; // even W steps when frames == slices
        int frameMs = 80;
        Path outDir = Path.of("tools", "output");
        Path assetPath = DEFAULT_ASSET;
        boolean writeAsset = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--size" -> size = Integer.parseInt(args[++i]);
                case "--slices" -> slices = Math.max(2, Integer.parseInt(args[++i]));
                case "--frames" -> frames = Math.max(2, Integer.parseInt(args[++i]));
                case "--frame-ms" -> frameMs = Math.max(16, Integer.parseInt(args[++i]));
                case "--out" -> outDir = Path.of(args[++i]);
                case "--asset" -> assetPath = Path.of(args[++i]);
                case "--no-asset" -> writeAsset = false;
                default -> System.err.println("Unknown arg: " + args[i]);
            }
        }

        Files.createDirectories(outDir);
        long t0 = System.nanoTime();

        float periodErr = StormWallBake.maxPeriodError(Math.min(size, 128), 0.0f);
        System.out.printf(Locale.ROOT, "Unit-cube period error (U/V/W f(0)-f(1)): %.6e%n", periodErr);

        List<float[][]> alphas = new ArrayList<>(slices);
        List<BufferedImage> previewKeyframes = new ArrayList<>(slices);
        for (int s = 0; s < slices; s++) {
            float w = s / (float) slices;
            float[][] alpha = StormWallBake.bakeAlpha(size, w);
            alphas.add(alpha);
            previewKeyframes.add(toPreviewImage(alpha, size));
        }

        if (writeAsset) {
            Files.createDirectories(assetPath.getParent());
            BufferedImage atlas = buildGameAtlas(alphas, size);
            ImageIO.write(atlas, "PNG", assetPath.toFile());
            System.out.println("Wrote game atlas " + assetPath.toAbsolutePath()
                    + " (" + size + "x" + (size * slices) + ", " + slices + " slices)");
            System.out.println("Set StormConfig.wallVoronoiMorphSliceCount = " + slices);
        }

        Path stripPath = outDir.resolve("storm_wall_w_strip.png");
        writeStrip(previewKeyframes, stripPath);
        System.out.println("Wrote " + stripPath.toAbsolutePath());

        Path tilePath = outDir.resolve("storm_wall_w0_tiled.png");
        ImageIO.write(tilePreview(previewKeyframes.get(0), 3), "PNG", tilePath.toFile());
        System.out.println("Wrote " + tilePath.toAbsolutePath());

        List<BufferedImage> anim = new ArrayList<>(frames);
        for (int f = 0; f < frames; f++) {
            float phase = f / (float) frames;
            float fIndex = phase * slices;
            int i0 = Math.floorMod((int) Math.floor(fIndex), slices);
            int i1 = Math.floorMod(i0 + 1, slices);
            float blend = fIndex - (float) Math.floor(fIndex);
            anim.add(blendImages(previewKeyframes.get(i0), previewKeyframes.get(i1), blend));
        }

        Path gifPath = outDir.resolve("storm_wall_morph_loop.gif");
        writeAnimatedGif(anim, gifPath, frameMs);
        System.out.println("Wrote " + gifPath.toAbsolutePath() + " (" + frames + " frames)");

        double ms = (System.nanoTime() - t0) / 1_000_000.0;
        System.out.printf(Locale.ROOT, "Done in %.0f ms (size=%d slices=%d frames=%d)%n", ms, size, slices, frames);
    }

    /** White RGB + density alpha, slices stacked in V — matches in-game sampler format. */
    private static BufferedImage buildGameAtlas(List<float[][]> alphas, int size) {
        int slices = alphas.size();
        BufferedImage atlas = new BufferedImage(size, size * slices, BufferedImage.TYPE_INT_ARGB);
        for (int s = 0; s < slices; s++) {
            float[][] alpha = alphas.get(s);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int a = StormWallBake.toByte(alpha[x][y]);
                    int argb = (a << 24) | 0x00FFFFFF;
                    atlas.setRGB(x, s * size + y, argb);
                }
            }
        }
        return atlas;
    }

    private static BufferedImage toPreviewImage(float[][] alpha, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int tr = StormConfig.wallColorR;
        int tg = StormConfig.wallColorG;
        int tb = StormConfig.wallColorB;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int a = StormWallBake.toByte(alpha[x][y]);
                float af = a / 255.0f;
                int r = Math.round(tr * af);
                int g = Math.round(tg * af);
                int b = Math.round(tb * af);
                int argb = (255 << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private static BufferedImage blendImages(BufferedImage a, BufferedImage b, float t) {
        int w = a.getWidth();
        int h = a.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float u = 1.0f - t;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                int r = Math.round(((ca >> 16) & 255) * u + ((cb >> 16) & 255) * t);
                int g = Math.round(((ca >> 8) & 255) * u + ((cb >> 8) & 255) * t);
                int bl = Math.round((ca & 255) * u + (cb & 255) * t);
                out.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | bl);
            }
        }
        return out;
    }

    private static BufferedImage tilePreview(BufferedImage src, int reps) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w * reps, h * reps, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(12, 12, 16));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        for (int ty = 0; ty < reps; ty++) {
            for (int tx = 0; tx < reps; tx++) {
                g.drawImage(src, tx * w, ty * h, null);
            }
        }
        g.dispose();
        return out;
    }

    private static void writeStrip(List<BufferedImage> frames, Path path) throws IOException {
        int size = frames.get(0).getWidth();
        BufferedImage strip = new BufferedImage(size * frames.size(), size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = strip.createGraphics();
        for (int i = 0; i < frames.size(); i++) {
            g.drawImage(frames.get(i), i * size, 0, null);
        }
        g.dispose();
        ImageIO.write(strip, "PNG", path.toFile());
    }

    private static void writeAnimatedGif(List<BufferedImage> frames, Path path, int delayCs) throws IOException {
        int delay = Math.max(2, delayCs / 10);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
        if (!writers.hasNext()) {
            throw new IOException("No GIF ImageWriter available");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                ImageWriteParam param = writer.getDefaultWriteParam();
                IIOMetadata meta = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromBufferedImageType(frame.getType()), param);
                String metaFormat = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(metaFormat);

                IIOMetadataNode graphicsControl = getNode(root, "GraphicControlExtension");
                graphicsControl.setAttribute("disposalMethod", "none");
                graphicsControl.setAttribute("userInputFlag", "FALSE");
                graphicsControl.setAttribute("transparentColorFlag", "FALSE");
                graphicsControl.setAttribute("delayTime", Integer.toString(delay));
                graphicsControl.setAttribute("transparentColorIndex", "0");

                if (i == 0) {
                    IIOMetadataNode appExtensions = getNode(root, "ApplicationExtensions");
                    IIOMetadataNode appNode = new IIOMetadataNode("ApplicationExtension");
                    appNode.setAttribute("applicationID", "NETSCAPE");
                    appNode.setAttribute("authenticationCode", "2.0");
                    appNode.setUserObject(new byte[] {0x1, 0x0, 0x0});
                    appExtensions.appendChild(appNode);
                }

                meta.setFromTree(metaFormat, root);
                writer.writeToSequence(new IIOImage(frame, null, meta), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
