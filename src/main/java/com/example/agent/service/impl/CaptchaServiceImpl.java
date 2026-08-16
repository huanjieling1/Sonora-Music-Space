package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.service.CaptchaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    static final String SESSION_KEY = "REGISTER_IMAGE_CAPTCHA";
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int WIDTH = 150;
    private static final int HEIGHT = 48;
    private final SecureRandom random = new SecureRandom();

    @Override
    public byte[] create(HttpSession session) {
        String code = randomText(5);
        session.setAttribute(SESSION_KEY, new CaptchaState(code, Instant.now().plusSeconds(120)));
        return render(code);
    }

    @Override
    public void verifyAndConsume(HttpSession session, String submitted) {
        Object stored = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (!(stored instanceof CaptchaState state) || state.expiresAt().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图形验证码已失效，请刷新后重试");
        }
        if (submitted == null || !state.code().equalsIgnoreCase(submitted.trim())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "图形验证码错误，请刷新后重试");
        }
    }

    private String randomText(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private byte[] render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(246, 248, 251));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setStroke(new BasicStroke(1.4f));
        for (int i = 0; i < 7; i++) {
            graphics.setColor(new Color(110 + random.nextInt(90), 115 + random.nextInt(85), 120 + random.nextInt(80)));
            graphics.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            graphics.setColor(new Color(20 + random.nextInt(60), 45 + random.nextInt(70), 65 + random.nextInt(70)));
            graphics.drawString(String.valueOf(code.charAt(i)), 13 + i * 26, 34 + random.nextInt(5));
        }
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法生成图形验证码", exception);
        }
    }

    record CaptchaState(String code, Instant expiresAt) implements Serializable {
    }
}
