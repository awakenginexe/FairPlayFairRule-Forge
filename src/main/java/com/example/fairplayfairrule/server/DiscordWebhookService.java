package com.example.fairplayfairrule.server;

import com.example.fairplayfairrule.FairPlayFairRule;
import com.example.fairplayfairrule.config.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for sending Discord webhook notifications and uploading data to Hastebin
 * Uses java.net.http.HttpClient for all web requests
 */
public class DiscordWebhookService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String HASTEBIN_URL = "https://hst.sh/documents";
    private static final String HASTEBIN_BASE = "https://hst.sh/";

    /**
     * Send a player join notification webhook (Webhook 1)
     *
     * @param player The player who joined
     */
    public static void sendPlayerJoinNotification(ServerPlayer player) {
        String webhookUrl = Config.WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            FairPlayFairRule.LOGGER.debug("Webhook URL not configured, skipping join notification");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Build Discord embed for player join
                String jsonPayload = buildPlayerJoinEmbed(player);

                // Send webhook
                sendWebhook(webhookUrl, jsonPayload);

                FairPlayFairRule.LOGGER.info("Sent player join notification for: {}", player.getName().getString());

            } catch (Exception e) {
                FairPlayFairRule.LOGGER.error("Failed to send player join notification", e);
            }
        });
    }

    /**
     * Send a player manifest webhook with full mod and resource pack lists (Webhook 2)
     *
     * @param player The player
     * @param mods List of mods (format: "modId@version")
     * @param packs List of active resource packs
     * @param packEnabledCount Number of enabled packs
     * @param packDisabledCount Number of disabled packs
     */
    public static void sendPlayerManifest(ServerPlayer player, List<String> mods, List<String> packs,
                                          int packEnabledCount, int packDisabledCount) {
        String webhookUrl = Config.WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            FairPlayFairRule.LOGGER.debug("Webhook URL not configured, skipping player manifest");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Combine mods and packs into a single text document
                StringBuilder fullList = new StringBuilder();
                fullList.append("=== MODS (").append(mods.size()).append(" total) ===\n");
                for (String mod : mods) {
                    fullList.append(mod).append("\n");
                }
                fullList.append("\n=== RESOURCE PACKS (").append(packs.size()).append(" enabled) ===\n");
                for (String pack : packs) {
                    fullList.append(pack).append("\n");
                }

                // Upload to Hastebin
                String hastebinUrl = uploadToHastebin(fullList.toString());

                // Build Discord embed for player manifest
                String jsonPayload = buildPlayerManifestEmbed(player, mods.size(), packEnabledCount,
                                                              packDisabledCount, hastebinUrl);

                // Send webhook
                sendWebhook(webhookUrl, jsonPayload);

                FairPlayFairRule.LOGGER.info("Sent player manifest for: {}", player.getName().getString());

            } catch (Exception e) {
                FairPlayFairRule.LOGGER.error("Failed to send player manifest", e);
            }
        });
    }

    /**
     * Send a ban notification webhook (high priority)
     *
     * @param player The banned player
     * @param bannedModId The mod ID that triggered the ban
     * @param mods Full mod list
     * @param packs Full resource pack list
     */
    public static void sendBanNotification(ServerPlayer player, String bannedModId,
                                          List<String> mods, List<String> packs) {
        String webhookUrl = Config.WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            FairPlayFairRule.LOGGER.debug("Webhook URL not configured, skipping ban notification");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Combine mods and packs into a single text document
                StringBuilder fullList = new StringBuilder();
                fullList.append("=== BANNED PLAYER INFO ===\n");
                fullList.append("Banned for mod: ").append(bannedModId).append("\n\n");
                fullList.append("=== MODS (").append(mods.size()).append(" total) ===\n");
                for (String mod : mods) {
                    fullList.append(mod).append("\n");
                }
                fullList.append("\n=== RESOURCE PACKS (").append(packs.size()).append(" enabled) ===\n");
                for (String pack : packs) {
                    fullList.append(pack).append("\n");
                }

                // Upload to Hastebin
                String hastebinUrl = uploadToHastebin(fullList.toString());

                // Build Discord embed for ban notification
                String jsonPayload = buildBanNotificationEmbed(player, bannedModId, hastebinUrl);

                // Send webhook
                sendWebhook(webhookUrl, jsonPayload);

                FairPlayFairRule.LOGGER.warn("Sent ban notification for: {}", player.getName().getString());

            } catch (Exception e) {
                FairPlayFairRule.LOGGER.error("Failed to send ban notification", e);
            }
        });
    }

    /**
     * Upload text content to Hastebin and return the URL
     *
     * @param text The text to upload
     * @return The Hastebin URL
     * @throws Exception if upload fails
     */
    private static String uploadToHastebin(String text) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HASTEBIN_URL))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(text))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Hastebin upload failed with status: " + response.statusCode());
        }

        // Parse JSON response: {"key":"ohecedehop"}
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
        String key = jsonResponse.get("key").getAsString();

        return HASTEBIN_BASE + key;
    }

    /**
     * Send a webhook to Discord
     *
     * @param webhookUrl The Discord webhook URL
     * @param jsonPayload The JSON payload to send
     * @throws Exception if sending fails
     */
    private static void sendWebhook(String webhookUrl, String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Exception("Discord webhook failed with status: " + response.statusCode() +
                              " - " + response.body());
        }
    }

    /**
     * Build JSON payload for player join embed
     */
    private static String buildPlayerJoinEmbed(ServerPlayer player) {
        String timestamp = Instant.now().toString();

        return "{\n" +
                "  \"embeds\": [{\n" +
                "    \"title\": \"\uD83D\uDFE2 Player Joined Server\",\n" +
                "    \"color\": 3066993,\n" +
                "    \"fields\": [\n" +
                "      {\n" +
                "        \"name\": \"Player\",\n" +
                "        \"value\": \"" + escapeJson(player.getName().getString()) + "\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"UUID\",\n" +
                "        \"value\": \"`" + player.getUUID().toString() + "`\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Timestamp\",\n" +
                "        \"value\": \"" + timestamp + "\",\n" +
                "        \"inline\": false\n" +
                "      }\n" +
                "    ],\n" +
                "    \"footer\": {\n" +
                "      \"text\": \"FairPlayFairRule\"\n" +
                "    }\n" +
                "  }]\n" +
                "}";
    }

    /**
     * Build JSON payload for player manifest embed
     */
    private static String buildPlayerManifestEmbed(ServerPlayer player, int modCount,
                                                   int packEnabledCount, int packDisabledCount,
                                                   String hastebinUrl) {
        return "{\n" +
                "  \"embeds\": [{\n" +
                "    \"title\": \"\uD83D\uDCCB Player Manifest\",\n" +
                "    \"color\": 3447003,\n" +
                "    \"fields\": [\n" +
                "      {\n" +
                "        \"name\": \"Player\",\n" +
                "        \"value\": \"" + escapeJson(player.getName().getString()) + "\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"UUID\",\n" +
                "        \"value\": \"`" + player.getUUID().toString() + "`\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Mods (Enabled)\",\n" +
                "        \"value\": \"" + modCount + "\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Resource Packs (Enabled)\",\n" +
                "        \"value\": \"" + packEnabledCount + "\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Full Lists\",\n" +
                "        \"value\": \"[View on Hastebin](" + hastebinUrl + ")\",\n" +
                "        \"inline\": false\n" +
                "      }\n" +
                "    ],\n" +
                "    \"footer\": {\n" +
                "      \"text\": \"FairPlayFairRule\"\n" +
                "    }\n" +
                "  }]\n" +
                "}";
    }

    /**
     * Build JSON payload for ban notification embed
     */
    private static String buildBanNotificationEmbed(ServerPlayer player, String bannedModId, String hastebinUrl) {
        String timestamp = Instant.now().toString();

        return "{\n" +
                "  \"embeds\": [{\n" +
                "    \"title\": \"\uD83D\uDEAB PLAYER BANNED - PROHIBITED MOD DETECTED\",\n" +
                "    \"color\": 15158332,\n" +
                "    \"fields\": [\n" +
                "      {\n" +
                "        \"name\": \"Player\",\n" +
                "        \"value\": \"" + escapeJson(player.getName().getString()) + "\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"UUID\",\n" +
                "        \"value\": \"`" + player.getUUID().toString() + "`\",\n" +
                "        \"inline\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Banned Mod\",\n" +
                "        \"value\": \"`" + escapeJson(bannedModId) + "`\",\n" +
                "        \"inline\": false\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Timestamp\",\n" +
                "        \"value\": \"" + timestamp + "\",\n" +
                "        \"inline\": false\n" +
                "      },\n" +
                "      {\n" +
                "        \"name\": \"Full Mod & Pack List\",\n" +
                "        \"value\": \"[View on Hastebin](" + hastebinUrl + ")\",\n" +
                "        \"inline\": false\n" +
                "      }\n" +
                "    ],\n" +
                "    \"footer\": {\n" +
                "      \"text\": \"FairPlayFairRule - HIGH PRIORITY ALERT\"\n" +
                "    }\n" +
                "  }]\n" +
                "}";
    }

    /**
     * Escape special JSON characters in strings
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
