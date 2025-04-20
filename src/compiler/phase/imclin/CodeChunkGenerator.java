package compiler.phase.imclin;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.Stack;
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
		public final IMC.Stmt jump;

		/** The other statements in the block */
		public final Vector<IMC.Stmt> stmts;

		public BasicBlock(MEM.Label label, IMC.Stmt jump, Vector<IMC.Stmt> stmts) {
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
			String jumpStr = jump == null ? "NULL" : jump.toString();
			sb.append("  ").append(jumpStr).append("\n");
			return sb.toString();
		}
	}

	/**
	 * Casts expression to IMC.NAME and extracts the labeled. In this stage
	 * we can assume that JUMP instructions always contain a NAME instruction
	 * as an address.
	 */
	private String getLabelFromExpr(IMC.Expr addr) {
		return ((IMC.NAME) addr).label.name;
	}

	/**
	 * Generates basic blocks recursively from a vector of stmts
	 */
	private Vector<BasicBlock> generateBasicBlocks(Vector<IMC.Stmt> stmts, MEM.Label exitFunctionLabel) {
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
				blocks.addAll(generateBasicBlocks(stmtsInstr.stmts, exitFunctionLabel));
			} else if (stmt instanceof IMC.LABEL) {
				if (currentBlockStmts.size() == 0) {
					// If the first statement is a label, we use that for our basic block
					label = ((IMC.LABEL) stmt).label;
				} else {
					// We create a new basic block with an unconditional jump
					MEM.Label currentLabel = ((IMC.LABEL) stmt).label;
					BasicBlock block = new BasicBlock(label, new IMC.JUMP(new IMC.NAME(currentLabel)),
							currentBlockStmts);
					blocks.add(block);
					// We then use this label as the start of the new block
					label = currentLabel;
					currentBlockStmts = new Vector<>();
				}
			} else {
				currentBlockStmts.add(stmt);
			}
		}

		// At the end add a block with the label and any instructions still left
		// and jump to the function epilogue
		BasicBlock block = new BasicBlock(label, new IMC.JUMP(new IMC.NAME(exitFunctionLabel)), currentBlockStmts);
		blocks.add(block);

		return blocks;
	}

	private HashMap<String, BasicBlock> makeBasicBlockMap(Vector<BasicBlock> blocks) {
		HashMap<String, BasicBlock> map = new HashMap<>();
		for (BasicBlock block : blocks) {
			map.put(block.label.name, block);
		}
		return map;
	}

	/**
	 * Generates a vector of basic blocks so the false branch of a CJUMP
	 * will always point to the block directly after it.
	 */
	private Vector<BasicBlock> permuteBlocks(BasicBlock firstBlock, HashMap<String, BasicBlock> map) {
		Vector<BasicBlock> permutedBlocks = new Vector<>();
		// We use a stack so we process the elements that need to be added first
		// right after a cjump
		Stack<BasicBlock> stack = new Stack<>();
		stack.push(firstBlock);
		while (!stack.isEmpty()) {
			BasicBlock currentBlock = stack.pop();
			// Check if the block hasn't been used
			if (!map.containsKey(currentBlock.label.name)) {
				continue;
			}

			IMC.Instr jump = currentBlock.jump;
			if (jump instanceof IMC.JUMP) {
				// Here we just try to add the basic block with after the jump
				IMC.JUMP jumpCast = (IMC.JUMP) jump;
				String jumpName = getLabelFromExpr(jumpCast.addr);
				if (map.containsKey(jumpName)) {
					stack.push(map.get(jumpName));
				}
			} else if (jump instanceof IMC.CJUMP) {
				IMC.CJUMP jumpCast = (IMC.CJUMP) jump;
				String falseName = getLabelFromExpr(jumpCast.negAddr);
				String trueName = getLabelFromExpr(jumpCast.posAddr);
				// We must first push the true block because it must get
				// processed after the false block
				if (map.containsKey(trueName)) {
					stack.push(map.get(trueName));
				}
				if (map.containsKey(falseName)) {
					stack.push(map.get(falseName));
				} else {
					// If the block was already inserted into the vector
					// we must create a false block.
					MEM.Label newLabel = new MEM.Label();
					MEM.Label oldLabel = ((IMC.NAME) jumpCast.negAddr).label;

					// Change the negative address to the new block
					jumpCast.negAddr = new IMC.NAME(newLabel);

					BasicBlock interBlock = new BasicBlock(newLabel,
							new IMC.JUMP(new IMC.NAME(oldLabel)), new Vector<>());

					map.put(newLabel.name, interBlock);
					stack.push(interBlock);
				}
			}

			// At the end we mark this block as used by removing it from the map
			map.remove(currentBlock.label.name);
			// And add the block the the permuted vector
			permutedBlocks.add(currentBlock);
		}
		return permutedBlocks;
	}

	/**
	 * Creates a linear vector of statements from the basic blocks. Removes
	 * any unnecessary jumps (like the ones for consecutive blocks)
	 */
	private Vector<IMC.Stmt> linearizeBlocks(Vector<BasicBlock> blocks) {
		Vector<IMC.Stmt> stmts = new Vector<>();
		for (BasicBlock block : blocks) {
			// Check if the last block jumps to the current one and skip the label
			// and remove the last jump
			if (!stmts.isEmpty()) {
				IMC.Stmt jump = stmts.lastElement();
				if (jump instanceof IMC.JUMP) {
					IMC.JUMP jumpCast = (IMC.JUMP) jump;
					if (getLabelFromExpr(jumpCast.addr).equals(block.label.name)) {
						// We remove the jump
						stmts.removeLast();
					}
				} else if (jump instanceof IMC.CJUMP) {
					// We do nothing because CJUMP must exist
				} else {
					// we reach this if the previous block
					// didn't contain a jump aka was the last block
				}
			}

			stmts.add(new IMC.LABEL(block.label));
			stmts.addAll(block.stmts);
			if (block.jump != null) {
				stmts.add(block.jump);
			}
		}
		return stmts;
	}

	public Object visit(AST.DefFunDefn defFunDefn, Object arg) {
		boolean DEBUG = false;
		Vector<IMC.Stmt> stmts = new Vector<>();
		stmts.add(new IMC.LABEL(ImcGen.entryLabel.get(defFunDefn)));
		// Resolve nested calls
		for (AST.Stmt stmt : defFunDefn.stmts) {
			IMC.Instr stmtInstr = ImcGen.stmt.get(stmt);
			IMC.Instr newStmtInstr = stmtInstr.accept(new NestedCallsTransformer(stmts), null);
			stmts.add((IMC.Stmt) newStmtInstr);
			if (DEBUG) {
				Report.info(newStmtInstr.toString());
			}
		}

		// Generate basic blocks and reorder them
		Vector<BasicBlock> blocks = generateBasicBlocks(stmts, ImcGen.exitLabel.get(defFunDefn));
		HashMap<String, BasicBlock> blockMap = makeBasicBlockMap(blocks);
		if (DEBUG) {
			Report.info("original");
			for (BasicBlock block : blocks) {
				Report.info(block.toString());
			}
		}
		Vector<BasicBlock> permutedBlocks = permuteBlocks(blocks.firstElement(), blockMap);
		if (DEBUG) {
			Report.info("linearized");
			for (BasicBlock block : permutedBlocks) {
				Report.info(block.toString());
			}
		}

		// Ge the statements from the permuted blocks
		Vector<IMC.Stmt> linearizedStmts = linearizeBlocks(permutedBlocks);

		MEM.Label entryLabel = ImcGen.entryLabel.get(defFunDefn);
		MEM.Label exitLabel = ImcGen.exitLabel.get(defFunDefn);
		MEM.Frame frame = Memory.frames.get(defFunDefn);
		LIN.CodeChunk codeChunk = new LIN.CodeChunk(frame, linearizedStmts, entryLabel, exitLabel);
		ImcLin.addCodeChunk(codeChunk);

		// Then we visit other functions
		defFunDefn.stmts.accept(this, arg);

		return null;
	}
}
