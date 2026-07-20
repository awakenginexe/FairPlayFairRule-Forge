package com.example.fairplayfairrule.server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class ManifestAttachmentPlannerTest {
    private static final UUID PLAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Instant TIMESTAMP = Instant.parse("2026-07-20T12:34:56.789Z");

    @Test
    void exactlyNineMibUsesOneAttachment() {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_PART_BYTES];
        Arrays.fill(manifest, (byte) 'a');

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner().plan(
                PLAYER_ID, TIMESTAMP, manifest);

        assertTrue(plan.isDeliverable());
        assertEquals(1, plan.parts().size());
        assertEquals(ManifestAttachmentPlanner.MAX_PART_BYTES, plan.parts().get(0).content().length);
        assertEquals("manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z.txt",
                plan.parts().get(0).fileName());
    }

    @Test
    void oneByteOverNineMibSplitsAtLastLineBoundary() throws Exception {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_PART_BYTES + 1];
        Arrays.fill(manifest, (byte) 'a');
        manifest[ManifestAttachmentPlanner.MAX_PART_BYTES - 1] = '\n';

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner().plan(
                PLAYER_ID, TIMESTAMP, manifest);

        assertTrue(plan.isDeliverable());
        assertEquals(2, plan.parts().size());
        assertEquals(ManifestAttachmentPlanner.MAX_PART_BYTES, plan.parts().get(0).content().length);
        assertEquals(1, plan.parts().get(1).content().length);
        assertArrayEquals(manifest, concatenate(plan));
    }

    @Test
    void splitIsUtf8AndLineBoundarySafe() throws Exception {
        byte[] manifest = "alpha😀\nβeta\r\ngamma終\n".getBytes(StandardCharsets.UTF_8);
        ManifestAttachmentPlanner planner = new ManifestAttachmentPlanner(12, 5, 60);

        ManifestAttachmentPlan plan = planner.plan(PLAYER_ID, TIMESTAMP, manifest);

        assertTrue(plan.isDeliverable());
        assertTrue(plan.parts().size() > 1);
        for (int index = 0; index < plan.parts().size() - 1; index++) {
            byte[] content = plan.parts().get(index).content();
            assertEquals('\n', content[content.length - 1]);
            assertEquals(new String(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8).length,
                    content.length);
        }
        assertArrayEquals(manifest, concatenate(plan));
    }

    @Test
    void multipartNamesAndOrderAreDeterministic() {
        byte[] manifest = "aaa\nbbb\nccc\n".getBytes(StandardCharsets.UTF_8);
        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner(4, 5, 20)
                .plan(PLAYER_ID, TIMESTAMP, manifest);

        assertEquals(3, plan.parts().size());
        assertEquals("manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-001-of-003.txt",
                plan.parts().get(0).fileName());
        assertEquals("manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-002-of-003.txt",
                plan.parts().get(1).fileName());
        assertEquals("manifest-550e8400-e29b-41d4-a716-446655440000-20260720T123456789Z-part-003-of-003.txt",
                plan.parts().get(2).fileName());
    }

    @Test
    void rejectsWhenLineBoundariesRequireMoreThanMaximumParts() {
        byte[] manifest = "aaa\nbbb\nccc\n".getBytes(StandardCharsets.UTF_8);

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner(4, 2, 20)
                .plan(PLAYER_ID, TIMESTAMP, manifest);

        assertFalse(plan.isDeliverable());
        assertTrue(plan.parts().isEmpty());
        assertTrue(plan.error().contains("part count"));
    }

    @Test
    void rejectsTotalOneByteAboveFortyFiveMibWithoutParts() {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_TOTAL_BYTES + 1];

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner().plan(
                PLAYER_ID, TIMESTAMP, manifest);

        assertFalse(plan.isDeliverable());
        assertTrue(plan.parts().isEmpty());
        assertTrue(plan.error().contains("45 MiB"));
    }

    @Test
    void exactlyFortyFiveMibUsesTheMaximumFiveOrderedParts() throws Exception {
        byte[] manifest = new byte[ManifestAttachmentPlanner.MAX_TOTAL_BYTES];
        Arrays.fill(manifest, (byte) 'a');
        for (int part = 1; part < ManifestAttachmentPlanner.MAX_PARTS; part++) {
            manifest[part * ManifestAttachmentPlanner.MAX_PART_BYTES - 1] = '\n';
        }

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner().plan(
                PLAYER_ID, TIMESTAMP, manifest);

        assertTrue(plan.isDeliverable());
        assertEquals(ManifestAttachmentPlanner.MAX_PARTS, plan.parts().size());
        for (ManifestAttachmentPlan.AttachmentPart part : plan.parts()) {
            assertEquals(ManifestAttachmentPlanner.MAX_PART_BYTES, part.content().length);
        }
        assertArrayEquals(manifest, concatenate(plan));
    }

    @Test
    void plannerUsesOwnedBackingSlicesAndPublicContentIsDefensive() {
        byte[] manifest = "aaa\nbbb\n".getBytes(StandardCharsets.UTF_8);
        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner(4, 5, 20)
                .planOwned(PLAYER_ID, TIMESTAMP, manifest);

        assertSame(manifest, plan.parts().get(0).backingForMultipart());
        byte[] exposed = plan.parts().get(0).content();
        exposed[0] = 'z';
        assertEquals('a', plan.parts().get(0).content()[0]);
    }

    @Test
    void rejectsSingleLineThatCannotFitWithoutBreakingIt() {
        byte[] manifest = "12345\n".getBytes(StandardCharsets.UTF_8);

        ManifestAttachmentPlan plan = new ManifestAttachmentPlanner(5, 5, 25)
                .plan(PLAYER_ID, TIMESTAMP, manifest);

        assertFalse(plan.isDeliverable());
        assertTrue(plan.error().contains("line"));
    }

    private static byte[] concatenate(ManifestAttachmentPlan plan) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (ManifestAttachmentPlan.AttachmentPart part : plan.parts()) {
            output.write(part.content());
        }
        return output.toByteArray();
    }
}
