package dev.visoftware.artiferrium.service;

@FunctionalInterface
public interface PlayerCountProvider {
    int getCurrentPlayerCount();
}
