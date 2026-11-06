package com.cassist.identity;

import com.cassist.common.BusinessException;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AvatarNormalizer {
    public static final int MAX_UPLOAD_BYTES = 512 * 1024;
    public static final int MAX_SOURCE_EDGE = 4096;
    public static final int MAX_OUTPUT_EDGE = 256;

    public byte[] normalize(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("请选择 PNG 或 JPEG 图片");
        if (file.getSize() > MAX_UPLOAD_BYTES) throw tooLarge();
        byte[] source;
        try (var input = file.getInputStream()) {
            source = input.readNBytes(MAX_UPLOAD_BYTES + 1);
        } catch (IOException e) {
            throw invalid("无法读取头像文件");
        }
        if (source.length > MAX_UPLOAD_BYTES) throw tooLarge();
        if (source.length == 0) throw invalid("头像文件为空");

        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("仅支持有效的 PNG 或 JPEG 图片");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!format.equals("PNG") && !format.equals("JPEG"))
                    throw invalid("仅支持 PNG 或 JPEG 图片");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE)
                    throw invalid("头像单边尺寸不能超过 4096 像素");
                // Subsample during decoding to avoid allocating a full 4096-by-4096 result.
                int sample = Math.max(1, Math.max(width, height) / MAX_OUTPUT_EDGE);
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, parameters);
                if (decoded == null) throw invalid("头像图片无法解码");
                double scale = Math.min(1.0, MAX_OUTPUT_EDGE / (double) Math.max(width, height));
                int outWidth = Math.max(1, (int) Math.round(width * scale));
                int outHeight = Math.max(1, (int) Math.round(height * scale));
                BufferedImage normalized = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = normalized.createGraphics();
                try {
                    graphics.setComposite(AlphaComposite.Src);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(decoded, 0, 0, outWidth, outHeight, null);
                } finally {
                    graphics.dispose();
                    decoded.flush();
                }
                try (var output = new ByteArrayOutputStream()) {
                    // A fresh image carries no uploaded metadata or original trailing bytes.
                    if (!ImageIO.write(normalized, "PNG", output))
                        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_PROCESSING_FAILED", "头像处理暂不可用");
                    return output.toByteArray();
                } finally {
                    normalized.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalid("头像图片损坏或无法解码");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", message);
    }
    private static BusinessException tooLarge() {
        return new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", "头像文件不能超过 512 KiB");
    }
}
