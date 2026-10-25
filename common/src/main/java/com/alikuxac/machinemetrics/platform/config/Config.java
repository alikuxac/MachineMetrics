package com.alikuxac.machinemetrics.platform.config;

import java.util.function.Supplier;

public final class Config {

    public interface Value<T> extends Supplier<T> {
        T get();

        void set(T value);
    }

    public interface Spec {
        Object nativeSpec();

        void save();
    }

    public interface Builder {
        Builder comment(String... comments);

        Builder push(String path);

        Builder pop();

        Value<Boolean> define(String key, boolean defaultValue);

        Value<Integer> defineInRange(String key, int defaultValue, int min, int max);

        Value<Double> defineInRange(String key, double defaultValue, double min, double max);

        Value<String> define(String key, String defaultValue);

        <E extends Enum<E>> Value<E> defineEnum(String key, E defaultValue);

        Spec build();
    }

    public interface Factory {
        Builder builder();
    }

    // ponytail: the loader entrypoint MUST call setFactory() before any Config.* class
    // (ClientConfig/ServerConfig) is class-loaded, because their SPEC builds in a static initializer.
    private static Factory factory = () -> {
        throw new IllegalStateException(
                "Config factory not installed; the loader entrypoint must call setFactory() "
                        + "before referencing ClientConfig/ServerConfig.");
    };

    private Config() {}

    public static void setFactory(Factory impl) {
        factory = impl;
    }

    public static Builder builder() {
        return factory.builder();
    }
}