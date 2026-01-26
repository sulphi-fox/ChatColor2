package com.sulphate.chatcolor2.schedulers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scheduler abstraction that provides compatibility between Paper and Folia.
 * Automatically detects the server type and uses appropriate scheduling methods.
 */
public class SchedulerAdapter {

    private static final boolean IS_FOLIA;
    private static Method getGlobalRegionSchedulerMethod;
    private static Method getAsyncSchedulerMethod;
    private static Method getEntitySchedulerMethod;
    private static Method runAtFixedRateMethod;
    private static Method runDelayedMethod;
    private static Method runMethod;
    private static Method executeMethod;
    private static Class<?> scheduledTaskClass;
    private static Method cancelMethod;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            folia = true;
            initializeFoliaReflection();
        } catch (ClassNotFoundException e) {
            // Running on Paper/Spigot
        }
        IS_FOLIA = folia;
    }

    private static void initializeFoliaReflection() {
        try {
            Class<?> serverClass = Class.forName("org.bukkit.Server");
            Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> asyncSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            getGlobalRegionSchedulerMethod = serverClass.getMethod("getGlobalRegionScheduler");
            getAsyncSchedulerMethod = serverClass.getMethod("getAsyncScheduler");
            getEntitySchedulerMethod = Class.forName("org.bukkit.entity.Entity").getMethod("getScheduler");

            runAtFixedRateMethod = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            runDelayedMethod = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            runMethod = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            executeMethod = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            cancelMethod = scheduledTaskClass.getMethod("cancel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Folia reflection", e);
        }
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
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                Object scheduledTask = runAtFixedRateMethod.invoke(
                    globalScheduler,
                    plugin,
                    (Consumer<Object>) t -> task.run(),
                    delayTicks,
                    periodTicks
                );
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to schedule repeating task on Folia", e);
            }
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
            try {
                Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(Bukkit.getServer());
                Object scheduledTask = runDelayedMethod.invoke(
                    globalScheduler,
                    plugin,
                    (Consumer<Object>) t -> task.run(),
                    delayTicks
                );
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to schedule delayed task on Folia", e);
            }
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
            try {
                Object asyncScheduler = getAsyncSchedulerMethod.invoke(Bukkit.getServer());
                runMethod.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                throw new RuntimeException("Failed to run async task on Folia", e);
            }
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
            try {
                Object entityScheduler = getEntitySchedulerMethod.invoke(entity);
                executeMethod.invoke(entityScheduler, plugin, (Consumer<Object>) t -> task.run(), null);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run entity task on Folia", e);
            }
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
            try {
                // On Folia, use entity scheduler with delay
                Object entityScheduler = getEntitySchedulerMethod.invoke(entity);
                Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                Method runDelayedMethod = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                Object scheduledTask = runDelayedMethod.invoke(
                    entityScheduler,
                    plugin,
                    (Consumer<Object>) t -> task.run(),
                    null,
                    delayTicks
                );
                return new FoliaTaskWrapper(scheduledTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run delayed entity task on Folia", e);
            }
        } else {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks).getTaskId();
            return new BukkitTaskWrapper(taskId);
        }
    }

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

    private static class FoliaTaskWrapper implements TaskWrapper {
        private final Object scheduledTask;

        FoliaTaskWrapper(Object scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void cancel() {
            try {
                cancelMethod.invoke(scheduledTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel Folia task", e);
            }
        }
    }

}
