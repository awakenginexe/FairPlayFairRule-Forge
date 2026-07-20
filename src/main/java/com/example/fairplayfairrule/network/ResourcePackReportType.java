package com.example.fairplayfairrule.network;

/** Distinguishes initial policy validation from reload-time session locking. */
public enum ResourcePackReportType {
    JOIN(0),
    RELOAD(1);

    private final int networkId;

    ResourcePackReportType(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static ResourcePackReportType fromNetworkId(int id) {
        for (ResourcePackReportType type : values()) {
            if (type.networkId == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown resource-pack report type: " + id);
    }
}
