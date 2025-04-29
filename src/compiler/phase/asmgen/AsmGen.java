package compiler.phase.mcgen;

import java.util.Vector;

import compiler.phase.Phase;

public class AsmGen extends Phase {
	public static final Vector<ASM.CodeInstrChunk> codeChunks = new Vector<>();

	public AsmGen() {
		super("asmgen");
	}
}
