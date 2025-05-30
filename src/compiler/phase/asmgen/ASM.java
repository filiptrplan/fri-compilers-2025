package compiler.phase.asmgen;

import java.util.Vector;

import compiler.phase.imclin.LIN;
import compiler.phase.memory.MEM;

/**
 * Machine code instruction representation
 */
public class ASM {
	public static abstract class DataInstrChunk {
		public String toString() {
			return null;
		}
	}

	/** A chunk of machine code describing global variables */
	public static class GlobalVarChunk extends DataInstrChunk {
		/** Name of global variable */
		public final String label;
		/** Size of global variable */
		public final long size;

		public GlobalVarChunk(String label, long size) {
			this.label = label;
			this.size = size;
		}

		@Override
		public String toString() {
			String s = "";
			s += "\t.globl " + this.label + "\n";
			s += "\t.align 8\n"; // For now we always align to 8 bytes
			s += this.label + ":\n"; // Start the label
			s += "\t.zero " + Long.toString(size) + "\n\n"; // We always have uninitialized values
			return s;
		}
	}

	/** A chunk of machine code defining string constants */
	public static class StringConstChunk extends DataInstrChunk {
		/** Name of label */
		public final String label;
		/** Actual string */
		public final String init;

		public StringConstChunk(String label, String init) {
			this.label = label;
			this.init = init;
		}

		@Override
		public String toString() {
			String s = "";
			s += this.label + ":\n"; // Start the label
			for (int i = 0; i < this.init.length(); i++) {
				s += "\t.byte 0x" + Integer.toHexString(this.init.charAt(i)) + "\n";
			}
			s += "\t.byte 0x0\n\n";
			return s;
		}
	}

	/**
	 * A chunk of machine code.
	 */
	public static class CodeInstrChunk {

		/** A frame of a function. */
		public final MEM.Frame frame;
		/** The instructions of a function body. */
		public Vector<ASM.Instr> instructions;
		/** The function's body entry label, i.e., the label the prologue jumps to. */
		public final MEM.Label entryLabel;
		/**
		 * The function's body exit label, i.e., the label at which the epilogue starts.
		 */
		public final MEM.Label exitLabel;

		/**
		 * The size of the stack frame that is allocated for temporary variables.
		 *
		 * Used for calculating the SP offset in the prologue
		 */
		public long tempSize = 0;

		public Vector<ASM.Instr> prologueInstructions;
		public Vector<ASM.Instr> epilogueInstructions;

		/**
		 * Constructs a new code chunk.
		 * 
		 * @param codeChunk The code chunk from the ImcLin phase this is based on
		 * @param instrs    The machine code instructions of the function body
		 */
		public CodeInstrChunk(LIN.CodeChunk codeChunk, Vector<ASM.Instr> instrs) {
			this.frame = codeChunk.frame;
			this.instructions = instrs;
			this.entryLabel = codeChunk.entryLabel;
			this.exitLabel = codeChunk.exitLabel;
		}

	}

	private static int instrCounter = 0;

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

		private final int instrId;

		/** Whether an instruction is an unconditional jump */
		public final boolean isJump;
		/** Whether the instruction is a conditional jump */
		public final boolean isCjump;
		/** Jump target label */
		public final String jumpTarget;

		public Oper(String opcode, Vector<String> defOps, Vector<String> useOps) {
			this.opcode = opcode;
			this.defOps = defOps;
			this.useOps = useOps;
			this.instrId = ASM.instrCounter;

			// Determine if the instruction is a conditional jump and extract the jump
			// target
			String opcodePrefix = getOpcodePrefix(opcode);
			this.isCjump = isConditionalJump(opcodePrefix);

			// Determine if it's an unconditional jump and get the jump target
			if (opcode.startsWith("j ")) {
				this.isJump = true;
				this.jumpTarget = useOps.getFirst();
			} else if (opcode.startsWith("jal ")) {
				this.isJump = true;
				this.jumpTarget = useOps.get(1);
			} else if (this.isCjump) {
				this.isJump = false;
				this.jumpTarget = useOps.get(useOps.size() - 1); // Last operand is always the target
			} else {
				this.isJump = false;
				this.jumpTarget = null;
			}

			ASM.instrCounter += 1;
		}

		/**
		 * Extracts the opcode prefix (the instruction mnemonic) from the full opcode
		 * string.
		 */
		private String getOpcodePrefix(String opcode) {
			int spaceIndex = opcode.indexOf(' ');
			return spaceIndex > 0 ? opcode.substring(0, spaceIndex) : opcode;
		}

		/**
		 * Determines if the given opcode prefix represents a conditional jump
		 * instruction.
		 */
		private boolean isConditionalJump(String opcodePrefix) {
			switch (opcodePrefix) {
				case "beq":
				case "bne":
				case "blt":
				case "bge":
				case "bltu":
				case "bgeu":
				case "beqz":
				case "bnez":
				case "blez":
				case "bgez":
				case "bltz":
				case "bgtz":
				case "bgt":
				case "ble":
				case "bgtu":
				case "bleu":
					return true;
				default:
					return false;
			}
		}

		@Override
		public int hashCode() {
			return this.instrId;
		}

		@Override
		public boolean equals(final Object other) {
			return this.hashCode() == other.hashCode();
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
