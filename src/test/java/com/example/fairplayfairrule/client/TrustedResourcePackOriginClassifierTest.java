package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ResourcePackType;
import org.junit.jupiter.api.Test;

import static com.example.fairplayfairrule.client.TrustedResourcePackOriginClassifier.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustedResourcePackOriginClassifierTest {
    @Test
    void trustsOnlyProvenSourceAndImplementationPairs() {
        assertEquals(ResourcePackType.BUILT_IN,
                classify(ProfileOrigin.MINECRAFT_BUILT_IN, ImplementationOrigin.VANILLA_BUILT_IN));
        assertEquals(ResourcePackType.BUILT_IN,
                classify(ProfileOrigin.MINECRAFT_BUILT_IN, ImplementationOrigin.USER_ZIP));
        assertEquals(ResourcePackType.MOD_BUNDLED,
                classify(ProfileOrigin.FORGE_MOD_BUNDLED, ImplementationOrigin.FORGE_MOD));
        assertEquals(ResourcePackType.ZIP,
                classify(ProfileOrigin.USER, ImplementationOrigin.USER_ZIP));
        assertEquals(ResourcePackType.DIRECTORY,
                classify(ProfileOrigin.USER, ImplementationOrigin.USER_DIRECTORY));
        assertEquals(ResourcePackType.SERVER_DOWNLOADED,
                classify(ProfileOrigin.SERVER, ImplementationOrigin.USER_ZIP));
    }

    @Test
    void trustedLookingImplementationWithWrongSourceFailsClosed() {
        assertEquals(ResourcePackType.UNRESOLVED,
                classify(ProfileOrigin.USER, ImplementationOrigin.VANILLA_BUILT_IN));
        assertEquals(ResourcePackType.UNRESOLVED,
                classify(ProfileOrigin.USER, ImplementationOrigin.FORGE_MOD));
        assertEquals(ResourcePackType.UNRESOLVED,
                classify(ProfileOrigin.OTHER, ImplementationOrigin.USER_ZIP));
        assertEquals(ResourcePackType.UNRESOLVED,
                classify(ProfileOrigin.SERVER, ImplementationOrigin.UNKNOWN_VIRTUAL));
    }
}
