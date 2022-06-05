package com.xeno.goo.entities;

import net.minecraft.nbt.CompoundTag;

public class EntityNbtHelper {
	/***
	 * Shorthand overload of incrementIntContents that defaults initialValue to 1 and max value to Integer.MAX_VALUE
	 * @param compound The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to increment
	 * @return The result value of the integer contents of the tag after being incremented.
	 */
	public static int incrementIntContents(CompoundTag compound, String name) {
		return incrementIntContents(compound, name, 1);
	}

	/***
	 * Shorthand overload of incrementIntContents that defaults max value to Integer.MAX_VALUE;
	 * @param nbt The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to increment
	 * @param initialValue The initial value of the entry we're trying to increment, to set if it doesn't exist
	 * @return The result value of the integer contents of the tag after being incremented.
	 */
	public static int incrementIntContents(CompoundTag nbt, String name, int initialValue) {
		return incrementIntContents(nbt, name, initialValue, Integer.MAX_VALUE);
	}

	/***
	 *
	 * @param nbt The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to increment
	 * @param initialValue The initial value of the entry we're trying to increment, to set if it doesn't exist
	 * @param max The maximum amount the int is allowed to go before it's effectively throttled.
	 * @return The result value of the integer contents of the tag after being incremented (or failing to increment due to throttling)
	 */
	public static int incrementIntContents(CompoundTag nbt, String name, int initialValue, int max) {

		if (ensureIntContents(nbt, name, initialValue)) {
			nbt.putInt(name, Math.min(max, nbt.getInt(name) + 1));
		}
		return nbt.getInt(name);
	}

	/***
	 *
	 * @param compound The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to confirm or initialize.
	 * @param initialValue The initialization value if we have to initialize the tag contents
	 * @return True if the tag already existed, false if we had to create it inside the method.
	 */
	public static boolean ensureIntContents(CompoundTag compound, String name, int initialValue) {
		if (!compound.contains(name)) {
			compound.putInt(name, initialValue);
			return false;
		}
		return true;
	}

	/***
	 * Shorthand overload of toggle bool contents that defaults to an initial value of false.
	 * @param compound The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to confirm or initialize.
	 * @return The contents of the tag after either ensuring it exists (and possibly initializing it) or after toggling it.
	 */
	public static boolean toggleBoolContents(CompoundTag compound, String name) {
		return toggleBoolContents(compound, name, false);
	}

	/***
	 *
	 * @param compound The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to confirm or initialize.
	 * @param initialValue The initialization value if we have to initialize the tag contents
	 * @return The contents of the tag after either ensuring it exists (and possibly initializing it) or after toggling it.
	 */
	public static boolean toggleBoolContents(CompoundTag compound, String name, boolean initialValue) {
		if(ensureBoolContents(compound, name, initialValue)) {
			compound.putBoolean(name, !compound.getBoolean(name));
		}
		return compound.getBoolean(name);
	}

	/***
	 *
	 * @param compound The tag we're modifying or checking contents of
	 * @param name The name of the entry we're trying to confirm or initialize.
	 * @param initialValue The initialization value if we have to initialize the tag contents
	 * @return True if the boolean tag info was already there, false if we had to create it.
	 */
	public static boolean ensureBoolContents(CompoundTag compound, String name, boolean initialValue) {
		if (!compound.contains(name)) {
			compound.putBoolean(name, initialValue);
			return false;
		}
		return true;
	}
}
