package com.example.fairplayfairrule.client;

import com.example.fairplayfairrule.resourcepack.ResourcePackType;

import java.nio.file.Path;

/** Client-local resolver output. The path must never be serialized or logged. */
public record ResolvedResourcePack(String displayName, ResourcePackType type, Path localPath) {
}
