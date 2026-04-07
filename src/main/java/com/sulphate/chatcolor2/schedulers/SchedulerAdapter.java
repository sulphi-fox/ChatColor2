package com.sulphate.chatcolor2.schedulers;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Scheduler abstraction that provides compatibility between Paper and Folia.
 * Automatically detects the server type and uses appropriate scheduling methods.
 *
 * Folia-specific code is isolated in a static inner class so that its classes
 * are only loaded (and linked) when actually called on a Folia server. This
 * avoids reflection while remaining safe on non-Folia servers where the Folia
 * classes do not exist at runtime.
 */
public class SchedulerAdapter {

    private static final boolean IS_FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            folia = true;
        } catch (ClassNotFoundException e) {
            // Running on Paper/Spigot
        }
        IS_FOLIA = folia;
    }

    /**
     * Check if the server is running Folia.
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Runs a task repeatedly on the global region (Folia) or main thread (Paper).
     *
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Delay before first execution in ticks
     * @param periodTicks Period between executions in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            return FoliaScheduling.runTimer(plugin, task, delayTicks, periodTicks);
        } else {
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }
    }

    /**
     * Runs a task once after a delay on the global region (Folia) or main thread (Paper).
     *
     * @param plugin The plugin instance
     * @param task The task to run
     * @param delayTicks Delay before execution in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            return FoliaScheduling.runLater(plugin, task, delayTicks);
        } else {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }
    }

    /**
     * Runs a task asynchronously.
     *
     * @param plugin The plugin instance
     * @param task The task to run
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            FoliaScheduling.runAsync(plugin, task);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs a task on the entity's scheduler (Folia) or main thread (Paper).
     * This is used for entity-specific operations.
     *
     * @param plugin The plugin instance
     * @param entity The entity
     * @param task The task to run
     */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            FoliaScheduling.runForEntity(plugin, entity, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task after a delay on the entity's scheduler (Folia) or main thread (Paper).
     * This is used for player-specific delayed operations.
     *
     * @param plugin The plugin instance
     * @param entity The entity
     * @param task The task to run
     * @param delayTicks Delay before execution in ticks
     * @return A task wrapper that can be cancelled
     */
    public static TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            return FoliaScheduling.runForEntity(plugin, entity, task, delayTicks);
        } else {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }
    }

    // -------------------------------------------------------------------------
    // Task wrapper interface and implementations
    // -------------------------------------------------------------------------

    /**
     * Wrapper interface for scheduled tasks that can be cancelled.
     */
    public interface TaskWrapper {
        void cancel();
    }

    private static class BukkitTaskWrapper implements TaskWrapper {
        private final int taskId;

        BukkitTaskWrapper(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public void cancel() {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    // -------------------------------------------------------------------------
    // Folia-specific scheduling, isolated in its own class so that Folia types
    // are only resolved by the JVM when this class is actually loaded (i.e.
    // only on Folia servers).
    // -------------------------------------------------------------------------

    private static final class FoliaScheduling {

        static TaskWrapper runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            Consumer<ScheduledTask> consumer = scheduledTask -> task.run();
            ScheduledTask handle = Bukkit.getServer()
                    .getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, consumer, delayTicks, periodTicks);
            return new FoliaTaskWrapper(handle);
        }

        static TaskWrapper runLater(Plugin plugin, Runnable task, long delayTicks) {
            Consumer<ScheduledTask> consumer = scheduledTask -> task.run();
            ScheduledTask handle = Bukkit.getServer()
                    .getGlobalRegionScheduler()
                    .runDelayed(plugin, consumer, delayTicks);
            return new FoliaTaskWrapper(handle);
        }

        static void runAsync(Plugin plugin, Runnable task) {
            Consumer<ScheduledTask> consumer = scheduledTask -> task.run();
            Bukkit.getServer()
                    .getAsyncScheduler()
                    .runNow(plugin, consumer);
        }

        static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
            Consumer<ScheduledTask> consumer = scheduledTask -> task.run();
            entity.getScheduler().run(plugin, consumer, null);
        }

        static TaskWrapper runForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
            Consumer<ScheduledTask> consumer = scheduledTask -> task.run();
            ScheduledTask handle = entity.getScheduler()
                    .runDelayed(plugin, consumer, null, delayTicks);
            return new FoliaTaskWrapper(handle);
        }

        private static class FoliaTaskWrapper implements TaskWrapper {
            private final ScheduledTask handle;

            FoliaTaskWrapper(ScheduledTask handle) {
                this.handle = handle;
            }

            @Override
            public void cancel() {
                handle.cancel();
            }
        }
    }

}
