package com.eyeofthestorm.client;

/**
 * Optional Iris detection via reflection — no hard dependency.
 * When a shader pack is active, Iris skips non-{@code ExtendedShader}
 * programs (see MixinShaderInstance), which is why a custom core shader
 * vanishes under Complementary.
 */
public final class IrisCompat {
    private static final boolean IRIS_PRESENT;
    private static final Object IRIS_API;
    private static final java.lang.reflect.Method IS_SHADER_PACK_IN_USE;

    static {
        boolean present = false;
        Object api = null;
        java.lang.reflect.Method method = null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            method = apiClass.getMethod("isShaderPackInUse");
            present = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Iris not installed
        }
        IRIS_PRESENT = present;
        IRIS_API = api;
        IS_SHADER_PACK_IN_USE = method;
    }

    private IrisCompat() {}

    /** True when Iris is loaded and a shader pack is currently applied. */
    public static boolean isShaderPackInUse() {
        if (!IRIS_PRESENT || IRIS_API == null || IS_SHADER_PACK_IN_USE == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(IS_SHADER_PACK_IN_USE.invoke(IRIS_API));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
