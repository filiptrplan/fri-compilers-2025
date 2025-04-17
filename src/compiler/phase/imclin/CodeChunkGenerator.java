package compiler.phase.imclin;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.abstr.AST;
import compiler.phase.imcgen.IMC;
import compiler.phase.imcgen.ImcGen;
import compiler.phase.memory.MEM;
import compiler.phase.memory.Memory;

public class CodeChunkGenerator implements AST.FullVisitor<Object, Object> {
	/**
	 * A basic block. This is a piece of code that doesn't contain any jumps
	 * in it. That way we make sure that it always executes in full.
	 */
	private class BasicBlock {
		/**
		 * Label that points to the start of the block
		 */
		public final MEM.Label label;

		/**
		 * The jump at the end of the block.
		 * If unconditional it doesn't have any constraints.
		 * If conditional, the false branch should always jump to the block directly
		 * after it.
		 */
		public final IMC.Instr jump;

		/** The other statements in the block */
		public final Vector<IMC.Stmt> stmts;

		public BasicBlock(MEM.Label label, IMC.Instr jump, Vector<IMC.Stmt> stmts) {
			this.label = label;
			this.jump = jump;
			this.stmts = stmts;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("BasicBlock(").append(label.name).append("):\n");
			for (IMC.Stmt stmt : stmts) {
				sb.append("  ").append(stmt.toString()).append("\n");
			}
			sb.append("  ").append(jump.toString()).append("\n");
			return sb.toString();
		}
	}

	/**
	 * Generates basic blocks recursively from a vector of stmts
	 */
	private Vector<BasicBlock> generateBasicBlocks(Vector<IMC.Stmt> stmts) {
		Vector<BasicBlock> blocks = new Vector<>();
		Vector<IMC.Stmt> currentBlockStmts = new Vector<>();
		MEM.Label label = new MEM.Label();
		for (IMC.Stmt stmt : stmts) {
			if (stmt instanceof IMC.CJUMP || stmt instanceof IMC.JUMP) {
				BasicBlock block = new BasicBlock(label, stmt, currentBlockStmts);
				blocks.add(block);
				currentBlockStmts = new Vector<>();
				label = new MEM.Label();
			} else if (stmt instanceof IMC.STMTS) {
				// We finish the basic block and add a jump to start of this STMTS instruction.
				// This should be okay because when we linearize we remove jumps that points to
				// the basic block right behind this one.
				IMC.STMTS stmtsInstr = (IMC.STMTS) stmt;
				if (currentBlockStmts.size() != 0) {
					IMC.JUMP jump = new IMC.JUMP(new IMC.NAME(stmtsInstr.startLabel));
					BasicBlock block = new BasicBlock(label, jump, currentBlockStmts);
					blocks.add(block);
				}
				// We need to create a new blockstmts instead of clearing it because we pass
				// it to the BasicBlock class
				currentBlockStmts = new Vector<>();
				label = new MEM.Label();
				// We recursively generate the basic blocks
				blocks.addAll(generateBasicBlocks(stmtsInstr.stmts));
			} else if (stmt instanceof IMC.LABEL && currentBlockStmts.size() == 0) {
				// If the first statement is a label, we use that for our basic block
				label = ((IMC.LABEL) stmt).label;
			} else {
				currentBlockStmts.add(stmt);
			}
		}

		return blocks;
	}

	public Object visit(AST.DefFunDefn defFunDefn, Object arg) {
		Vector<IMC.Stmt> stmts = new Vector<>();
		for (AST.Stmt stmt : defFunDefn.stmts) {
			IMC.Instr stmtInstr = ImcGen.stmt.get(stmt);
			IMC.Instr newStmtInstr = stmtInstr.accept(new NestedCallsTransformer(stmts), null);
			stmts.add((IMC.Stmt) newStmtInstr);
		}

		Vector<BasicBlock> blocks = generateBasicBlocks(stmts);
		for (BasicBlock block : blocks) {
			Report.info(block.toString());
		}

		MEM.Label entryLabel = ImcGen.entryLabel.get(defFunDefn);
		MEM.Label exitLabel = ImcGen.exitLabel.get(defFunDefn);
		MEM.Frame frame = Memory.frames.get(defFunDefn);
		LIN.CodeChunk codeChunk = new LIN.CodeChunk(frame, stmts, entryLabel, exitLabel);
		ImcLin.addCodeChunk(codeChunk);

		// Then we visit other functions
		defFunDefn.stmts.accept(this, arg);

		return null;
	}
}
