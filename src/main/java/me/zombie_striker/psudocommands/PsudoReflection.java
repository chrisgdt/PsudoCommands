package me.zombie_striker.psudocommands;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

// https://mappings.cephx.dev/1.21/net/minecraft/commands/CommandSourceStack.html
public class PsudoReflection {

    private static final boolean PAPER;

    // CLASS NAME : Spigot: CommandListenerWrapper, Mojang: CommandSourceStack
    private static final Method GET_ENTITY_METHOD, // Method getEntity() (for both Spigot and Mojang)
                                GET_BUKKIT_BASED_SENDER_METHOD, // CraftBukkit method: getBukkitSender()
                                GET_BUKKIT_LOCATION_METHOD, // Paper method: getBukkitLocation()
                                GET_POSITION, GET_LEVEL, GET_ROTATION; // if getBukkitLocation is found, these three methods are null

    private static final Method GET_BUKKIT_SENDER_METHOD; // CraftBukkit method ICommandListener#getBukkitSender(), Mojang class name : CommandSource

    // CLASS NAME : Spigot: ArgumentEntity, Mojang: EntityArgument
    static final Method ENTITY_ARGUMENT_ENTITIES_METHOD, // Spigot: multipleEntities(), Mojang: entities()
                        ENTITY_ARGUMENT_PARSE_METHOD; // Spigot: a(StringReader, boolean), Mojang: parse(StringReader arg0) (boolean added by CraftBukkit)

    static final Method GET_COMMANDS_DISPATCHER, // Spigot : MinecraftServer#getCommandDispatcher(), Mojang : MinecraftServer#getCommands()
                        GET_DISPATCHER; // Spigot : CommandDispatcher#a(), Mojang : Commands#getDispatcher()

    private static final Method ENTITY_SELECTOR_FIND_ENTITIES_METHOD; // Spigot: EntitySelector#getEntities(CommandListenerWrapper), Mojang: EntitySelector#findEntities(CommandSourceStack)
    private static final Method LOCAL_COORD_GET_POSITION_METHOD; // Spigot: ArgumentVectorPosition#a(CommandListenerWrapper), Mojang: LocalCoordinates#getPosition(CommandSourceStack)
    private static final Method GET_X, GET_Y, GET_Z; // Spigot: Vec3D#a(), Mojang: Vec3#x()
    private static final Method GET_COMMAND_MAP_METHOD; // craftbukkit package: CraftServer#getCommandMap()
    private static final Method GET_KNOW_COMMANDS_METHOD; // craftbukkit package: CraftCommandMap#getKnownCommand()
    private static final Method GET_LISTENER; // craftbukkit package: VanillaCommandWrapper#getListener(CommandSender)
    private static final Method GET_SERVER; // craftbukkit package: CraftServer#getServer()

    private static final Constructor<?> LOCAL_COORD_CONSTRUCTOR;

    private static final Method SERVER_LEVEL_GET_WORLD; // craftbukkit method ServerLevel#getWorld(), null if getBukkitLocation is found
    private static final Field X, Y; // x and y fields of Vec2 class

    static {
        // Have to change version because they've updated Bukkit.getServer().getClass().getPackage().getName() and old method no longer works
        String[] versions = Bukkit.getServer().getMinecraftVersion().split("\\."); // 1.17.1
        int version = Integer.parseInt(versions[1]);
        int versionMinor;
        if (version >= 17) {
            versions = Bukkit.getBukkitVersion().split("-R")[0].split("\\.");
            versionMinor = versions.length <= 2 ? 0 : Integer.parseInt(versions[2]);
        } else {
            versionMinor = Integer.parseInt(versions[2].substring(1));
        }

        try {
            Class<?> commandListenerWrapper, commandListener, argumentEntity, entitySelector,
                    localCoordinates, vec3, minecraftServer, commands;
            if (ReflectionUtil.minecraftVersion() > 16) {
                commandListenerWrapper = ReflectionUtil.mcClass("commands.CommandListenerWrapper");
                commandListener = ReflectionUtil.mcClass("commands.ICommandListener");
                argumentEntity = ReflectionUtil.mcClass("commands.arguments.ArgumentEntity");
                entitySelector = ReflectionUtil.mcClass("commands.arguments.selector.EntitySelector");
                localCoordinates = ReflectionUtil.mcClass("commands.arguments.coordinates.ArgumentVectorPosition");
                vec3 = ReflectionUtil.mcClass("world.phys.Vec3D");
                minecraftServer = ReflectionUtil.mcClass("server.MinecraftServer");
                commands = ReflectionUtil.mcClass("commands.CommandDispatcher");
            } else {
                commandListenerWrapper = ReflectionUtil.nmsClass("CommandListenerWrapper");
                commandListener = ReflectionUtil.nmsClass("ICommandListener");
                argumentEntity = ReflectionUtil.nmsClass("ArgumentEntity");
                entitySelector = ReflectionUtil.nmsClass("EntitySelector");
                localCoordinates = ReflectionUtil.nmsClass("ArgumentVectorPosition");
                vec3 = ReflectionUtil.nmsClass("Vec3D");
                minecraftServer = ReflectionUtil.nmsClass("MinecraftServer");
                commands = ReflectionUtil.nmsClass("CommandDispatcher");
            }
            Class<?> vanillaCommandWrapper = ReflectionUtil.obcClass("command.VanillaCommandWrapper");
            Class<?> craftCommandMap = ReflectionUtil.obcClass("command.CraftCommandMap");
            Class<?> craftServer = ReflectionUtil.obcClass("CraftServer");

            if (version >= 21) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "f");
                GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "aH");
            }
            else if (version == 20) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "f");
                if (versionMinor <= 2) GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "aC");
                else if (versionMinor == 6) GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "aH");
                else GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "aE");
            } else if (version == 19) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, versionMinor <= 2 ? "g" : "f");
                GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, versionMinor == 3 ? "aB" : "aC");
            } else if (version == 18) {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "f");
                GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "aA");
            } else {
                GET_ENTITY_METHOD = getMethod(commandListenerWrapper, "getEntity");
                GET_COMMANDS_DISPATCHER = getMethod(minecraftServer, "getCommandDispatcher");
            }
            // same obfuscated names
            if (version > 17) {
                ENTITY_ARGUMENT_ENTITIES_METHOD = getMethod(argumentEntity, "b");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = getMethod(entitySelector, "b", commandListenerWrapper);
                GET_X = getMethod(vec3, "a");
                GET_Y = getMethod(vec3, "b");
                GET_Z = getMethod(vec3, "c");
            } else {
                ENTITY_ARGUMENT_ENTITIES_METHOD = argumentEntity.getDeclaredMethod("multipleEntities");
                ENTITY_SELECTOR_FIND_ENTITIES_METHOD = entitySelector.getDeclaredMethod("getEntities", commandListenerWrapper);
                GET_X = getMethod(vec3, "getX");
                GET_Y = getMethod(vec3, "getY");
                GET_Z = getMethod(vec3, "getZ");
            }
            LOCAL_COORD_GET_POSITION_METHOD = getMethod(localCoordinates, "a", commandListenerWrapper);
            GET_DISPATCHER = getMethod(commands, "a");

            ENTITY_ARGUMENT_PARSE_METHOD = getMethod(argumentEntity, "parse", StringReader.class, boolean.class); // craftbukkit method (without boolean, obf name is a)
            GET_BUKKIT_SENDER_METHOD = getMethod(commandListener, "getBukkitSender", commandListenerWrapper); // craftbukkit method
            GET_BUKKIT_BASED_SENDER_METHOD = getMethod(commandListenerWrapper, "getBukkitSender"); // craftbukkit method
            GET_LISTENER = getMethod(vanillaCommandWrapper, "getListener", CommandSender.class); // not NMS, craftbukkit package
            GET_KNOW_COMMANDS_METHOD = getMethod(craftCommandMap, "getKnownCommands"); // not NMS, craftbukkit package
            GET_COMMAND_MAP_METHOD = getMethod(craftServer, "getCommandMap"); // not NMS, craftbukkit package
            GET_SERVER = getMethod(craftServer, "getServer"); // not NMS, craftbukkit package

            LOCAL_COORD_CONSTRUCTOR = localCoordinates.getConstructor(double.class, double.class, double.class);
            LOCAL_COORD_CONSTRUCTOR.setAccessible(true);

            // If getBukkitLocation doesn't exist (i.e. server is not running on Paper), use obfuscated methods
            PAPER = Arrays.stream(commandListenerWrapper.getDeclaredMethods())
                    .map(Method::getName)
                    .anyMatch(m -> m.equals("getBukkitLocation"));

            if (PAPER) {
                GET_BUKKIT_LOCATION_METHOD = getMethod(commandListenerWrapper, "getBukkitLocation"); // Paper method
                GET_POSITION = null;
                GET_LEVEL = null;
                GET_ROTATION = null;
                SERVER_LEVEL_GET_WORLD = null;
                X = null;
                Y = null;
            } else {
                GET_BUKKIT_LOCATION_METHOD = null;
                Class<?> level, vec2;
                if (version > 16) {
                    level = ReflectionUtil.mcClass("world.level.World");
                    vec2 = ReflectionUtil.mcClass("world.phys.Vec2F");
                } else {
                    level = ReflectionUtil.nmsClass("World");
                    vec2 = ReflectionUtil.nmsClass("Vec2F");
                }
                SERVER_LEVEL_GET_WORLD = getMethod(level, "getWorld");
                X = vec2.getDeclaredField("i");
                Y = vec2.getDeclaredField("j");
                X.setAccessible(true);
                Y.setAccessible(true);
                if (version >= 20) {
                    GET_POSITION = getMethod(commandListenerWrapper, "d");
                    GET_LEVEL = getMethod(commandListenerWrapper, "e");
                    GET_ROTATION = getMethod(commandListenerWrapper, "k");
                } else if (version == 19) {
                    GET_POSITION = getMethod(commandListenerWrapper, versionMinor <= 2 ? "e" : "d");
                    GET_LEVEL = getMethod(commandListenerWrapper, versionMinor <= 2 ? "f" : "e");
                    GET_ROTATION = getMethod(commandListenerWrapper, versionMinor <= 2 ? "l" : "k");
                } else if (version == 18) {
                    GET_POSITION = getMethod(commandListenerWrapper, "d");
                    GET_LEVEL = getMethod(commandListenerWrapper, "e");
                    GET_ROTATION = getMethod(commandListenerWrapper, "i");
                } else {
                    GET_POSITION = getMethod(commandListenerWrapper, "getPosition");
                    GET_LEVEL = getMethod(commandListenerWrapper, "getWorld");
                    GET_ROTATION = getMethod(commandListenerWrapper, "i");
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... params) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    public static CommandDispatcher<Object> getCommandDispatcher() {
        try {
            Object nmsDedicatedServer = GET_SERVER.invoke(Bukkit.getServer());
            Object nmsDispatcher = GET_COMMANDS_DISPATCHER.invoke(nmsDedicatedServer);
            return (CommandDispatcher<Object>) GET_DISPATCHER.invoke(nmsDispatcher);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static CommandSender getBukkitSender(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            Object entity = GET_ENTITY_METHOD.invoke(commandWrapperListener);
            if (entity == null) {
                return null;
            } else {
                return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(entity, commandWrapperListener);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static CommandSender getBukkitBasedSender(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            return (CommandSender) GET_BUKKIT_BASED_SENDER_METHOD.invoke(commandWrapperListener);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Location getBukkitLocation(Object commandWrapperListener) {
        Objects.requireNonNull(commandWrapperListener, "commandWrapperListener");

        try {
            if (PAPER) {
                // problem with local coordinates that does not work because pos has nul yaw & pitch. We use local coordinates
                // with the commandWrapperListener (cf. getLocalCoord) to avoid to get rotation and anchor by hand.
                return (Location) GET_BUKKIT_LOCATION_METHOD.invoke(commandWrapperListener);
            } else {
                World world = (World) SERVER_LEVEL_GET_WORLD.invoke(GET_LEVEL.invoke(commandWrapperListener));
                Object pos = GET_POSITION.invoke(commandWrapperListener);
                Object rot = GET_ROTATION.invoke(commandWrapperListener);
                if (world == null || pos == null) {
                    return null;
                }
                float yaw = rot != null ? (float) X.get(rot) : 0;
                float pitch = rot != null ? (float) Y.get(rot) : 0;
                return new Location(world, (double) GET_X.invoke(pos), (double) GET_Y.invoke(pos), (double) GET_Z.invoke(pos), yaw, pitch);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // Partially extracted from CraftServer class
    public static List<Entity> selectEntities(Object commandSourceStack, String selector) {
        List<Object> nms;
        List<Entity> result = new ArrayList<>();

        try {
            Object arg_entities = ENTITY_ARGUMENT_ENTITIES_METHOD.invoke(null);
            StringReader reader = new StringReader(selector);

            Object entitySelectorObject = ENTITY_ARGUMENT_PARSE_METHOD.invoke(arg_entities, reader, true);
            nms = (List<Object>) ENTITY_SELECTOR_FIND_ENTITIES_METHOD.invoke(entitySelectorObject, commandSourceStack);

            Preconditions.checkArgument(!reader.canRead(), "Spurious trailing data in selector: " + selector);

            for (Object entity : nms) {
                // use getBukkitSender because on entity it just returns the BukkitEntity
                result.add((Entity) GET_BUKKIT_SENDER_METHOD.invoke(entity, commandSourceStack));
            }

        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Could not parse selector: " + selector, ex);
        }

        return result;
    }

    public static Location getLocalCoord(double x, double y, double z, Object commandWrapperListener) {
        try {
            Object localCoordObject = LOCAL_COORD_CONSTRUCTOR.newInstance(x, y, z);
            Object pos = LOCAL_COORD_GET_POSITION_METHOD.invoke(localCoordObject, commandWrapperListener);

            Location loc = getBukkitLocation(commandWrapperListener);

            return new Location(loc.getWorld(), (double) GET_X.invoke(pos), (double) GET_Y.invoke(pos), (double) GET_Z.invoke((pos)));
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getCommandWrapperListenerObject(CommandSender sender) {
        try {
            return GET_LISTENER.invoke(null, sender);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Command> getKnownCommands() {
        try {
            Object craftCommandMap = GET_COMMAND_MAP_METHOD.invoke(Bukkit.getServer());
            return (Map<String, Command>) GET_KNOW_COMMANDS_METHOD.invoke(craftCommandMap);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean dispatchCommandIgnorePerms(CommandSender sender, String commandstr) {
        // TODO : org.apache.commons.lang3 will be removed in the future, keep an eye here
        //String[] args = StringUtils.split(commandstr, ' ');
        String[] args = commandstr.split(" ");
        if (args.length == 0) {
            return false;
        }
        String sentCommandLabel = args[0].toLowerCase(java.util.Locale.ENGLISH);
        Command command = getKnownCommands().get(sentCommandLabel.toLowerCase(java.util.Locale.ENGLISH));
        if (command == null) {
            return false;
        }

        if (PAPER) {
            DispatchCommandPaperHook.dispatchCommandPaper(sender, commandstr, command, sentCommandLabel, args);
        } else {
            try {
                //command.timings.startTiming();
                executeIgnorePerms(command, sender, sentCommandLabel, Arrays.copyOfRange(args, 1, args.length));
                //command.timings.stopTiming();
            } catch (CommandException ex) {
                //command.timings.stopTiming();
                throw ex;
            } catch (Throwable ex) {
                //command.timings.stopTiming();
                throw new CommandException("Unhandled exception executing '" + commandstr + "' in " + command, ex);
            }
        }
        return true;
    }

    public static boolean executeIgnorePerms(Command command, CommandSender sender, String label, String[] args) {
        if (command instanceof PluginCommand) {
            PluginCommand pluginCommand = (PluginCommand) command;
            boolean success;
            if (!pluginCommand.getPlugin().isEnabled()) {
                throw new CommandException("Cannot execute command '" + label + "' in plugin " + pluginCommand.getPlugin().getDescription().getFullName() + " - plugin is disabled.");
            }
            try {
                success = pluginCommand.getExecutor().onCommand(sender, pluginCommand, label, args);
            } catch (Throwable ex) {
                throw new CommandException("Unhandled exception executing command '" + label + "' in plugin " + pluginCommand.getPlugin().getDescription().getFullName(), ex);
            }
            if (!success && pluginCommand.getUsage().length() > 0) {
                for (String line : pluginCommand.getUsage().replace("<command>", label).split("\n")) {
                    sender.sendMessage(line);
                }
            }
            return success;
        } else {
            // don't check VanillaCommandWrapper type because we can ignore psudo and use vanilla behavior
            return command.execute(sender, label, args);
        }
    }
}
