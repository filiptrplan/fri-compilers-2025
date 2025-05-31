package compiler.common;

import java.util.List;

import compiler.phase.imcgen.IMC;

public class Helper {
	public static List<String> physicalRegisters = List.of(
			"x1",
			"x2",
			"x3",
			"x4",
			"x5",
			"x6",
			"x7",
			"x8",
			"x9",
			"x10",
			"x11",
			"x12",
			"x13",
			"x14",
			"x15",
			"x16",
			"x17",
			"x18",
			"x19",
			"x20",
			"x21",
			"x22",
			"x23",
			"x24",
			"x25",
			"x26",
			"x27",
			"x28",
			"x29",
			"x30",
			"x31");

	public static boolean isInstrConst(IMC.Instr instr) {
		return instr instanceof IMC.CONST;
	}

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
		return s.startsWith("T") || s.equals("SP");
	}
}
