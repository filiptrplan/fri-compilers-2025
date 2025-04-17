package compiler.phase.imclin;

import java.util.Vector;

import compiler.phase.abstr.AST;
import compiler.phase.imcgen.IMC;
import compiler.phase.imcgen.ImcGen;
import compiler.phase.memory.MEM;
import compiler.phase.memory.Memory;

public class CodeChunkGenerator implements AST.FullVisitor<Object, Object> {
	public Object visit(AST.DefFunDefn defFunDefn, Object arg) {
		Vector<IMC.Stmt> stmts = new Vector<>();
		for (AST.Stmt stmt : defFunDefn.stmts) {
			IMC.Instr stmtInstr = ImcGen.stmt.get(stmt);
			IMC.Instr newStmtInstr = stmtInstr.accept(new NestedCallsTransformer(stmts), null);
			stmts.add((IMC.Stmt) newStmtInstr);
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
