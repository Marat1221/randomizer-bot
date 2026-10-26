package com.example.demo.entity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BotProtectionFilter extends OncePerRequestFilter {

    // -------- Настройки --------
    private static final int MAX_REQUESTS_PER_MINUTE = 30; // запросов/мин с одного IP
    private static final long WINDOW_MS = 60_000;

    private final Map<String, RateInfo> rateMap = new ConcurrentHashMap<>();

    // Боты, которых блокируем (подстрока в нижнем регистре)
    private static final String[] BOT_UA_MARKERS = {
            "spider", "crawler", "scanner", "headless",
            "python-requests", "python-urllib", "curl/", "wget/",
            "go-http-client", "okhttp", "axios", "node-fetch",
            "phantomjs", "selenium", "puppeteer", "playwright",
            "scrapy", "httpclient", "java/", "sqlmap", "nikto",
            "masscan", "nmap", "zgrab"
    };

    // Легитимные UA — пропускаем (поисковики, мессенджеры)
    private static final String[] ALLOWED_UA_MARKERS = {
            "googlebot", "yandexbot", "bingbot", "duckduckbot",
            "telegram", "whatsapp", "viber", "facebookexternalhit",
            "chrome", "safari", "firefox", "edg/", "opr/", "opera"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Статику пропускаем (её много, не блокируем)
        if (isStaticResource(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Реальный IP (Amvera за прокси)
        String ip = getClientIp(request);

        // 3. Проверка User-Agent
        String userAgent = request.getHeader("User-Agent");
        if (isBotUserAgent(userAgent)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("Forbidden");
            return;
        }

        // 4. Rate limiting
        if (!isAllowedByRateLimit(ip)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("Too many requests. Slow down.");
            return;
        }

        // 5. Пропускаем
        filterChain.doFilter(request, response);
    }

    private boolean isStaticResource(String path) {
        return path.endsWith(".css") || path.endsWith(".js")
                || path.endsWith(".png") || path.endsWith(".jpg")
                || path.endsWith(".jpeg") || path.endsWith(".svg")
                || path.endsWith(".ico") || path.endsWith(".woff")
                || path.endsWith(".woff2") || path.endsWith(".mp3")
                || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.equals("/favicon.ico");
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isBotUserAgent(String ua) {
        if (ua == null || ua.isBlank() || ua.length() < 15) {
            return true;
        }
        String lower = ua.toLowerCase();

        // Сначала проверяем разрешённых — их пропускаем
        for (String allowed : ALLOWED_UA_MARKERS) {
            if (lower.contains(allowed)) {
                return false;
            }
        }

        // Затем проверяем запрещённых
        for (String marker : BOT_UA_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedByRateLimit(String ip) {
        long now = System.currentTimeMillis();
        RateInfo info = rateMap.computeIfAbsent(ip, k -> new RateInfo(now));

        synchronized (info) {
            if (now - info.windowStart > WINDOW_MS) {
                info.count.set(0);
                info.windowStart = now;
            }
            int count = info.count.incrementAndGet();
            return count <= MAX_REQUESTS_PER_MINUTE;
        }
    }

    private static class RateInfo {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart;
        RateInfo(long start) { this.windowStart = start; }
    }
}