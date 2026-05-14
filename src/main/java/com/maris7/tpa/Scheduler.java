package com.maris7.tpa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Scheduler {
    private Scheduler() {}

    public interface TaskHandle {
        void cancel();
    }

    public static TaskHandle globalLater(Plugin plugin, Runnable task, long ticks) {
        if (ticks <= 0) {
            task.run();
            return () -> {};
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            Object scheduled = runDelayed.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), ticks);
            return cancellable(scheduled);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
            return bukkitTask::cancel;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to schedule global delayed task", e);
        }
    }

    public static TaskHandle globalTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            Object scheduled = runAtFixedRate.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), initialDelayTicks, periodTicks);
            return cancellable(scheduled);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
            return bukkitTask::cancel;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to schedule global repeating task", e);
        }
    }

    public static TaskHandle async(Plugin plugin, Runnable task) {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method runNow = scheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
            Object scheduled = runNow.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run());
            return cancellable(scheduled);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return bukkitTask::cancel;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to schedule async task", e);
        }
    }

    public static void entityNow(Plugin plugin, Player player, Runnable task) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), null);
        } catch (NoSuchMethodException ignored) {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to schedule entity task", e);
        }
    }

    public static void teleport(Plugin plugin, Player player, Location destination) {
        entityNow(plugin, player, () -> {
            try {
                Method teleportAsync = player.getClass().getMethod("teleportAsync", Location.class);
                teleportAsync.invoke(player, destination);
            } catch (NoSuchMethodException ignored) {
                player.teleport(destination);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Unable to teleport player", e);
            }
        });
    }

    private static TaskHandle cancellable(Object scheduledTask) {
        return () -> {
            try {
                Method cancel = scheduledTask.getClass().getMethod("cancel");
                cancel.setAccessible(true);
                cancel.invoke(scheduledTask);
            } catch (NoSuchMethodException e) {
                // Some scheduler implementations do not expose a cancel method.
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Unable to cancel task", e);
            }
        };
    }
}
