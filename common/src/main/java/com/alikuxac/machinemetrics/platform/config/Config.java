package com.alikuxac.machinemetrics.platform.config;

import java.util.List;
import java.util.function.Predicate;
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

        Value<List<? extends String>> defineList(String key, List<? extends String> defaultValue, Predicate<Object> elementValidator);

        Spec build();
    }

    public interface Factory {
        Builder builder();
    }

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