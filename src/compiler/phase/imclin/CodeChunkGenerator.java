package compiler.phase.imclin;

import compiler.phase.abstr.AST;

public class CodeChunkGenerator implements AST.FullVisitor<Object, Object> {
	private NestedCallsTransformer nestedCallsTransformer = new NestedCallsTransformer();

	@Override
	public Object visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		nodes.accept(nestedCallsTransformer, null);
		return null;
	}
}
