package compiler.phase.mcgen;

import java.util.Vector;

import compiler.phase.imclin.LIN;
import compiler.phase.memory.MEM;

/**
 * Machine code instruction representation
 */
public class MC {
	/**
	 * A chunk of machine code.
	 */
	public static class CodeInstrChunk {

		/** A frame of a function. */
		public final MEM.Frame frame;
		/** The instructions of a function body. */
		private final Vector<MC.Instr> instructions;
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
		public CodeInstrChunk(LIN.CodeChunk codeChunk, Vector<MC.Instr> instrs) {
			this.frame = codeChunk.frame;
			this.instructions = instrs;
			this.entryLabel = codeChunk.entryLabel;
			this.exitLabel = codeChunk.exitLabel;
		}
	}

	public abstract class Instr {
	}

	/** A label in the machine code */
	public class Label extends Instr {
		/** The string representation of {@link compiler.phase.memory.MEM.Label} */
		public String label;

		public Label(String label) {
			this.label = label;
		}
	}

	/** Represents any instruction with operands */
	public class Oper extends Instr {
		/**
		 * The string representation of operands.
		 * - labels start with L
		 * - temps start with T
		 * - constants are just numbers
		 * The `usedOps` are for the operands that are used by the instruction.
		 */
		public final Vector<String> useOps;
		/**
		 * The operands that are written to / defined by the instruction.
		 * There can be multiple (i.e. when dividing and the remainder gets written to
		 * another register)
		 */
		public final Vector<String> defOps;
		public final String opcode;

		public Oper(String opcode, Vector<String> defOps, Vector<String> useOps) {
			this.opcode = opcode;
			this.defOps = defOps;
			this.useOps = useOps;
		}
	}

	/**
	 * A special case of an instruction called move.
	 * It signifies ONLY instructions that copy one register to another
	 */
	public class Move extends Oper {
		public Move(String opcode, String destination, String source) {
			super(opcode, new Vector<String>() {
				{
					add(destination);
				}
			}, new Vector<String>() {
				{
					add(source);
				}
			});
		}
	}
}
