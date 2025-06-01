package compiler.phase.imcgen;

import compiler.phase.abstr.AST;

/**
 * Checks whether the visited function node is the direct parent of the function
 * that is passed as an argument to the visitor. Returns true if the visited
 * function is the direct parent of the argument function.
 */
public class DirectChildChecker implements AST.NullVisitor<Boolean, AST.DefFunDefn> {
	private boolean started = false;

	public Boolean visit(AST.DefFunDefn defFunDefn, AST.DefFunDefn child) {
		// The visitor was just called
		if (!started) {
			started = true;
			return defFunDefn.stmts.accept(this, child);
		} else {
			return defFunDefn.equals(child);
		}
	}

	public Boolean visit(AST.Nodes<? extends AST.Node> nodes, AST.DefFunDefn child) {
		for (final AST.Node node : nodes) {
			Boolean ret = node.accept(this, child);
			if (ret != null && ret == true) {
				return true;
			}
		}
		return false;
	}

	public Boolean visit(AST.LetStmt letStmt, AST.DefFunDefn child) {
		return letStmt.stmts.accept(this, child) || letStmt.defns.accept(this, child);
	}
}
