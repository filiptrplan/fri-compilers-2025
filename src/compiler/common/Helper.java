package compiler.common;

public class Helper {
	/**
	 * Checks if a string represents a label
	 */
	public static boolean isLabel(String s) {
		return s.startsWith("L") || s.startsWith("_");
	}

	/**
	 * Checks if a string represents a constant
	 */
	public static boolean isConstant(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		// Simple check for integer literal (allows negative numbers)
		return s.matches("-?\\d+");
	}

	/**
	 * Checks if a string represents a register (not a label or a constant)
	 */
	public static boolean isRegister(String s) {
		return !isLabel(s) && !isConstant(s);
	}

	/**
	 * Checks if a string represents a physical register (x0-x31)
	 */
	public static boolean isPhysicalRegister(String s) {
		return s.startsWith("x");
	}

	/**
	 * Checks if a string represents a temporary variable
	 */
	public static boolean isTemp(String s) {
		return s.startsWith("T");
	}
}
