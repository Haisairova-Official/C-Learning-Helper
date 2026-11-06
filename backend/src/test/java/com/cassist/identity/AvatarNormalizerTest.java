package com.cassist.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.cassist.common.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class AvatarNormalizerTest {
    private final AvatarNormalizer normalizer = new AvatarNormalizer();

    @Test void scalesPngAndKeepsAspectRatio() throws Exception {
        byte[] result = normalizer.normalize(file(image("PNG", 600, 300)));
        var decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(256, decoded.getWidth());
        assertEquals(128, decoded.getHeight());
        assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}, Arrays.copyOf(result, 8));
    }

    @Test void acceptsActualJpegDespiteMisleadingNameAndDoesNotEnlarge() throws Exception {
        byte[] result = normalizer.normalize(new MockMultipartFile("file", "not-a-picture.txt", "text/plain", image("JPEG", 12, 9)));
        var decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(12, decoded.getWidth());
        assertEquals(9, decoded.getHeight());
        assertEquals('P', result[1]);
        assertEquals('N', result[2]);
        assertEquals('G', result[3]);
    }

    @Test void stripsUploadedMetadataAndTrailingContent() throws Exception {
        var picture = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        var writer = ImageIO.getImageWritersByFormatName("PNG").next();
        var metadata = writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(picture), null);
        var tree = new IIOMetadataNode("javax_imageio_png_1.0");
        var text = new IIOMetadataNode("tEXt");
        var entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword", "Comment");
        entry.setAttribute("value", "private-camera-coordinate");
        text.appendChild(entry); tree.appendChild(text);
        metadata.mergeTree("javax_imageio_png_1.0", tree);
        var source = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(source)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(picture, null, metadata), null);
        } finally { writer.dispose(); }
        source.write("private-trailing-content".getBytes(StandardCharsets.UTF_8));
        byte[] normalized = normalizer.normalize(file(source.toByteArray()));
        String binary = new String(normalized, StandardCharsets.ISO_8859_1);
        assertFalse(binary.contains("private-camera-coordinate"));
        assertFalse(binary.contains("private-trailing-content"));
        assertNotNull(ImageIO.read(new ByteArrayInputStream(normalized)));
    }

    @Test void rejectsOversizedDeclaredAndActualPayloads() {
        byte[] tooMuch = new byte[AvatarNormalizer.MAX_UPLOAD_BYTES + 1];
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> normalizer.normalize(file(tooMuch)));
        var understated = new MockMultipartFile("file", "avatar.png", "image/png", tooMuch) {
            @Override public long getSize() { return 1; }
        };
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, () -> normalizer.normalize(understated));
    }

    @Test void rejectsDimensionsBeforeFullImageDecode() throws Exception {
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file(image("PNG", 4097, 1))));
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file(image("PNG", 1, 4097))));
    }

    @Test void rejectsGifHtmlEmptyAndTruncatedImageDespitePngLabel() throws Exception {
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file(image("GIF", 10, 10))));
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))));
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file(new byte[0])));
        assertStatus(HttpStatus.BAD_REQUEST, () -> normalizer.normalize(file(Arrays.copyOf(image("PNG", 20, 20), 35))));
    }

    private MockMultipartFile file(byte[] content) { return new MockMultipartFile("file", "avatar.png", "image/png", content); }
    private static byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out));
        return out.toByteArray();
    }
    private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(BusinessException.class, action).status());
    }
}
