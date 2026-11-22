package jui.render;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A pure-Java PNG reader, so the kit shows real pixels without a platform
 * image API: it walks the chunks, inflates the scanlines, and undoes the
 * filters itself. It reads the common encodings — bit depth 8, color types
 * 0 (gray), 2 (RGB), 3 (palette) and 6 (RGBA), no interlace — and returns
 * straight ARGB pixels, row-major, top-left first.
 */
public final class Png {

    /** One decoded picture. */
    public record Image(int width, int height, int[] argb) {}

    public static Image read(InputStream in) throws IOException {
        byte[] all = in.readAllBytes();
        return read(all, 0, all.length);
    }

    public static Image read(byte[] data, int off, int len) throws IOException {
        if (len < 57 || !signature(data, off)) {
            throw new IOException("not a PNG");
        }
        int at = off + 8;
        int end = off + len;
        int width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
        int[] palette = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        while (at + 8 <= end) {
            int length = i32(data, at);
            String type = type(data, at + 4);
            int body = at + 8;
            if (body + length > end) {
                throw new IOException("truncated PNG chunk");
            }
            switch (type) {
                case "IHDR" -> {
                    width = i32(data, body);
                    height = i32(data, body + 4);
                    bitDepth = data[body + 8] & 0xFF;
                    colorType = data[body + 9] & 0xFF;
                    interlace = data[body + 12] & 0xFF;
                }
                case "PLTE" -> {
                    int colors = length / 3;
                    palette = new int[colors];
                    for (int i = 0; i < colors; i++) {
                        palette[i] = 0xFF << 24
                                | (data[body + i * 3] & 0xFF) << 16
                                | (data[body + i * 3 + 1] & 0xFF) << 8
                                | (data[body + i * 3 + 2] & 0xFF);
                    }
                }
                case "IDAT" -> idat.write(data, body, length);
                case "IEND" -> at = end;
                default -> { }
            }
            at = body + length + 4; // the CRC
        }
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
            throw new IOException("PNG size out of range: " + width + "x" + height);
        }
        if (bitDepth != 8) {
            throw new IOException("PNG bit depth " + bitDepth + " unsupported; only 8 reads");
        }
        if (interlace != 0) {
            throw new IOException("interlaced PNG unsupported");
        }
        boolean hasAlpha = colorType == 6;
        boolean hasPalette = colorType == 3;
        if (colorType != 0 && colorType != 2 && !hasPalette && !hasAlpha) {
            throw new IOException("PNG color type " + colorType + " unsupported");
        }
        int channels = colorType == 0 ? 1 : colorType == 2 ? 3 : hasPalette ? 1 : 4;
        byte[] raw = inflate(idat.toByteArray(), height * (width * channels + 1));
        int[] argb = new int[width * height];
        int stride = width * channels;
        int previousAt = -1; // the unfiltered previous row, in raw[]
        for (int row = 0; row < height; row++) {
            int at2 = row * (stride + 1);
            int filter = raw[at2] & 0xFF;
            int line = at2 + 1;
            unfilter(raw, line, stride, previousAt < 0 ? null : raw, previousAt, channels);
            previousAt = line;
            for (int col = 0; col < width; col++) {
                int i = line + col * channels;
                int p;
                switch (colorType) {
                    case 0 -> {
                        int g = raw[i] & 0xFF;
                        p = 0xFF000000 | g << 16 | g << 8 | g;
                    }
                    case 2 -> p = 0xFF000000 | (raw[i] & 0xFF) << 16 | (raw[i + 1] & 0xFF) << 8
                            | (raw[i + 2] & 0xFF);
                    case 3 -> p = palette != null ? palette[raw[i] & 0xFF] : 0xFF000000;
                    default -> p = (raw[i + 3] & 0xFF) << 24 | (raw[i] & 0xFF) << 16
                            | (raw[i + 1] & 0xFF) << 8 | (raw[i + 2] & 0xFF);
                }
                argb[row * width + col] = p;
            }
        }
        return new Image(width, height, argb);
    }

    private static boolean signature(byte[] d, int at) {
        return (d[at] & 0xFF) == 0x89 && d[at + 1] == 'P' && d[at + 2] == 'N' && d[at + 3] == 'G';
    }

    private static int i32(byte[] d, int at) {
        return (d[at] & 0xFF) << 24 | (d[at + 1] & 0xFF) << 16 | (d[at + 2] & 0xFF) << 8 | (d[at + 3] & 0xFF);
    }

    private static String type(byte[] d, int at) {
        return new String(d, at, 4, StandardCharsets.US_ASCII);
    }

    private static byte[] inflate(byte[] compressed, int expected) throws IOException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] out = new byte[expected];
            int done = 0;
            while (!inflater.finished() && done < expected) {
                int n = inflater.inflate(out, done, expected - done);
                if (n == 0 && inflater.needsInput()) {
                    throw new IOException("truncated PNG data");
                }
                done += n;
            }
            return out;
        } catch (DataFormatException e) {
            throw new IOException("bad PNG zlib stream", e);
        } finally {
            inflater.end();
        }
    }

    /** Undo one row's filter, in place, against the previous unfiltered row. */
    private static void unfilter(byte[] raw, int line, int stride, byte[] previous, int previousAt,
                                 int channels) {
        int filter = raw[line - 1] & 0xFF;
        if (filter == 0 || previous == null && filter > 1) {
            if (filter == 0) {
                return;
            }
            // Filters 2 (Up), 3 (Average) and 4 (Paeth) read the row above;
            // with none, they treat it as zeros, which the arithmetic does.
        }
        switch (filter) {
            case 0 -> { }
            case 1 -> { // Sub: the byte to the left
                for (int i = channels; i < stride; i++) {
                    raw[line + i] += raw[line + i - channels];
                }
            }
            case 2 -> { // Up: the byte above
                for (int i = 0; i < stride; i++) {
                    raw[line + i] += previous[previousAt + i];
                }
            }
            case 3 -> { // Average: the mean of left and above
                for (int i = 0; i < stride; i++) {
                    int left = i >= channels ? raw[line + i - channels] & 0xFF : 0;
                    int up = previous[previousAt + i] & 0xFF;
                    raw[line + i] += (byte) ((left + up) / 2);
                }
            }
            case 4 -> { // Paeth
                for (int i = 0; i < stride; i++) {
                    int left = i >= channels ? raw[line + i - channels] & 0xFF : 0;
                    int up = previous[previousAt + i] & 0xFF;
                    int upLeft = i >= channels ? previous[previousAt + i - channels] & 0xFF : 0;
                    raw[line + i] += (byte) paeth(left, up, upLeft);
                }
            }
            default -> throw new IllegalStateException("bad PNG filter " + filter);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private Png() {}
}
