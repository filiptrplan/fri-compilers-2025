package compiler.phase.mcgen;

import java.util.Vector;

import compiler.phase.Phase;

public class McGen extends Phase {
	public static final Vector<MC.CodeInstrChunk> codeChunks = new Vector<>();

	public McGen() {
		super("mcgen");
	}
}
