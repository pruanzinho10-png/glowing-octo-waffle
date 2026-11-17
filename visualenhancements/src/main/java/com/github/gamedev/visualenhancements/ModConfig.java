package com.github.gamedev.visualenhancements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Salva módulos ligados e a cor da aura em config/visualenhancements.json. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    private Map<String, Boolean> modules = new LinkedHashMap<>();
    private String auraColor = AuraColor.YELLOW.id;

    private ModConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("visualenhancements.json");
    }

    public static void load() {
        Path path = file();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null && loaded.modules != null) {
                    instance = loaded;
                }
            } catch (IOException | JsonParseException e) {
                VisualEnhancementsClient.LOGGER.warn("Não foi possível ler a configuração, usando padrões", e);
            }
        }
        for (VisualModule module : VisualModule.values()) {
            instance.modules.putIfAbsent(module.id(), true);
        }
        if (instance.auraColor == null) {
            instance.auraColor = AuraColor.YELLOW.id;
        }
        save();
    }

    public static void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            VisualEnhancementsClient.LOGGER.warn("Não foi possível salvar a configuração", e);
        }
    }

    public static boolean isEnabled(VisualModule module) {
        return instance.modules.getOrDefault(module.id(), true);
    }

    public static void setEnabled(VisualModule module, boolean enabled) {
        instance.modules.put(module.id(), enabled);
        save();
    }

    public static AuraColor auraColor() {
        return AuraColor.byId(instance.auraColor);
    }

    public static void setAuraColor(AuraColor color) {
        instance.auraColor = color.id;
        save();
    }
}
