package me.zombie_striker.psudocommands;

import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class PsudoCommands extends JavaPlugin {

    public void onEnable() {
        PsudoCommandExecutor executor = new PsudoCommandExecutor(this);

        PluginCommand[] commands = new PluginCommand[]{ getCommand("psudo"), getCommand("psudouuid"),
                getCommand("psudoas"), getCommand("psudoasraw"),
                getCommand("psudoasop"), getCommand("psudoasconsole") };

        try {
            if (!PsudoReflection.USING_PAPER || PsudoReflection.runningBelowVersion("1.20.4")) { // Using Spigot or previous Paper command behavior
                getLogger().log(Level.INFO, "Because you are running a Spigot build, or 1.20.4 or before, registering commands through old Brigadier !");

                for (PluginCommand command : commands) {
                    command.setExecutor(executor);
                    command.setTabCompleter(executor);
                    getLogger().log(Level.INFO, "Registered command " + command.getName() + " for vanilla usage.");
                }

                try {
                    CommandDispatcher<Object> dispatcher = PsudoReflection.getCommandDispatcher();
                    for (PluginCommand command : commands) {
                        dispatcher.register(CommandUtils.buildSpigotBrigadierCommand(executor, command, PsudoCommandExecutor.PsudoCommandType.getType(command)));
                        getLogger().log(Level.INFO, "Registered command " + command.getName() + " for /execute (through Brigadier).");
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().log(Level.SEVERE, "An error occurred while using old Brigadier, PsudoCommands does not support Spigot anymore and some features might be broken.");
                    getLogger().log(Level.SEVERE, e.getMessage());
                }

            } else {
                getLogger().log(Level.INFO, "Using new Paper Brigadier registering !");
                PaperCommandRegistering.registerPaperBrigadierCommand(this, executor, commands);
            }
        } catch (ExceptionInInitializerError e) {
            getLogger().log(Level.SEVERE, "Could not load reflection ! Is your version (" + Bukkit.getVersion() + ") supported ? -> " + e.getMessage());
            throw new ExceptionInInitializerError(e);
        }
    }
}