package compiler.phase.initfun;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import compiler.phase.Phase;
import compiler.phase.asmgen.ASM;
import compiler.phase.asmgen.Tiles;

public class InitFun extends Phase {
	public InitFun() {
		super("initfun");
	}

	public static void addPrologueAndEpilogue(ASM.CodeInstrChunk chunk) {
		addPrologue(chunk);
		addEpilogue(chunk);
	}

	private static ASM.Instr storeRegAtOffset(String regToStore, String fp, long offset) {
		Vector<String> ops = new Vector<>();
		ops.add(regToStore);
		ops.add(Long.toString(offset));
		ops.add(fp);
		return new ASM.Oper("sd #1, #2(#3)", null, ops);
	}

	private static ASM.Instr restoreRegFromOffset(String regToRestore, String fp, long offset) {
		return new ASM.Oper("ld $1, #1(#2)", Tiles.singleOp(regToRestore), Tiles.doubleOp(Long.toString(offset), fp));
	}

	private static List<String> getDefinedRegisters(ASM.CodeInstrChunk chunk) {
		Set<String> regs = new HashSet<>();

		// Always restore RV and SP
		regs.add("x10");
		regs.add("x2");

		for (ASM.Instr instr : chunk.instructions) {
			if (instr instanceof ASM.Oper oper) {
				if (oper.defOps != null) {
					regs.addAll(oper.defOps);
				}
			}
		}

		// We save RA and FP separately
		regs.remove("x1");
		regs.remove("x3");

		return List.copyOf(regs);
	}

	private static void addPrologue(ASM.CodeInstrChunk chunk) {
		Vector<ASM.Instr> prologue = new Vector<>();

		// Add entry label
		prologue.add(new ASM.Label(chunk.frame.label.name));

		// Save registers
		// We are saving them relative to the SP (which is our new FP)

		// The saved registers are saved after the temporary variables
		// the FP and RA
		long offset = chunk.frame.locsSize + chunk.tempSize + 24;

		for (String reg : getDefinedRegisters(chunk)) {
			if (reg.equals("x3")) {
				continue;
			}
			// The containing function's SP is our FP
			prologue.add(storeRegAtOffset(reg, "x2", -offset));
			// Increment offset for next register
			offset += 8;
		}

		// Save FP address
		// NOTE: This is not necessary as we are currently just saving all the registers
		// but is here for redundancy
		prologue.add(storeRegAtOffset("x3", "x2", -(chunk.frame.locsSize + 8)));

		// Use the old SP as the new FP
		prologue.add(new ASM.Oper("mv $1, #1", Tiles.singleOp("x3"), Tiles.singleOp("x2")));

		// Move new SP to right location
		long spOffset = offset + chunk.frame.argsSize;
		prologue.add(
				new ASM.Oper(
						"addi $1, #1, #2",
						Tiles.singleOp("x2"),
						Tiles.doubleOp("x3", Long.toString(-spOffset))));

		// Save return address = local variables + saved FP from parent call
		long retAddrOffset = chunk.frame.locsSize + 16;
		prologue.add(storeRegAtOffset("x1", "x3", -retAddrOffset));

		// Jump to entry label
		prologue.add(new ASM.Oper("j #1", null, Tiles.singleOp(chunk.entryLabel.name)));

		chunk.prologueInstructions = prologue;
	}

	private static void addEpilogue(ASM.CodeInstrChunk chunk) {
		Vector<ASM.Instr> epilogue = new Vector<>();

		// Add exit label
		epilogue.add(new ASM.Label(chunk.exitLabel.name));

		// Store RV to the stack
		epilogue.add(storeRegAtOffset("x10", "x3", 0));

		// Restore all registers except FP
		long offset = chunk.frame.locsSize + chunk.tempSize + 24;
		for (String reg : getDefinedRegisters(chunk)) {
			if (reg.equals("x3")) {
				continue;
			}
			epilogue.add(restoreRegFromOffset(reg, "x3", -offset));
			offset += 8;
		}

		// Restore RA
		epilogue.add(restoreRegFromOffset("x1", "x3", -(chunk.frame.locsSize + 16)));

		// Restore FP last
		epilogue.add(restoreRegFromOffset("x3", "x3", -chunk.frame.locsSize - 8));

		// Jump to return address (should already be in x1)
		epilogue.add(new ASM.Oper("jalr x0, #1, 0", null, Tiles.singleOp("x1")));

		chunk.epilogueInstructions = epilogue;
	}
}
