package com.example.fairplayfairrule.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordMultipartPayloadTest {
    @Test
    void buildsOrderedMultipartWithExactUtf8AndSuppressedMentions() throws Exception {
        JsonObject summary = new JsonObject();
        JsonArray embeds = new JsonArray();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Manifest 😀");
        embeds.add(embed);
        summary.add("embeds", embeds);
        byte[] first = "α\r\nβ\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "終\n".getBytes(StandardCharsets.UTF_8);
        List<ManifestAttachmentPlan.AttachmentPart> parts = List.of(
                new ManifestAttachmentPlan.AttachmentPart("manifest-id-time-part-001-of-002.txt", first),
                new ManifestAttachmentPlan.AttachmentPart("manifest-id-time-part-002-of-002.txt", second));

        DiscordMultipartPayload payload = DiscordMultipartPayload.create(
                summary, parts, "fpfrBoundary1234567890");
        JsonObject payloadJson = JsonParser.parseString(payload.payloadJson()).getAsJsonObject();
        byte[] rendered = payload.renderForTesting();

        assertEquals("multipart/form-data; boundary=fpfrBoundary1234567890", payload.contentType());
        assertTrue(payloadJson.getAsJsonObject("allowed_mentions")
                .getAsJsonArray("parse").isEmpty());
        assertEquals("manifest-id-time-part-001-of-002.txt",
                payloadJson.getAsJsonArray("attachments").get(0).getAsJsonObject()
                        .get("filename").getAsString());
        assertEquals("manifest-id-time-part-002-of-002.txt",
                payloadJson.getAsJsonArray("attachments").get(1).getAsJsonObject()
                        .get("filename").getAsString());
        assertEquals(0, payloadJson.getAsJsonArray("attachments").get(0)
                .getAsJsonObject().get("id").getAsInt());
        assertEquals(1, payloadJson.getAsJsonArray("attachments").get(1)
                .getAsJsonObject().get("id").getAsInt());
        assertArrayEquals(first, extractFile(rendered, "files[0]",
                "manifest-id-time-part-001-of-002.txt"));
        assertArrayEquals(second, extractFile(rendered, "files[1]",
                "manifest-id-time-part-002-of-002.txt"));
        assertTrue(new String(rendered, StandardCharsets.UTF_8)
                .contains("Content-Disposition: form-data; name=\"payload_json\"\r\n"));
    }

    @Test
    void rejectsUnsafeBoundaryAndFilenameHeaders() {
        JsonObject summary = new JsonObject();
        ManifestAttachmentPlan.AttachmentPart unsafe =
                new ManifestAttachmentPlan.AttachmentPart("bad\r\nname.txt", new byte[]{1});

        assertThrows(IllegalArgumentException.class,
                () -> DiscordMultipartPayload.create(summary, List.of(unsafe),
                        "fpfrBoundary1234567890"));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordMultipartPayload.create(summary, List.of(
                                new ManifestAttachmentPlan.AttachmentPart("manifest-safe.txt", new byte[]{1})),
                        "bad\r\nboundary"));
    }

    @Test
    void rejectsMoreThanFiveOrOversizedAttachments() {
        JsonObject summary = new JsonObject();
        ManifestAttachmentPlan.AttachmentPart small =
                new ManifestAttachmentPlan.AttachmentPart("manifest-safe.txt", new byte[]{1});
        List<ManifestAttachmentPlan.AttachmentPart> six = List.of(small, small, small, small, small, small);
        byte[] oversized = new byte[ManifestAttachmentPlanner.MAX_PART_BYTES + 1];

        assertThrows(IllegalArgumentException.class,
                () -> DiscordMultipartPayload.create(summary, six, "fpfrBoundary1234567890"));
        assertThrows(IllegalArgumentException.class,
                () -> DiscordMultipartPayload.create(summary, List.of(
                                new ManifestAttachmentPlan.AttachmentPart("manifest-large.txt", oversized)),
                        "fpfrBoundary1234567890"));
    }

    private static byte[] extractFile(byte[] body, String field, String fileName) {
        byte[] marker = ("Content-Disposition: form-data; name=\"" + field
                + "\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        int start = indexOf(body, marker, 0) + marker.length;
        int end = indexOf(body, "\r\n--fpfrBoundary1234567890".getBytes(StandardCharsets.UTF_8), start);
        return java.util.Arrays.copyOfRange(body, start, end);
    }

    private static int indexOf(byte[] body, byte[] needle, int from) {
        outer:
        for (int index = from; index <= body.length - needle.length; index++) {
            for (int offset = 0; offset < needle.length; offset++) {
                if (body[index + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return index;
        }
        throw new AssertionError("Multipart marker not found");
    }
}
