package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ResourcePackType;

/** Pure trust matrix; the Forge adapter must independently prove both inputs. */
public final class TrustedResourcePackOriginClassifier {
    public enum ProfileOrigin { MINECRAFT_BUILT_IN, FORGE_MOD_BUNDLED, USER, SERVER, OTHER }
    public enum ImplementationOrigin {
        VANILLA_BUILT_IN, FORGE_MOD, USER_ZIP, USER_DIRECTORY, UNKNOWN_VIRTUAL
    }

    private TrustedResourcePackOriginClassifier() { }

    public static ResourcePackType classify(ProfileOrigin profile,
                                            ImplementationOrigin implementation) {
        if (profile == ProfileOrigin.MINECRAFT_BUILT_IN
                && (implementation == ImplementationOrigin.VANILLA_BUILT_IN
                || implementation == ImplementationOrigin.USER_ZIP
                || implementation == ImplementationOrigin.USER_DIRECTORY)) {
            return ResourcePackType.BUILT_IN;
        }
        if (profile == ProfileOrigin.FORGE_MOD_BUNDLED
                && implementation == ImplementationOrigin.FORGE_MOD) {
            return ResourcePackType.MOD_BUNDLED;
        }
        if (profile == ProfileOrigin.USER && implementation == ImplementationOrigin.USER_ZIP) {
            return ResourcePackType.ZIP;
        }
        if (profile == ProfileOrigin.USER
                && implementation == ImplementationOrigin.USER_DIRECTORY) {
            return ResourcePackType.DIRECTORY;
        }
        if (profile == ProfileOrigin.SERVER && implementation == ImplementationOrigin.USER_ZIP) {
            return ResourcePackType.SERVER_DOWNLOADED;
        }
        return ResourcePackType.UNRESOLVED;
    }
}
