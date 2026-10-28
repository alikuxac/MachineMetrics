package com.alikuxac.machinemetrics.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflect {

    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Method>> TYPED_METHOD_CACHE = new ConcurrentHashMap<>();

    private static final Method MISSING_METHOD;
    private static final Field MISSING_FIELD;

    static {
        Method m = null;
        Field f = null;
        try {
            m = Reflect.class.getDeclaredMethod("missSentinel");
            f = Reflect.class.getDeclaredField("METHOD_CACHE");
        } catch (Throwable ignored) {
        }
        MISSING_METHOD = m;
        MISSING_FIELD = f;
    }

    private static void missSentinel() {
    }

    private Reflect() {
    }

    public static Method findMethod(Class<?> clazz, String... names) {
        if (clazz == null || names == null || names.length == 0) {
            return null;
        }
        Map<String, Method> slot = METHOD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        String key = cacheKey(names);
        if (slot.containsKey(key)) {
            Method cached = slot.get(key);
            return cached == MISSING_METHOD ? null : cached;
        }
        Method resolved = resolveMethod(clazz, names);
        slot.put(key, resolved != null ? resolved : MISSING_METHOD);
        return resolved;
    }

    public static Method findMethodTyped(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        if (clazz == null || names == null) {
            return null;
        }
        Map<String, Method> slot = TYPED_METHOD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        String key = typedCacheKey(paramTypes, names);
        if (slot.containsKey(key)) {
            Method cached = slot.get(key);
            return cached == MISSING_METHOD ? null : cached;
        }
        Method resolved = resolveMethodTyped(clazz, paramTypes, names);
        slot.put(key, resolved != null ? resolved : MISSING_METHOD);
        return resolved;
    }

    private static Method resolveMethodTyped(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (String name : names) {
                try {
                    Method m = curr.getDeclaredMethod(name, paramTypes);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    public static Field findField(Class<?> clazz, String... names) {
        if (clazz == null || names == null || names.length == 0) {
            return null;
        }
        Map<String, Field> slot = FIELD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        String key = cacheKey(names);
        if (slot.containsKey(key)) {
            Field cached = slot.get(key);
            return cached == MISSING_FIELD ? null : cached;
        }
        Field resolved = resolveField(clazz, names);
        slot.put(key, resolved != null ? resolved : MISSING_FIELD);
        return resolved;
    }

    public static Float invokeFloat(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            Object res = method.invoke(target);
            if (res instanceof Number num) {
                return num.floatValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Long invokeLong(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            Object res = method.invoke(target);
            if (res instanceof Number num) {
                return num.longValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Integer invokeInt(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            Object res = method.invoke(target);
            if (res instanceof Number num) {
                return num.intValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Object invokeSafe(Method method, Object target, Object... args) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Float getFloatField(Object target, String... names) {
        return getNumber(target, Float.class, names);
    }


    public static Long getLongField(Object target, String... names) {
        return getNumber(target, Long.class, names);
    }

    public static Integer getIntField(Object target, String... names) {
        return getNumber(target, Integer.class, names);
    }

    public static boolean getBooleanField(Object target, boolean fallback, String... names) {
        if (target == null) {
            return fallback;
        }
        Field f = findField(target.getClass(), names);
        if (f == null) {
            return fallback;
        }
        try {
            return f.getBoolean(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static Object getField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Field f = findField(target.getClass(), names);
        if (f == null) {
            return null;
        }
        try {
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static <N extends Number> N getNumber(Object target, Class<N> type, String... names) {
        if (target == null) {
            return null;
        }
        Field f = findField(target.getClass(), names);
        if (f == null) {
            return null;
        }
        try {
            Object res = f.get(target);
            if (type == Float.class && res instanceof Number num) {
                return type.cast(num.floatValue());
            }
            if (type == Long.class && res instanceof Number num) {
                return type.cast(num.longValue());
            }
            if (type == Integer.class && res instanceof Number num) {
                return type.cast(num.intValue());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method resolveMethod(Class<?> clazz, String... names) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (String name : names) {
                try {
                    Method m = curr.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static Field resolveField(Class<?> clazz, String... names) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (String name : names) {
                try {
                    Field f = curr.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (Throwable ignored) {
                }
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static String cacheKey(String... names) {
        return String.join("\u0000", names);
    }

    private static String typedCacheKey(Class<?>[] paramTypes, String... names) {
        StringBuilder sb = new StringBuilder();
        if (paramTypes != null) {
            for (Class<?> p : paramTypes) {
                sb.append(p != null ? p.getName() : "null").append('\u0001');
            }
        }
        sb.append('\u0000');
        sb.append(String.join("\u0000", names));
        return sb.toString();
    }
}