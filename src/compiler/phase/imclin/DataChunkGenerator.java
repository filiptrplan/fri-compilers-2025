
package compiler.phase.imclin;

import compiler.phase.abstr.AST;
import compiler.phase.memory.MEM;
import compiler.phase.memory.Memory;

public class DataChunkGenerator implements AST.FullVisitor<Object, Object> {
	@Override
	public Object visit(AST.AtomExpr atomExpr, Object arg) {
		if (atomExpr.type == AST.AtomExpr.Type.STR) {
			MEM.AbsAccess access = Memory.strings.get(atomExpr);
			ImcLin.addDataChunk(new LIN.DataChunk(access, LIN.DataChunkType.StringConst));
		}
		return null;
	}

	@Override
	public Object visit(AST.VarDefn varDefn, Object arg) {
		MEM.Access access = Memory.accesses.get(varDefn);
		if (access instanceof MEM.AbsAccess) {
			ImcLin.addDataChunk(new LIN.DataChunk((MEM.AbsAccess) access, LIN.DataChunkType.GlobalVar));
		}
		return null;
	}
}
