package com.alikuxac.machinemetrics.neoforge.platform;

import com.alikuxac.machinemetrics.platform.config.Config;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeConfigFactory implements Config.Factory {

    public static void install() {
        Config.setFactory(new NeoForgeConfigFactory());
    }

    @Override
    public Config.Builder builder() {
        return new BuilderImpl(new ModConfigSpec.Builder());
    }

    private static final class BuilderImpl implements Config.Builder {
        private final ModConfigSpec.Builder delegate;

        BuilderImpl(ModConfigSpec.Builder delegate) {
            this.delegate = delegate;
        }

        @Override public Config.Builder comment(String... comments) { delegate.comment(comments); return this; }
        @Override public Config.Builder push(String path) { delegate.push(path); return this; }
        @Override public Config.Builder pop() { delegate.pop(); return this; }

        @Override
        public Config.Value<Boolean> define(String key, boolean defaultValue) {
            return wrap(delegate.define(key, defaultValue));
        }

        @Override
        public Config.Value<Integer> defineInRange(String key, int defaultValue, int min, int max) {
            return wrap(delegate.defineInRange(key, defaultValue, min, max));
        }

        @Override
        public Config.Value<Double> defineInRange(String key, double defaultValue, double min, double max) {
            return wrap(delegate.defineInRange(key, defaultValue, min, max));
        }

        @Override
        public Config.Value<String> define(String key, String defaultValue) {
            return wrap(delegate.define(key, defaultValue));
        }

        @Override
        public <E extends Enum<E>> Config.Value<E> defineEnum(String key, E defaultValue) {
            return wrap(delegate.defineEnum(key, defaultValue));
        }

        @Override
        public Config.Spec build() {
            ModConfigSpec spec = delegate.build();
            return new Config.Spec() {
                @Override public Object nativeSpec() { return spec; }
                @Override public void save() { spec.save(); }
            };
        }

        private static <T> Config.Value<T> wrap(ModConfigSpec.ConfigValue<T> value) {
            return new Config.Value<>() {
                @Override public T get() { return value.get(); }
                @Override public void set(T v) { value.set(v); }
            };
        }
    }
}