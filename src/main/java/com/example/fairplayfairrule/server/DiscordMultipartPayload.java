package com.example.fairplayfairrule.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Safely framed Discord multipart body with bounded ordered UTF-8 attachments. */
public final class DiscordMultipartPayload {
    private static final Pattern SAFE_BOUNDARY = Pattern.compile("[A-Za-z0-9_-]{16,70}");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9-]+\\.txt");

    private final String boundary;
    private final String payloadJson;
    private final List<BodySegment> segments;
    private final long contentLength;

    private DiscordMultipartPayload(String boundary, String payloadJson, List<BodySegment> segments) {
        this.boundary = boundary;
        this.payloadJson = payloadJson;
        this.segments = List.copyOf(segments);
        long length = 0;
        for (BodySegment segment : segments) {
            length = Math.addExact(length, segment.length());
        }
        this.contentLength = length;
    }

    public static DiscordMultipartPayload create(
            JsonObject summary,
            List<ManifestAttachmentPlan.AttachmentPart> parts,
            String boundary) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(parts, "parts");
        if (!SAFE_BOUNDARY.matcher(Objects.requireNonNull(boundary, "boundary")).matches()) {
            throw new IllegalArgumentException("Unsafe multipart boundary");
        }
        if (parts.isEmpty() || parts.size() > ManifestAttachmentPlanner.MAX_PARTS) {
            throw new IllegalArgumentException("Invalid Discord attachment count");
        }

        long totalContent = 0;
        Set<String> names = new HashSet<>();
        for (ManifestAttachmentPlan.AttachmentPart part : parts) {
            if (!SAFE_FILE_NAME.matcher(part.fileName()).matches() || !names.add(part.fileName())) {
                throw new IllegalArgumentException("Unsafe or duplicate Discord attachment filename");
            }
            if (part.length() > ManifestAttachmentPlanner.MAX_PART_BYTES) {
                throw new IllegalArgumentException("Discord attachment exceeds the per-part limit");
            }
            totalContent += part.length();
            if (totalContent > ManifestAttachmentPlanner.MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Discord attachments exceed the total limit");
            }
        }

        JsonObject json = summary.deepCopy();
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        json.add("allowed_mentions", allowedMentions);
        JsonArray attachments = new JsonArray();
        for (int index = 0; index < parts.size(); index++) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("id", index);
            attachment.addProperty("filename", parts.get(index).fileName());
            attachments.add(attachment);
        }
        json.add("attachments", attachments);
        String payloadJson = json.toString();

        List<BodySegment> segments = new ArrayList<>(3 + parts.size() * 3);
        segments.add(BodySegment.text("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                + payloadJson + "\r\n"));
        for (int index = 0; index < parts.size(); index++) {
            ManifestAttachmentPlan.AttachmentPart part = parts.get(index);
            segments.add(BodySegment.text("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"files[" + index
                    + "]\"; filename=\"" + part.fileName() + "\"\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"));
            segments.add(BodySegment.attachment(part));
            segments.add(BodySegment.text("\r\n"));
        }
        segments.add(BodySegment.text("--" + boundary + "--\r\n"));
        return new DiscordMultipartPayload(boundary, payloadJson, segments);
    }

    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public long contentLength() {
        return contentLength;
    }

    public HttpRequest.BodyPublisher bodyPublisher() {
        HttpRequest.BodyPublisher[] publishers = segments.stream()
                .map(BodySegment::publisher)
                .toArray(HttpRequest.BodyPublisher[]::new);
        return HttpRequest.BodyPublishers.concat(publishers);
    }

    String payloadJson() {
        return payloadJson;
    }

    byte[] renderForTesting() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(contentLength));
        for (BodySegment segment : segments) {
            output.write(segment.backing(), segment.offset(), segment.length());
        }
        return output.toByteArray();
    }

    private record BodySegment(byte[] backing, int offset, int length) {
        private static BodySegment text(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return new BodySegment(bytes, 0, bytes.length);
        }

        private static BodySegment attachment(ManifestAttachmentPlan.AttachmentPart part) {
            return new BodySegment(
                    part.backingForMultipart(), part.offsetForMultipart(), part.length());
        }

        private HttpRequest.BodyPublisher publisher() {
            return HttpRequest.BodyPublishers.ofByteArray(backing, offset, length);
        }
    }
}
