package de.justgino.plugins.wildhomes.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record Home(UUID owner, int slot, String name, String server, String world,
                   double x, double y, double z, float yaw, float pitch,
                   @Nullable String icon, long created) {

    public static Home at(UUID owner, int slot, String name, String server, Location location) {
        return new Home(owner, slot, name, server, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                null, System.currentTimeMillis());
    }

    public Home withName(String newName) {
        return new Home(owner, slot, newName, server, world, x, y, z, yaw, pitch, icon, created);
    }

    public Home withIcon(@Nullable String newIcon) {
        return new Home(owner, slot, name, server, world, x, y, z, yaw, pitch, newIcon, created);
    }

    public Home movedTo(String newServer, Location location) {
        return new Home(owner, slot, name, newServer, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), icon, created);
    }

    public @Nullable Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
