package de.justgino.plugins.wildhomes.home;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;

public final class PlayerHomes {

    private final UUID owner;
    private final String name;
    private final ConcurrentSkipListMap<Integer, Home> slots = new ConcurrentSkipListMap<>();

    public PlayerHomes(UUID owner, String name, List<Home> homes) {
        this.owner = owner;
        this.name = name;
        for (Home home : homes) slots.put(home.slot(), home);
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public Collection<Home> all() {
        return slots.values();
    }

    public int size() {
        return slots.size();
    }

    public @Nullable Home bySlot(int slot) {
        return slots.get(slot);
    }

    public @Nullable Home byName(String name) {
        for (Home home : slots.values()) {
            if (home.name().equalsIgnoreCase(name.trim())) return home;
        }
        return null;
    }

    public int firstFree(int limit) {
        for (int slot = 1; slot <= limit; slot++) {
            if (!slots.containsKey(slot)) return slot;
        }
        return -1;
    }

    public int countWithin(int limit) {
        return slots.headMap(limit, true).size();
    }

    void put(Home home) {
        slots.put(home.slot(), home);
    }

    void remove(int slot) {
        slots.remove(slot);
    }
}
