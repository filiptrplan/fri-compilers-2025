package compiler.phase.imclin;

import compiler.phase.abstr.AST;

/**
 * Mutates expression in-place to replace any nested calls with MOVEs
 */
public class NestedCallsTransformer implements AST.FullVisitor<Object, Object> {

	@Override
	public Object visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		return null;
	}
}
