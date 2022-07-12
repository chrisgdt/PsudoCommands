package me.zombie_striker.psudocommands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.regex.Pattern;

public class CommandUtils {

	public static boolean isSelector(String arg) {
		return Pattern.matches("@[aerps](\\[.*\\])?", arg);
	}

	public static boolean isSelectorWithoutTag(String arg) {
		return Pattern.matches("@[aerps](\\[ *\\])?", arg);
	}

	public static boolean isSelectorStartWithTag(String arg) {
		return Pattern.matches("@[aerps]\\[", arg);
	}

	/**
	 * Modify the given String array to concat some arguments in one according
	 * to the begin and the end. For example, if : begin and end are double
	 * quote " and sep is a space space :
	 * split is ["first", "second", "\"combine", "args", "in", "one\"", "last"]
	 * index is 2
	 * -> returns ["first", "second", "combine args in one", "last"]
	 * If index isn't 2, nothing will happen.
	 *
	 *
	 * Begin (resp. end) must be at the begining (resp. the end) of their
	 * argument if they represent the begining (resp. the end) of the new args.
	 * They are also removed.
	 * @param args The initial array.
	 * @param index The index of the start.
	 * @param begin String that represents the begin of the concat argument.
	 * @param end String that represents the end of the concat argument.
	 * @param sep The separator, often a space.
	 * @return A new String array or the same reference than the given array.
	 */
	public static String[] combineArgs(String[] args, int index, String begin, String end, String sep) {
		if (index >= args.length) {
			return args;
		}
		String prevArg = args[index];
		if(prevArg.startsWith(begin)) {
			int beginLength = begin.length();
			int endLength = end.length();
			boolean onlyBegin = prevArg.equals(begin);
			if (onlyBegin && index == args.length-1) {
				// The index is the last and text only the begin string... What's your problem ??
				args[index] = "";
				return args;
			} else if (!onlyBegin && prevArg.endsWith(end)) {
				// something like <"argument">, only remove quotes
				args[index] = prevArg.substring(beginLength, prevArg.length() - endLength);
				return args;
			} else {
				// Loop to the end or the arg with quotes in last char
				StringBuilder newArg = new StringBuilder(onlyBegin ? args[index+1] : prevArg.substring(beginLength));
				int i = index + (onlyBegin ? 2 : 1);
				while (i < args.length && !args[i].endsWith(end)) {
					newArg.append(sep).append(args[i]);
					i++;
				}

				// If true, it means it found the "end" string at the end of the current arg
				if (i < args.length) {
					newArg.append(sep).append(args[i], 0, args[i].length() - endLength);
					i++;
				}
				// modify this to copy
				args[index] = newArg.toString();

				// copy the split array into a new one.
				String[] newArgs = new String[args.length - i + index+1];
				System.arraycopy(args, 0, newArgs, 0, index+1);
				if (i < args.length) {
					System.arraycopy(args, i, newArgs, index + 1, args.length - i);
				}

				// set split like before copy
				args[index] = prevArg;

				return newArgs;
			}
		}
		return args;
	}

	/**
	 * Returns true if str is a relative, local or world coordinate.
	 * E.g. "^-2" or "~1.45" or "-2.4"
	 * If firstCoord is true, str has to be only relative or local, with "~" or "^",
	 * not only number. Easier to detect the first coordinate of the three.
	 * If it is false, str can be a double as string.
	 *
	 * @param str        The tested coordinate
	 * @param firstCoord Is str the first coordinate of the three.
	 * @return True if str is a relative coordinate
	 */
	public static boolean isRelativeCoord(String str, boolean firstCoord) {
		// return true if it is a used coordinated like ~3.4 or ^40 or 3.1 ...
		if(str.startsWith("~") || str.startsWith("^")) {
			return str.length() == 1 || isDouble(str.substring(1));
		}
		return !firstCoord && isDouble(str);
	}

	/**
	 * Parse string coordinates as double coordinates.
	 * Each string is a number or starts with "~".
	 * Precondition : x, y and z verify isRelativeCoord.
	 *
	 * @param x      First coordinate
	 * @param y      Second coordinate
	 * @param z      Third coordinate
	 * @param origin The origin location for relative
	 * @return The arrival location
	 */
	public static Location getRelativeCoord(String x, String y, String z, Location origin) {
		Location arrival = origin.clone();
		if(x.startsWith("~")) {
			arrival.setX(arrival.getX() + getCoordinate(x));
		} else {
			arrival.setX(Double.parseDouble(x));
		}

		if(y.startsWith("~")) {
			arrival.setY(arrival.getY() + getCoordinate(y));
		} else {
			arrival.setY(Double.parseDouble(y));
		}

		if(z.startsWith("~")) {
			arrival.setZ(arrival.getZ() + getCoordinate(z));
		} else {
			arrival.setZ(Double.parseDouble(z));
		}
		return arrival;
	}

	/**
	 * Parse string coordinates as double coordinates.
	 * Each string starts with "^".
	 * Precondition : x, y and z verify isRelativeCoord.
	 *
	 * @param x      First coordinate
	 * @param y      Second coordinate
	 * @param z      Third coordinate
	 * @param origin The origin location for local
	 * @return The arrival location
	 */
	public static Location getLocalCoord(String x, String y, String z, Location origin) {
		Location arrival = origin.clone();

		Vector dirX = new Location(arrival.getWorld(), 0, 0, 0, Location.normalizeYaw(arrival.getYaw()-90),
				arrival.getPitch()).getDirection().normalize();
		Vector dirY = new Location(arrival.getWorld(), 0, 0, 0, arrival.getYaw(),
				arrival.getPitch()-90).getDirection().normalize();
		Vector dirZ = arrival.getDirection().normalize();

		return arrival.add(dirX.multiply(getCoordinate(x)))
				      .add(dirY.multiply(getCoordinate(y)))
				      .add(dirZ.multiply(getCoordinate(z)));
	}

	/**
	 * Get the location of the sender
	 * @param sender The command sender.
	 * @return The location of the sender, the default world's location if it's the Console.
	 */
	public static Location getLocation(CommandSender sender) {
		if (sender instanceof Entity) {
			return ((Entity) sender).getLocation();
		} else if (sender instanceof BlockCommandSender) {
			return ((BlockCommandSender) sender).getBlock().getLocation().add(.5, 0, .5); // Center of block
		} else {
			return Bukkit.getWorlds().get(0).getSpawnLocation();
		}
	}

	private static double getCoordinate(String c) {
		// c is like ^3 or ~-1.2 or 489.1
		if (Character.isDigit(c.charAt(0))) {
			return Double.parseDouble(c);
		} else {
			return c.length() == 1 ? 0 : Double.parseDouble(c.substring(1));
		}
	}

	private static boolean isDouble(String str) {
		try {
			Double.parseDouble(str);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
}
