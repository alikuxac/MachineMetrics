package com.alikuxac.machinemetrics.platform.config;

public final class ConfigReload {

    public interface Handler {
        void reloadClient();

        void reloadServer();
    }

    private static volatile Handler handler = new Handler() {
        @Override public void reloadClient() {}
        @Override public void reloadServer() {}
    };

    private ConfigReload() {
    }

    public static void setHandler(Handler handler) {
        ConfigReload.handler = handler != null ? handler : new Handler() {
            @Override public void reloadClient() {}
            @Override public void reloadServer() {}
        };
    }

    public static void reloadClient() {
        try {
            handler.reloadClient();
        } catch (Throwable ignored) {
        }
    }

    public static void reloadServer() {
        try {
            handler.reloadServer();
        } catch (Throwable ignored) {
        }
    }
}
