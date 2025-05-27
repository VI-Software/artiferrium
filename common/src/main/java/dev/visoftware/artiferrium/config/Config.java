package dev.visoftware.artiferrium.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    private static final Config INSTANCE = new Config();
    private String serverKey = "";
    private boolean debug = false;
    private String kickMessage = "You are not allowed to join this private server";
    private final Map<String, Map<String, String>> sections = new HashMap<>();

    private Config() {} // Singleton

    public static Config get() {
        return INSTANCE;
    }

    public String getServerKey() {
        return serverKey;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public void load(Path configPath) throws IOException {
        if (!Files.exists(configPath)) {
            createDefaultConfig(configPath);
        }

        List<String> lines = Files.readAllLines(configPath);
        Map<String, String> currentSection = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                String sectionName = line.substring(1, line.length() - 1);
                currentSection = new HashMap<>();
                sections.put(sectionName, currentSection);
            } else if (currentSection != null && line.contains("=")) {
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                currentSection.put(key, value);
            }
        }

        Map<String, String> serverSection = sections.get("server");
        if (serverSection != null) {
            serverKey = serverSection.getOrDefault("key", "");
            debug = Boolean.parseBoolean(serverSection.getOrDefault("debug", "false"));
            kickMessage = serverSection.getOrDefault("kick_message", kickMessage);
        }
    }

    private void createDefaultConfig(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        String defaultConfig = """
            # Artiferrium Server Configuration
            
            [server]
            # The server key from VI Software
            key = ""
            # Enable debug logging
            debug = false
            # Message shown to players who are not allowed to join the private server
            kick_message = "You are not allowed to join this private server"
            """;
        Files.writeString(configPath, defaultConfig);
    }
}
