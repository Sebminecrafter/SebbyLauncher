package io.github.sebminecrafter.sebbylauncher;

/**
 * The account details the game needs to start.
 *
 * @param name        player name
 * @param uuid        player UUID without dashes
 * @param accessToken Minecraft access token
 */
public record Session(String name, String uuid, String accessToken) {
    /**
     * Leaves out the access token so it can't end up in logs.
     */
    @Override
    public String toString() {
        return "Session[name=" + name + "]";
    }
}
