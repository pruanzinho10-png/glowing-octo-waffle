package com.github.gamedev.visualenhancements;

import com.github.gamedev.visualenhancements.gui.ModulesScreen;
import com.github.gamedev.visualenhancements.render.HitAuraRenderer;
import com.github.gamedev.visualenhancements.render.Pipelines;
import com.github.gamedev.visualenhancements.render.RainStreaksRenderer;
import com.github.gamedev.visualenhancements.render.WetGroundRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint do mod (somente cliente). Só registra os eventos; a lógica fica
 * nas outras classes.
 */
public class VisualEnhancementsClient implements ClientModInitializer {
    public static final String MOD_ID = "visualenhancements";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        Pipelines.init();

        // Tecla para abrir o menu (padrão: Shift direito, configurável em Controles)
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.visualenhancements.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ModulesScreen());
                }
            }
            WeatherState.tick(client);
            if (client.level == null) {
                HitAuraManager.clear();
            } else {
                HitAuraManager.tick();
            }
        });

        // Ataques do jogador local
        AttackEntityCallback.EVENT.register(HitAuraManager::onAttack);

        // Riscos de chuva na tela (HUD)
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "rain_streaks"),
                (graphics, deltaTracker) -> RainStreaksRenderer.render(graphics)
        );

        // Efeitos no mundo (3D)
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            WetGroundRenderer.render(context);
            HitAuraRenderer.render(context);
        });

        // Libera os buffers de GPU ao fechar o jogo
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            WetGroundRenderer.close();
            HitAuraRenderer.close();
        });

        LOGGER.info("Visual Enhancements carregado");
    }
}
