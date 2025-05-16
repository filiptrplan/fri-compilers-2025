package compiler.phase.regall;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import compiler.phase.Phase;
import compiler.phase.asmgen.ASM;
import compiler.phase.livean.IntGraph;
import compiler.phase.livean.LiveAn;

public class RegAll extends Phase {
	public final int K;

	public RegAll(int K) {
		super("regall");
		this.K = K;
	}

	public void assignRegisters(ASM.CodeInstrChunk codeChunk) {
		RegisterAllocator regAlloc = new RegisterAllocator(K, codeChunk);
		regAlloc.assignRegisters();
	}
}
