package compiler.phase.seman;

import compiler.common.report.*;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.*;

/**
 * Checks for any cycles in the union or struct types
 */
public class CycleChecker implements AST.FullVisitor<Object, Object> {
	public CycleChecker() {
	}

	@Override
	public Object visit(AST.NameType nameType, Object arg) {
		TYP.NameType type = (TYP.NameType) SemAn.isType.get(nameType);
		TYP.Type actType = type.actualType();
		Report.info(actType.toString());
		return null;
	}
}
