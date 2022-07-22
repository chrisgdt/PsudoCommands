package me.zombie_striker.psudocommands;

import me.lucko.commodore.PsudoCommodoreExtension;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import co.aikar.timings.Timing;
import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerCommandException;

import java.util.Arrays;

public class DispatchCommandPaperHook {

    public static void dispatchCommandPaper(CommandSender sender, String commandstr, Command command, String sentCommandLabel, String[] args) {
        if (command.timings == null) {
            command.timings = co.aikar.timings.TimingsManager.getCommandTiming(null, command);
        }
        try {
            try (Timing ignored = command.timings.startTiming()) {
                PsudoCommodoreExtension.executeIgnorePerms(command, sender, sentCommandLabel, Arrays.copyOfRange(args, 1, args.length));
            }
        } catch (CommandException ex) {
            Bukkit.getServer().getPluginManager().callEvent(new ServerExceptionEvent(new ServerCommandException(ex, command, sender, args))); // Paper
            throw ex;
        } catch (Throwable ex) {
            String msg = "Unhandled exception executing '" + commandstr + "' in " + command;
            Bukkit.getServer().getPluginManager().callEvent(new ServerExceptionEvent(new ServerCommandException(ex, command, sender, args))); // Paper
            throw new CommandException(msg, ex);
        }
    }
}
