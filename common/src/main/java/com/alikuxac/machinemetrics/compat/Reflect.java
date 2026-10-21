package com.alikuxac.machinemetrics.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflect {

    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private Reflect() {
    }

    public static Method findMethod(Class<?> clazz, String... names) {
        if (clazz == null || names == null || names.length == 0) {
            return null;
        }
        Map<String, Method> slot = METHOD_CACHE.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
        String key = cacheKey(names);
        Method existing = slot.get(key);
        if (existing != null) {
            return existing;
        }
        Method resolved = resolveMethod(clazz, names);
        if (resolved != null) {
            slot.put(key, resolved);
        }
        return resolved;
    }

    public static Method findMethodTyped(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        if (clazz == null || names == null) {
            return null;
        }
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
        Field existing = slot.get(key);
        if (existing != null) {
            return existing;
        }
        Field resolved = resolveField(clazz, names);
        if (resolved != null) {
            slot.put(key, resolved);
        }
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
}