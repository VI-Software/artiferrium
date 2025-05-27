package dev.visoftware.artiferrium.model;

import java.time.LocalDateTime;

public class AllowedUser {
    private final String uuid;
    private final LocalDateTime expiryDate;

    public AllowedUser(String uuid, LocalDateTime expiryDate) {
        this.uuid = uuid;
        this.expiryDate = expiryDate;
    }

    public String getUuid() {
        return uuid;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public boolean isValid() {
        return expiryDate == null || LocalDateTime.now().isBefore(expiryDate);
    }
}
