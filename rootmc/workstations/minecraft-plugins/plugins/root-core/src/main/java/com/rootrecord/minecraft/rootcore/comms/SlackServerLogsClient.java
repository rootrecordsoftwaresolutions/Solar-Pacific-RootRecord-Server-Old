package com.rootrecord.minecraft.rootcore.comms;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Direct Slack Incoming Webhook posts for server logs (no Cloudflare Worker).
 * Webhook URL: {@code slack.server-logs-webhook} in cloud.yml or env-style keys.
 */
final class SlackServerLogsClient {

    private static final int MAX_TAIL_CHARS = 35_000;

    private SlackServerLogsClient() {}

    static String resolveWebhook(JavaPlugin plugin, FileConfiguration cloud) {
        String fromCloud = firstNonBlank(
                cloud != null ? cloud.getString("slack.server-logs-webhook") : null,
                cloud != null ? cloud.getString("slack.webhooks.server-logs") : null);
        if (!fromCloud.isBlank()) {
            return fromCloud;
        }
        // Optional system properties for local test hosts.
        return firstNonBlank(
                System.getProperty("rootmc.slack.server-logs-webhook"),
                System.getenv("SLACK_SERVER_LOGS_WEBHOOK_URL"));
    }

    static boolean isWebhook(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim().toLowerCase(Locale.ROOT);
        return u.startsWith("https://hooks.slack.com/services/");
    }

    /**
     * Posts caption + log file tail as a fenced code block via Incoming Webhook.
     * @return true when Slack accepted the post
     */
    static boolean postLogFile(JavaPlugin plugin, String webhookUrl, String caption, java.io.File file) {
        if (!isWebhook(webhookUrl) || file == null || !file.isFile()) {
            return false;
        }
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String note = "";
            if (raw.length() > MAX_TAIL_CHARS) {
                raw = raw.substring(raw.length() - MAX_TAIL_CHARS);
                note = "\n_(truncated to last " + MAX_TAIL_CHARS + " chars)_";
            }
            String head = caption == null || caption.isBlank() ? "server logs" : caption.trim();
            String text = head + note + "\n```" + raw + "```";
            if (text.length() > 39_000) {
                text = text.substring(0, 39_000);
            }
            postText(webhookUrl, text);
            plugin.getLogger().info("Root-Core comms: posted server log " + file.getName() + " to Slack #server-logs.");
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Slack #server-logs upload failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    static void postText(String webhookUrl, String text) throws IOException {
        byte[] body = ("{\"text\":" + jsonString(text) + "}").getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) URI.create(webhookUrl.trim()).toURL().openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "RootMC-RootDiscord/1.0");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String resp = "";
        if (stream != null) {
            resp = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            stream.close();
        }
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + resp);
        }
    }

    private static String jsonString(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
