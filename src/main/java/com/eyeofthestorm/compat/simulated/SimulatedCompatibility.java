package com.eyeofthestorm.compat.simulated;

import com.eyeofthestorm.EyeOfTheStormMod;
import com.eyeofthestorm.registry.ModItems;
import com.eyeofthestorm.storm.StormData;
import dev.simulated_team.simulated.service.SimModCompatibilityService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class SimulatedCompatibility implements SimModCompatibilityService {
    @Override
    public void init() {
        try {
            registerNavigationTarget();
            EyeOfTheStormMod.LOGGER.info("Registered Storm Compass with Create Aeronautics navigation table");
        } catch (ReflectiveOperationException exception) {
            EyeOfTheStormMod.LOGGER.warn("Create Aeronautics navigation compat failed to register", exception);
        }
    }

    @Override
    public String getModId() {
        return EyeOfTheStormMod.MOD_ID;
    }

    private static void registerNavigationTarget() throws ReflectiveOperationException {
        ClassLoader loader = SimulatedCompatibility.class.getClassLoader();
        Class<?> navigationTargetType = Class.forName(
                "dev.simulated_team.simulated.content.blocks.nav_table.navigation_target.NavigationTarget",
                true,
                loader
        );
        Class<?> nonNullSupplierType = Class.forName(
                "com.tterrag.registrate.util.nullness.NonNullSupplier",
                true,
                loader
        );

        Object navigationTarget = Proxy.newProxyInstance(loader, new Class<?>[] {navigationTargetType}, (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (args != null && "getTarget".equals(method.getName()) && args.length == 2) {
                return resolveStormTarget(args[0]);
            }
            return defaultNavigationTargetValue(method);
        });

        Object supplier = Proxy.newProxyInstance(loader, new Class<?>[] {nonNullSupplierType}, (proxy, method, args) -> {
            if ("get".equals(method.getName())) {
                return navigationTarget;
            }
            return null;
        });

        Class<?> simulated = Class.forName("dev.simulated_team.simulated.Simulated", true, loader);
        Object registrate = simulated.getMethod("getRegistrate").invoke(null);
        Method navTarget = registrate.getClass().getMethod(
                "navTarget",
                String.class,
                nonNullSupplierType,
                Class.forName("net.minecraft.world.level.ItemLike", true, loader)
        );
        navTarget.invoke(registrate, "storm_compass", supplier, ModItems.STORM_COMPASS);
    }

    private static Vec3 resolveStormTarget(Object table) throws ReflectiveOperationException {
        Method getLevel = table.getClass().getMethod("getLevel");
        Object levelObj = getLevel.invoke(table);
        if (!(levelObj instanceof Level level) || level.getServer() == null) {
            return null;
        }
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }
        StormData data = StormData.get(overworld);
        if (!data.initialized || !data.active) {
            return null;
        }
        return new Vec3(data.centerX, data.centerY, data.centerZ);
    }

    private static Object defaultNavigationTargetValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == float.class) {
            return switch (method.getName()) {
                case "getDeadzone" -> 2f;
                case "getModulatingRange" -> 200f;
                case "getMaxRange" -> 0f;
                default -> 0f;
            };
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == double.class) {
            return -1.0d;
        }
        return null;
    }
}
