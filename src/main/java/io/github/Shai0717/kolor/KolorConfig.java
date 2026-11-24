package io.github.Shai0717.kolor;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class KolorConfig {
    private KolorConfig() {}

    public static YamlConfiguration load(File file, Class<?> resourceOwner, String resourcePath) {
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("Cannot create configuration directory: " + parent);
            }
            try (InputStream resource = resourceOwner.getResourceAsStream(normalizedPath)) {
                if (resource == null) {
                    throw new IllegalStateException("Missing configuration resource: " + normalizedPath);
                }
                java.nio.file.Files.copy(resource, file.toPath());
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create configuration file: " + file, e);
            }
        }

        YamlConfiguration values = new YamlConfiguration();
        try {
            values.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalArgumentException("Invalid configuration: " + file, e);
        }
        try (InputStream resource = resourceOwner.getResourceAsStream(normalizedPath)) {
            if (resource == null) throw new IllegalStateException("Missing configuration resource: " + normalizedPath);
            YamlConfiguration defaults = new YamlConfiguration();
            try {
                defaults.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
            } catch (org.bukkit.configuration.InvalidConfigurationException e) {
                throw new IllegalStateException("Invalid configuration resource: " + normalizedPath, e);
            }
            values.setDefaults(defaults);
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read configuration resource: " + resourcePath, e);
        }
    }
}
