package com.eyeofthestorm.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Registers the built-in NeoForge configuration screen on the client only. */
public final class ClientConfigScreenRegistration {
    private ClientConfigScreenRegistration() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
