package com.routeplan.user.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileImageService {
    public static final long MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    private final JdbcTemplate jdbc;

    public ProfileImageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String url(long userId) {
        return jdbc.query("SELECT revision FROM user_avatars WHERE user_id = ?",
                (rs, row) -> "/api/v1/profile/avatar?v=" + rs.getString(1), userId)
                .stream().findFirst().orElse(null);
    }

    public String upload(long userId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw invalid("프로필 사진은 2MB 이하의 PNG 또는 JPEG 파일을 선택하세요.");
        }
        byte[] sanitized;
        try { sanitized = sanitize(file.getBytes()); }
        catch (IOException exception) { throw invalid("이미지를 읽을 수 없습니다. PNG 또는 JPEG 파일을 다시 선택하세요."); }
        String revision = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO user_avatars(user_id, image_data, revision) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET image_data = EXCLUDED.image_data, revision = EXCLUDED.revision
                """, userId, sanitized, revision);
        return "/api/v1/profile/avatar?v=" + revision;
    }

    public byte[] read(long userId) {
        return jdbc.query("SELECT image_data FROM user_avatars WHERE user_id = ?",
                (rs, row) -> rs.getBytes(1), userId).stream().findFirst().orElse(null);
    }

    public void remove(long userId) { jdbc.update("DELETE FROM user_avatars WHERE user_id = ?", userId); }

    /** Decode only bounded PNG/JPEG raster data, then strip metadata and original encoding. */
    static byte[] sanitize(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) throw invalid("이미지 용량은 2MB 이하여야 합니다.");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("PNG 또는 JPEG 이미지 파일만 사용할 수 있습니다.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) {
                    throw invalid("PNG 또는 JPEG 이미지 파일만 사용할 수 있습니다.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000) {
                    throw invalid("이미지는 가로·세로 4096px 이하, 1600만 화소 이하여야 합니다.");
                }
                var parameters = reader.getDefaultReadParam();
                int sampling = Math.max(1, Math.min(width, height) / 512);
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                BufferedImage source = reader.read(0, parameters);
                width = source.getWidth(); height = source.getHeight();
                int side = Math.min(width, height);
                BufferedImage square = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                var graphics = square.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int x = (width - side) / 2, y = (height - side) / 2;
                    graphics.drawImage(source, 0, 0, 256, 256, x, y, x + side, y + side, null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(square, "png", output);
                return output.toByteArray();
            } finally { reader.dispose(); }
        }
    }

    private static RoutePlanException invalid(String message) { return new RoutePlanException(ErrorCode.INVALID_INPUT, message); }
}
