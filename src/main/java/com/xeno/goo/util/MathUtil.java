package com.xeno.goo.util;

public class MathUtil {
	public static long clamp(long num, long min, long max) {
		if (num < min) {
			return min;
		} else {
			return num > max ? max : num;
		}
	}
}
