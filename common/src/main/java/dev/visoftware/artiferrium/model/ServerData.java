package dev.visoftware.artiferrium.model;

import com.google.gson.JsonObject;

public class ServerData {
    private final String id;
    private final String name;
    private final String description;
    private final boolean isPrivate;
    private final String ownerUuid;
    private final String ownerName;
    private final String language;
    private int playerCount;
    private long lastHeartbeat;

    public ServerData(JsonObject serverInfo) {
        JsonObject server = serverInfo.get("server").getAsJsonObject();
        this.id = server.get("id").getAsString();
        this.name = server.get("name").getAsString();
        this.description = server.has("description") ? server.get("description").getAsString() : "";
        this.isPrivate = server.get("private").getAsBoolean();
        this.ownerUuid = server.get("owner_uuid").getAsString();
        this.ownerName = server.get("owner_name").getAsString();
        this.language = server.get("lang").getAsString();
        this.playerCount = 0;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getLanguage() {
        return language;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    @Override
    public String toString() {
        return "ServerData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", isPrivate=" + isPrivate +
                ", ownerName='" + ownerName + '\'' +
                ", language='" + language + '\'' +
                ", playerCount=" + playerCount +
                ", lastHeartbeat=" + lastHeartbeat +
                '}';
    }
}
