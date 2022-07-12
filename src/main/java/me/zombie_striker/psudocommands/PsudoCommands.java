package me.zombie_striker.psudocommands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import me.lucko.commodore.PsudoCommodoreExtension;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PsudoCommands extends JavaPlugin {

	@Override
	public void onEnable() {
		PsudoCommandExecutor executor = new PsudoCommandExecutor(this);

		PluginCommand[] commands = new PluginCommand[]{ getCommand("psudo"), getCommand("psudouuid"),
													    getCommand("psudoas"), getCommand("psudoasraw") };

		for (PluginCommand command : commands) {
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		// check if brigadier is supported
		if (CommodoreProvider.isSupported()) {
			Commodore commodore = CommodoreProvider.getCommodore(this);

			for (PluginCommand command : commands) {
				registerCommand(commodore, executor, command, PsudoCommandExecutor.PsudoCommandType.getType(command));
			}
		}
	}

	private static void registerCommand(Commodore commodore, PsudoCommandExecutor executor, PluginCommand command, PsudoCommandExecutor.PsudoCommandType commandType) {
		commodore.register(LiteralArgumentBuilder.literal(command.getName()) // don't put "command" as first argument to not call the CraftBukkit SuggestionProvider
				.then(
						RequiredArgumentBuilder.argument("arguments", StringArgumentType.greedyString())
								.suggests((ctx, builder) -> builder.buildFuture())
								.executes(cs -> {
									String[] args = StringArgumentType.getString(cs, "arguments").split(" ");
									Object source = cs.getSource();
									CommandSender baseSender = PsudoCommodoreExtension.getBukkitBasedSender(source);
									CommandSender sender = PsudoCommodoreExtension.getBukkitSender(source);
									boolean result = executor.onCommand(baseSender, sender, commandType, args);
									return result ? SINGLE_SUCCESS : 0;
								})
				)
		);
	}
}