package compiler.phase.asmgen;

import java.util.Vector;

import compiler.phase.imcgen.IMC;

public interface Tile {
	/** Whether the tile matches the tree with instr as root */
	boolean matches(IMC.Instr instr);

	/** Size of tile */
	int getSize();

	/**
	 * Emits the instructions. Returns the register / place where the result is
	 * stored.
	 * The return value of an expression is the register that contains the result of
	 * the expression. So it will return a String with the format T{number}
	 * If the return value is a constant, then the constant is the return value (aka
	 * a string with the format {number}).
	 * The return value of a statement is null.
	 *
	 * This function also visits all the sub-instructions in the tree with the
	 * generator visitor.
	 */
	String emit(AsmGenerator generator, Vector<ASM.Instr> instructions, IMC.Instr instr);
}
