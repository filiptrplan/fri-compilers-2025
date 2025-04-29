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
	 * stored
	 */
	String emit(AsmGenerator generator, Vector<ASM.Instr> instructions, IMC.Instr instr);
}

public class ConstTile implements Tile {
	boolean matches(IMC.Instr instr) {
		return instr instanceof IMC.CONST;
	}
}
