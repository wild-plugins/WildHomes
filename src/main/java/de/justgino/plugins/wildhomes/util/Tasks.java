package de.justgino.plugins.wildhomes.util;

import de.justgino.plugins.wildhomes.WildHomes;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Tasks {

    private final WildHomes plugin;
    private final ExecutorService async = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("WildHomes-Database").daemon(true).factory());

    public Tasks(WildHomes plugin) {
        this.plugin = plugin;
    }

    public void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, ignored -> task.run(), null);
    }

    public void entityLater(Entity entity, Runnable task, long ticks) {
        entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, Math.max(1L, ticks));
    }

    public ScheduledTask entityTimer(Entity entity, Consumer<ScheduledTask> task, long delay, long period) {
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1L, delay), Math.max(1L, period));
    }

    public <T> CompletableFuture<T> supply(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        async.execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Database task failed", t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public void async(Runnable task) {
        async.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Async task failed", t);
            }
        });
    }

    public void shutdown() {
        async.shutdown();
        try {
            if (!async.awaitTermination(5, TimeUnit.SECONDS)) async.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            async.shutdownNow();
        }
    }
}
