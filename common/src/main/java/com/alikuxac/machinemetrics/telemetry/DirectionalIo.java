package com.alikuxac.machinemetrics.telemetry;

public enum DirectionalIo {
    NONE((byte) 0, "none"),
    INPUT((byte) 1, "input"),
    OUTPUT((byte) 2, "output"),
    BIDIRECTIONAL((byte) 3, "bidirectional");

    private final byte id;
    private final String label;

    DirectionalIo(byte id, String label) {
        this.id = id;
        this.label = label;
    }

    public byte getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public static DirectionalIo fromId(byte id) {
        for (DirectionalIo val : values()) {
            if (val.id == id) {
                return val;
            }
        }
        return NONE;
    }
}

