package com.routeplan.user.application;

import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProfileImageServiceTest {
    @Test void sanitizesAndCropsRasterTo256Pixels() throws Exception {
        var source = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", bytes);
        byte[] result = ProfileImageService.sanitize(bytes.toByteArray());
        var decoded = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(decoded.getWidth()).isEqualTo(256);
        assertThat(decoded.getHeight()).isEqualTo(256);
        assertThat(result).hasSizeLessThan(1024 * 1024);
    }
    @Test void rejectsSvgTextOversizeAndPixelBomb() throws Exception {
        assertThatThrownBy(() -> ProfileImageService.sanitize("<svg onload='alert(1)'/>".getBytes())).hasMessageContaining("PNG");
        assertThatThrownBy(() -> ProfileImageService.sanitize(new byte[2 * 1024 * 1024 + 1])).hasMessageContaining("2MB");
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        assertThatThrownBy(() -> ProfileImageService.sanitize(out.toByteArray())).hasMessageContaining("4096");
    }
}
