package com.nailinai.noveltoscriptbackend.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.CrudResourceStore;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generate high-contrast, bright captcha background images and register them
 * with the captcha resource store so the slider cut-out is easy to spot.
 *
 * Each background is composed of:
 *   1. A bright linear gradient (lightness >= 170) to maximize base contrast.
 *   2. A layer of semi-transparent geometric primitives (circles, rectangles,
 *      triangles, line segments) so the cut-out edge has a clear, irregular
 *      boundary that the human eye can latch onto.
 *   3. A subtle noise field to further break up flat regions.
 *
 * Compared to the previous pure-gradient version, these images keep the slider
 * gap clearly visible even on dim displays.
 */
@Component
public class CaptchaResourceInit {

    private static final Logger log = LoggerFactory.getLogger(CaptchaResourceInit.class);

    @Autowired
    private CrudResourceStore resourceStore;

    private static final int W = 600;
    private static final int H = 360;

    private static final Color[][] PALETTES = new Color[][] {
            { new Color(135, 206, 235), new Color(176, 226, 255) }, // sky blue
            { new Color(255, 183, 94),  new Color(255, 218, 145) }, // warm amber
            { new Color(195, 140, 230), new Color(224, 187, 245) }, // soft violet
            { new Color(120, 220, 180), new Color(186, 240, 215) }, // mint
            { new Color(255, 145, 160), new Color(255, 198, 206) }  // coral pink
    };

    @PostConstruct
    public void init() {
        try {
            File dir = Files.createTempDirectory("captcha-bg-").toFile();
            dir.deleteOnExit();

            int count = 0;
            for (int i = 0; i < PALETTES.length; i++) {
                count += render(dir, "bg" + (i + 1) + ".jpg", i, PALETTES[i][0], PALETTES[i][1]);
            }
            log.info("Generated {} captcha background images in {}", count, dir);
        } catch (Exception e) {
            log.warn("Could not generate captcha backgrounds: {}", e.getMessage());
        }
    }

    private int render(File dir, String name, int seed, Color c1, Color c2) throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g.setPaint(new GradientPaint(0, 0, c1, W, H, c2));
        g.fillRect(0, 0, W, H);

        Random rng = new Random(0xC0FFEE ^ seed);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
        for (int i = 0; i < 18; i++) {
            int x = rng.nextInt(W);
            int y = rng.nextInt(H);
            int r = 18 + rng.nextInt(46);
            g.setColor(rng.nextBoolean() ? Color.WHITE : darken(c1, 0.4f));
            g.fill(new Ellipse2D.Double(x, y, r, r));
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        for (int i = 0; i < 14; i++) {
            int x = rng.nextInt(W);
            int y = rng.nextInt(H);
            int rw = 24 + rng.nextInt(60);
            int rh = 10 + rng.nextInt(30);
            float angle = rng.nextFloat() * (float) Math.PI;
            g.rotate(angle, x + rw / 2.0, y + rh / 2.0);
            g.setColor(rng.nextBoolean() ? Color.WHITE : darken(c2, 0.4f));
            g.fill(new Rectangle2D.Double(x, y, rw, rh));
            g.rotate(-angle, x + rw / 2.0, y + rh / 2.0);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        Stroke stroke = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g.setStroke(stroke);
        for (int i = 0; i < 22; i++) {
            int x1 = rng.nextInt(W);
            int y1 = rng.nextInt(H);
            int x2 = x1 + (rng.nextInt(81) - 40);
            int y2 = y1 + (rng.nextInt(81) - 40);
            g.setColor(new Color(255, 255, 255, 220));
            g.drawLine(x1, y1, x2, y2);
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        Random nrng = new Random(0xBEEF ^ seed);
        for (int i = 0; i < 1200; i++) {
            int x = nrng.nextInt(W);
            int y = nrng.nextInt(H);
            img.setRGB(x, y, darken(nrng.nextBoolean() ? c1 : c2, 0.3f).getRGB());
        }

        g.setComposite(AlphaComposite.SrcOver);
        g.dispose();

        File file = new File(dir, name);
        ImageIO.write(img, "jpg", file);
        resourceStore.addResource(CaptchaTypeConstant.SLIDER,
                new Resource("file", file.getAbsolutePath()));
        return 1;
    }

    private Color darken(Color c, float factor) {
        int r = Math.max(0, Math.min(255, (int) (c.getRed() * factor)));
        int g = Math.max(0, Math.min(255, (int) (c.getGreen() * factor)));
        int b = Math.max(0, Math.min(255, (int) (c.getBlue() * factor)));
        return new Color(r, g, b);
    }
}
