package compiler.phase.asmgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.imclin.LIN;
import compiler.phase.memory.MEM;

/**
 * Machine code instruction representation
 */
public class ASM {
	/**
	 * A chunk of machine code.
	 */
	public static class CodeInstrChunk {

		/** A frame of a function. */
		public final MEM.Frame frame;
		/** The instructions of a function body. */
		public final Vector<ASM.Instr> instructions;
		/** The function's body entry label, i.e., the label the prologue jumps to. */
		public final MEM.Label entryLabel;
		/**
		 * The function's body exit label, i.e., the label at which the epilogue starts.
		 */
		public final MEM.Label exitLabel;

		/**
		 * Constructs a new code chunk.
		 * 
		 * @param codeChunk    The code chunk from the ImcLin phase this is based on
		 * @param instructions The machine code instructions of the function body
		 */
		public CodeInstrChunk(LIN.CodeChunk codeChunk, Vector<ASM.Instr> instrs) {
			this.frame = codeChunk.frame;
			this.instructions = instrs;
			this.entryLabel = codeChunk.entryLabel;
			this.exitLabel = codeChunk.exitLabel;
		}
	}

	static public abstract class Instr {
	}

	/** A label in the machine code */
	static public class Label extends Instr {
		/** The string representation of {@link compiler.phase.memory.MEM.Label} */
		public String label;

		public Label(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label + ":";
		}
	}

	/** Represents any instruction with operands */
	public static class Oper extends Instr {
		/**
		 * The string representation of operands.
		 *
		 * - labels start with L or _ (for named functions)
		 * - temps start with T
		 * - constants are just numbers
		 * - special cases of FP and SP
		 * - RV denotes that this is a return value of a function and is returned
		 * on the stack
		 *
		 * The `usedOps` are for the operands that are used by the instruction.
		 */
		public final Vector<String> useOps;
		/**
		 * The operands that are written to / defined by the instruction.
		 * There can be multiple (i.e. when dividing and the remainder gets written to
		 * another register)
		 */
		public final Vector<String> defOps;
		/*
		 * The string representation of the operand instruction.
		 * The placeholders for the useOps registers are #1, #2, #3, ...
		 * The placeholders for the defOpss registers are $1, $2, $3 ...
		 */
		public final String opcode;

		public Oper(String opcode, Vector<String> defOps, Vector<String> useOps) {
			this.opcode = opcode;
			this.defOps = defOps;
			this.useOps = useOps;
		}

		@Override
		public String toString() {
			String result = opcode;
			if (defOps != null) {
				for (int i = 0; i < defOps.size(); i++) {
					result = result.replace("$" + Integer.toString(i + 1), defOps.get(i));
				}
			}
			if (useOps != null) {
				for (int i = 0; i < useOps.size(); i++) {
					result = result.replace("#" + Integer.toString(i + 1), useOps.get(i));
				}
			}
			return "\t" + result;
		}
	}

	/**
	 * A special case of an instruction called move.
	 * It signifies ONLY instructions that copy one register to another
	 */
	public static class Move extends Oper {
		public Move(String opcode, Vector<String> destination, Vector<String> source) {
			super(opcode, destination, source);
		}
	}
}
