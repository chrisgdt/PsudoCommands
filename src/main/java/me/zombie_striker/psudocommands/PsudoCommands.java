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

import java.util.List;

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
					// "arguments" is a greedy string, i.e. get everything, then parsed as getString
					RequiredArgumentBuilder.argument("arguments", StringArgumentType.greedyString())
							.suggests((context, builder) -> {
								CommandSender baseSender = PsudoCommodoreExtension.getBukkitBasedSender(context.getSource());
								String[] args = builder.getRemaining().split(" ", -1); // -1 to keep trailing space

								List<String> completion = executor.onTabComplete(baseSender, command, null, args);

								if (completion != null) {
									// offset the builder to the next argument to not be stuck at the beginning
									int offset = 0;
									for (int i = 0; i < args.length - 1; i += 1) {
										offset += args[i].length() + 1; // +1 for the space deleted with split
									}
									builder = builder.createOffset(builder.getStart() + offset);

									// no need to check if the completion string starts with the remaining, already filter in onTabComplete
									for (String str : completion) {
										builder.suggest(str);
									}
								}
								return builder.buildFuture();
							})
							.executes(context -> {
								String[] args = StringArgumentType.getString(context, "arguments").split(" ");
								Object source = context.getSource();
								CommandSender baseSender = PsudoCommodoreExtension.getBukkitBasedSender(source);
								CommandSender sender = PsudoCommodoreExtension.getBukkitSender(source);
								if (sender == null) {
									sender = baseSender;
								}
								boolean result = executor.onCommand(baseSender, sender, source, commandType, args);
								return result ? SINGLE_SUCCESS : 0;
							})
				)
		);
	}
}