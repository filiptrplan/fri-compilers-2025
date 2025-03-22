package compiler.phase.seman;

import java.util.*;

import compiler.common.report.*;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.*;

/**
 * Checks for any cycles in the union or struct types
 */
public class CycleChecker implements AST.FullVisitor<Object, Object> {
	public CycleChecker() {
	}

	/**
	 * Returns true if there is a cycle detected in the type definition
	 * e.g. `typ a = a`
	 *
	 * @param typDefn The type definition to check for cycles
	 * @returns True if a cycle is detected
	 */
	private boolean typeHasCycle(AST.TypDefn typDefn) {
		// We just basically do DFS and check whether we detect a cycle
		// First we grab the "origin type", that is the type defined with our name
		TYP.Type originNameType = SemAn.isType.get(typDefn);
		Stack<TYP.Type> dfsStack = new Stack<>();
		dfsStack.push(originNameType.actualType());
		while (!dfsStack.empty()) {
			TYP.Type top = dfsStack.pop();
			// We found a cycle
			if (top.equals(originNameType)) {
				return true;
			}
			// We need to recurse into unions, structs and actual nametypes
			if (top instanceof TYP.StrType) {
				for (TYP.Type t : ((TYP.StrType) top).compTypes) {
					dfsStack.push(t);
				}
			}
			if (top instanceof TYP.UniType) {
				for (TYP.Type t : ((TYP.UniType) top).compTypes) {
					dfsStack.push(t);
				}
			}
			if (top instanceof TYP.NameType) {
				dfsStack.push(((TYP.NameType) top).actualType());
			}
		}

		return false;
	}

	@Override
	public Object visit(AST.TypDefn typDefn, Object arg) {
		if (typeHasCycle(typDefn)) {
			throw new Report.Error(typDefn, "Detected a cyclical type definition.");
		}
		return null;
	}
}
