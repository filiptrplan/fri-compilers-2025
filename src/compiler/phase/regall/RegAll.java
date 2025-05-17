package compiler.phase.regall;

import java.util.Set;

import compiler.phase.Phase;
import compiler.phase.asmgen.ASM;

public class RegAll extends Phase {
	public static int K;

	public RegAll(int K) {
		super("regall");
		RegAll.K = K;
	}

	public static void assignRegisters(ASM.CodeInstrChunk codeChunk, Set<String> previouslySpilled) {
		RegisterAllocator regAlloc = new RegisterAllocator(K, codeChunk, previouslySpilled);
		regAlloc.assignRegisters();
	}
}
