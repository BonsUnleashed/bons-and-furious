package bons.furious.patch.ftbchunks_c49;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch ftbchunks_region_write_direct (FTB Chunks 2001.3.8, client). Ours; no FTB Chunks code is carried.
 *
 * What FTB Chunks does. MapRegionData.write() copies a 512 x 512 map region into five fresh BufferedImages (the data
 * image TYPE_INT_ARGB, foliage / grass / water / blocks TYPE_INT_RGB) with 1,310,720 BufferedImage.setRGB calls on the
 * render thread (each one a DirectColorModel.getDataElements, an int[1] and a raster store), then hands the images to its
 * map thread, which encodes them as PNG into the region's zip file. It runs at every 60 s autosave, when the pause menu
 * opens, when a stale region is released and for every modified region at logout.
 *
 * What the switch does. Each of the five setRGB calls is redirected to one handler per call site, which stores the pixel
 * straight into that image's int array: rgb unchanged for the TYPE_INT_ARGB image, rgb & 0xFFFFFF for the TYPE_INT_RGB
 * images. FTB Chunks' lock, chunk list, early return, loop and PNG encoding are untouched.
 *
 * Why it is identical. A new BufferedImage(512, 512, TYPE_INT_ARGB or TYPE_INT_RGB) has a one-bank DataBufferInt with
 * offset 0 behind an integer raster with scanline stride 512 and no translation, so setRGB(x, y, rgb) stores
 * DirectColorModel.getDataElements(rgb) at index y * 512 + x: rgb itself for the default ARGB model, (r << 16 | g << 8 | b)
 * = rgb & 0xFFFFFF for the 24-bit RGB model (proven exhaustively for every int against the JDK's own setRGB). The handler
 * checks that layout once per image (type, size, raster, sample model, buffer) and otherwise calls setRGB; an x or y
 * outside 0..511 also goes to setRGB (which throws as before). The images are local to write() and only PNG-encoded, so
 * the zip files are byte-identical. The one internal difference: reading the int array marks each image's DataBuffer as
 * untrackable for Java2D's image acceleration, which these images (never drawn, only encoded) do not use.
 *
 * -Dbons_and_furious.ftbchunksRegionWriteDirect=false switches it off at run time.
 */
public final class RegionWriteDirect {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ftbchunksRegionWriteDirect", "true"));
    /** Calls answered by a direct store (harness and rig counter; plain long, render thread). */
    public static long direct;
    private static volatile boolean announced;

    /** The last image a call site saw and its int array (null: not the expected layout, setRGB is called). */
    private static final class Slot {
        final BufferedImage image;
        final int[] data;

        Slot(BufferedImage image, int[] data) {
            this.image = image;
            this.data = data;
        }
    }

    private static volatile Slot dataSlot, foliageSlot, grassSlot, waterSlot, blocksSlot;

    private RegionWriteDirect() {}

    private static Slot slot(BufferedImage image, int type) {
        int[] data = null;
        if (image.getType() == type && image.getWidth() == 512 && image.getHeight() == 512) {
            WritableRaster raster = image.getRaster();
            if (raster.getParent() == null && raster.getMinX() == 0 && raster.getMinY() == 0
                    && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0
                    && raster.getWidth() == 512 && raster.getHeight() == 512
                    && raster.getSampleModel() instanceof SinglePixelPackedSampleModel model && model.getScanlineStride() == 512
                    && model.getNumBands() == (type == BufferedImage.TYPE_INT_ARGB ? 4 : 3)
                    && raster.getDataBuffer() instanceof DataBufferInt buffer && buffer.getNumBanks() == 1
                    && buffer.getOffset() == 0 && buffer.getSize() == 512 * 512) {
                data = buffer.getData();
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: ftbchunks_region_write_direct applies (FTB Chunks map regions are copied into their images directly)");
                }
            }
        }
        return new Slot(image, data);
    }

    /** setRGB of the data image (TYPE_INT_ARGB: the int is stored as it is). */
    public static void data(BufferedImage image, int x, int y, int rgb) {
        if (!enabled) {
            image.setRGB(x, y, rgb);
            return;
        }
        Slot s = dataSlot;
        if (s == null || s.image != image) dataSlot = s = slot(image, BufferedImage.TYPE_INT_ARGB);
        if (s.data == null || ((x | y) >>> 9) != 0) {
            image.setRGB(x, y, rgb);
            return;
        }
        s.data[(y << 9) + x] = rgb;
        direct++;
    }

    public static void foliage(BufferedImage image, int x, int y, int rgb) {
        if (!enabled) {
            image.setRGB(x, y, rgb);
            return;
        }
        Slot s = foliageSlot;
        if (s == null || s.image != image) foliageSlot = s = slot(image, BufferedImage.TYPE_INT_RGB);
        rgb24(s, image, x, y, rgb);
    }

    public static void grass(BufferedImage image, int x, int y, int rgb) {
        if (!enabled) {
            image.setRGB(x, y, rgb);
            return;
        }
        Slot s = grassSlot;
        if (s == null || s.image != image) grassSlot = s = slot(image, BufferedImage.TYPE_INT_RGB);
        rgb24(s, image, x, y, rgb);
    }

    public static void water(BufferedImage image, int x, int y, int rgb) {
        if (!enabled) {
            image.setRGB(x, y, rgb);
            return;
        }
        Slot s = waterSlot;
        if (s == null || s.image != image) waterSlot = s = slot(image, BufferedImage.TYPE_INT_RGB);
        rgb24(s, image, x, y, rgb);
    }

    public static void blocks(BufferedImage image, int x, int y, int rgb) {
        if (!enabled) {
            image.setRGB(x, y, rgb);
            return;
        }
        Slot s = blocksSlot;
        if (s == null || s.image != image) blocksSlot = s = slot(image, BufferedImage.TYPE_INT_RGB);
        rgb24(s, image, x, y, rgb);
    }

    private static void rgb24(Slot s, BufferedImage image, int x, int y, int rgb) {
        if (s.data == null || ((x | y) >>> 9) != 0) {
            image.setRGB(x, y, rgb);
            return;
        }
        s.data[(y << 9) + x] = rgb & 0xFFFFFF;
        direct++;
    }

    /** Harness only: forget the images the call sites saw (each run starts like a fresh game). */
    public static void reset() {
        dataSlot = foliageSlot = grassSlot = waterSlot = blocksSlot = null;
        direct = 0;
    }
}
