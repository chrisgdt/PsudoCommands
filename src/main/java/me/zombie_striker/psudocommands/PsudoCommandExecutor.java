package me.zombie_striker.psudocommands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PsudoCommandExecutor implements CommandExecutor, TabCompleter {

    private final JavaPlugin psudoCommands;

    public PsudoCommandExecutor(JavaPlugin psudoCommands) {
        this.psudoCommands = psudoCommands;
    }

    public enum PsudoCommandType {
        PSUDO,
        PSUDO_AS,
        PSUDO_AS_RAW,
        PSUDO_UUID;

        public boolean useBaseSender() {
            return this == PSUDO || this == PSUDO_UUID;
        }

        public static PsudoCommandType getType(Command command) {
            switch (command.getName().toLowerCase()) {
                case "psudo":
                    return PsudoCommandType.PSUDO;
                case "psudoas":
                    return PsudoCommandType.PSUDO_AS;
                case "psudoasraw":
                    return PsudoCommandType.PSUDO_AS_RAW;
                case "psudouuid":
                    return PsudoCommandType.PSUDO_UUID;
            }
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return onCommand(sender, sender, PsudoCommandType.getType(command), args);
    }

    boolean onCommand(CommandSender baseSender, CommandSender sender, PsudoCommandType type, String[] args) {
        if (type == null) {
            return false;
        }

        if ((type != PsudoCommandType.PSUDO_UUID || !baseSender.hasPermission("psudocommand.psudouuid"))
                && (type != PsudoCommandType.PSUDO || !baseSender.hasPermission("psudocommand.psudo"))
                && (type != PsudoCommandType.PSUDO_AS || !baseSender.hasPermission("psudocommand.psudoas"))
                && (type != PsudoCommandType.PSUDO_AS_RAW || !baseSender.hasPermission("psudocommand.psudoasraw"))) {
            baseSender.sendMessage(ChatColor.GRAY + "[PsudoCommands] You don't have permission to use this command.");
            return false;
        }

        if (args.length <= 0) {
            baseSender.sendMessage(ChatColor.GRAY + "[PsudoCommands] Please provide a valid command.");
            return false;
        }

        // Remove every spaces between tags inside a selector
        for (int i = 0; i < args.length; i++) {
            if (CommandUtils.isSelectorStartWithTag(args[i])) {
                args = CommandUtils.combineArgs(args, i, "", "]", "");
                // Because the method combineArgs ignore the last char
                args[i] = args[i] + "]";
            }
        }

        List<StringBuilder> cmds = new ArrayList<>();
        cmds.add(new StringBuilder());
        int nextstep;
        for (int i = 0; i < args.length; i += nextstep) {
            nextstep = 1;
            List<StringBuilder> temps = new ArrayList<>();
            for (StringBuilder cmd : cmds) {

                if (type == PsudoCommandType.PSUDO_AS_RAW) {
                    cmd.append(args[i]);
                } else {

                    // Set of 3 relative, world or local coordinates. The first one has to be relative or local ~ ^
                    if (CommandUtils.isRelativeCoord(args[i], true)) {
                        nextstep = 3;
                        if (i + 2 >= args.length || !CommandUtils.isRelativeCoord(args[i+1], false)
                                || !CommandUtils.isRelativeCoord(args[i+2], false)) {
                            baseSender.sendMessage(ChatColor.RED + "Please provide three coordinates after \"" + args[i] + "\"");
                            return false;
                        }
                        Location origin = CommandUtils.getLocation(sender); // sender "at" and "as" are merged
                        Location arv;
                        if(args[i].startsWith("^")) {
                            if(!args[i+1].startsWith("^") || !args[i+2].startsWith("^")) {
                                baseSender.sendMessage(ChatColor.RED + "Cannot mix world & local coordinates (everything must either use ^ or not)");
                                return false;
                            }
                            arv = CommandUtils.getLocalCoord(args[i], args[i+1], args[i+2], origin);
                        } else {
                            if(args[i+1].startsWith("^") || args[i+2].startsWith("^")) {
                                baseSender.sendMessage(ChatColor.RED + "Cannot mix world & local coordinates (everything must either use ^ or not)");
                                return false;
                            }
                            arv = CommandUtils.getRelativeCoord(args[i], args[i+1], args[i+2], origin);
                        }
                        cmd.append(arv.getX()).append(" ").append(arv.getY()).append(" ").append(arv.getZ());

                        // Targets from the selector
                    } else if (CommandUtils.isSelector(args[i])) {
                        List<Entity> selectedEntities;
                        try {
                            selectedEntities = Bukkit.selectEntities(sender, args[i]); // sender "at" and "as" are merged
                        } catch (IllegalArgumentException e) {
                            baseSender.sendMessage(ChatColor.RED + e.getMessage());
                            baseSender.sendMessage(ChatColor.RED + e.getCause().getMessage());
                            return false;
                        }
                        for (int j = 1; j < selectedEntities.size(); j++) {
                            StringBuilder sb = new StringBuilder(cmd.toString());
                            if (selectedEntities.get(j) == null) {
                                return false;
                            }
                            if (type != PsudoCommandType.PSUDO_UUID) {
                                sb.append((selectedEntities.get(j).getCustomName() != null
                                        ? selectedEntities.get(j).getCustomName()
                                        : selectedEntities.get(j).getName()));
                            } else {
                                sb.append(selectedEntities.get(j).getUniqueId());
                            }
                            if (i + 1 < args.length) {
                                sb.append(" ");
                            }
                            temps.add(sb);
                        }
                        if (selectedEntities.isEmpty() || selectedEntities.get(0) == null) {
                            return false;
                        } else {
                            if (type != PsudoCommandType.PSUDO_UUID) {
                                cmd.append(selectedEntities.get(0).getCustomName() != null
                                        ? selectedEntities.get(0).getCustomName()
                                        : selectedEntities.get(0).getName());
                            } else {
                                cmd.append(selectedEntities.get(0).getUniqueId());
                            }
                        }

                    } else {
                        cmd.append(args[i]);
                    }
                }

                if (i + 1 < args.length) {
                    cmd.append(" ");
                }
            }
            if (temps.size() > 0) {
                cmds.addAll(temps);
                temps.clear();
            }
        }
        boolean atleastOne = false;
        for (StringBuilder cmd : cmds) {
            if (Bukkit.dispatchCommand(type.useBaseSender() ? baseSender : sender, cmd.toString()))
                atleastOne = true;
        }
        return atleastOne;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        PsudoCommandType type = PsudoCommandType.getType(command);
        if ((type == PsudoCommandType.PSUDO_UUID && sender.hasPermission("psudocommand.psudouuid"))
                || (type == PsudoCommandType.PSUDO && sender.hasPermission("psudocommand.psudo"))
                || (type == PsudoCommandType.PSUDO_AS && sender.hasPermission("psudocommand.psudoas"))
                || (type == PsudoCommandType.PSUDO_AS_RAW && sender.hasPermission("psudocommand.psudoasraw"))) {
            List<String> completion = new ArrayList<>();
            int remove = 1;
            if (args.length == remove) {
                // Add all loaded command
                String lastArg = args[remove-1];
                for(Plugin plugin : Bukkit.getPluginManager().getPlugins()){
                    for(Command c : PluginCommandYamlParser.parse(plugin)) {
                        for (String alias_ : c.getAliases()) {
                            complete(completion, alias_, lastArg);
                        }
                        complete(completion, c.getName(), lastArg);
                    }
                }
            } else if (args.length > remove) {
                // remove first args and copy the other to get the tabComplete (like writing command without psudo)
                String[] newArgs = new String[args.length-remove];
                System.arraycopy(args, remove, newArgs, 0, args.length - remove);
                Command newCommand = Bukkit.getServer().getPluginCommand(args[remove-1]);
                if (newCommand != null) {
                    completion = newCommand.tabComplete(sender, args[remove-1], newArgs);
                }
            }
            return completion;
        } else {
            return psudoCommands.onTabComplete(sender, command, alias, args);
        }
    }

    private static void complete(List<String> completion, String target, String arg) {
        if (target.toLowerCase().startsWith(arg.toLowerCase())) {
            completion.add(target);
        }
    }
}
