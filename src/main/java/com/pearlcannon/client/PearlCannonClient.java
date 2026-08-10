package com.pearlcannon.client;

import com.pearlcannon.network.PCCNetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class PearlCannonClient implements ClientModInitializer {

    private static KeyMapping openCalculatorKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("pearl_cannon_calculator", "category")
        );

        openCalculatorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.pearl_cannon_calculator.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TrajectoryRenderer.tick();
            while (openCalculatorKey.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new CannonCalculatorScreen());
                }
            }
        });

        PCCNetworkHandler.initialize();
        ClientNetworkReceiver.initialize();
    }
}
