package compiler.phase.imclin;

import compiler.phase.abstr.AST;

/**
 * Constructs basic blocks for each function
 */
public class BasicBlocksTransformer implements AST.FullVisitor<Object, Object> {

	@Override
	public Object visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		return null;
	}
}
