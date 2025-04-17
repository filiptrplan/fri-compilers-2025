package compiler.phase.imclin;

import compiler.phase.abstr.AST;

public class CodeChunkGenerator implements AST.FullVisitor<Object, Object> {
	private NestedCallsTransformer nestedCallsTransformer = new NestedCallsTransformer();

	public Object visit(AST.DefFunDefn defFunDefn, Object arg) {
		defFunDefn.stmts.accept(nestedCallsTransformer, null);
		return null;
	}
}
