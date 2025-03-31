package compiler.phase.seman;

import java.util.*;
import java.util.concurrent.Semaphore;

import org.antlr.v4.parse.ANTLRParser.parserRule_return;

import compiler.common.report.*;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.DefFunDefn;
import compiler.phase.abstr.AST.FunDefn;
import compiler.phase.abstr.AST.ParDefn;

/**
 * Type checker.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
class Argument {

}

public class TypeChecker implements AST.FullVisitor<TYP.Type, Argument> {

	/** The type of function we are currently residing in */
	private TYP.FunType containingFunctionType;

	/** Constructs a new name checker. */
	public TypeChecker() {
		containingFunctionType = null;
	}

	/*
	 * Returns true if the two types are equivalent
	 */
	public static boolean equivalent(TYP.Type t1, TYP.Type t2) {
		// EQU1
		if (t1.equals(t2))
			return true;
		// EQU2
		// TODO: See if I need a duplicate rule for T2 == T1
		if (t1 instanceof TYP.NameType && equivalent(t1.actualType(), t2))
			return true;
		// EQU3
		if (t1 instanceof TYP.ArrType && t2 instanceof TYP.ArrType) {
			TYP.ArrType ct1 = (TYP.ArrType) t1;
			TYP.ArrType ct2 = (TYP.ArrType) t2;
			return ct1.numElems == ct2.numElems && equivalent(ct1.elemType, ct2.elemType);
		}
		// EQU4
		if (t1 instanceof TYP.PtrType && t2 instanceof TYP.PtrType) {
			TYP.PtrType ct1 = (TYP.PtrType) t1;
			TYP.PtrType ct2 = (TYP.PtrType) t2;
			return equivalent(ct1.baseType, ct2.baseType);
		}
		// EQU5
		if (t1 instanceof TYP.StrType && t2 instanceof TYP.StrType) {
			TYP.StrType ct1 = (TYP.StrType) t1;
			TYP.StrType ct2 = (TYP.StrType) t2;
			if (ct1.compTypes.size() != ct2.compTypes.size()) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.compTypes.iterator();
			int i = 0;
			// TODO: Refactor with get method of Types
			for (TYP.Type comp1 : ct1.compTypes) {
				TYP.Type comp2 = it2.next();
				// First compare the identifiers of the components
				if (!ct1.compTypeNames.get(i).equals(ct2.compTypeNames.get(i))) {
					return false;
				}
				// Then compare the actual types
				if (!equivalent(comp1, comp2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		// EQU6
		if (t1 instanceof TYP.UniType && t2 instanceof TYP.UniType) {
			TYP.UniType ct1 = (TYP.UniType) t1;
			TYP.UniType ct2 = (TYP.UniType) t2;
			if (ct1.compTypes.size() != ct2.compTypes.size()) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.compTypes.iterator();
			int i = 0;
			for (TYP.Type comp1 : ct1.compTypes) {
				TYP.Type comp2 = it2.next();
				// First compare the identifiers of the components
				if (!ct1.compTypeNames.get(i).equals(ct2.compTypeNames.get(i))) {
					return false;
				}
				// Then compare the actual types
				if (!equivalent(comp1, comp2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		// EQU5
		if (t1 instanceof TYP.FunType && t2 instanceof TYP.FunType) {
			TYP.FunType ct1 = (TYP.FunType) t1;
			TYP.FunType ct2 = (TYP.FunType) t2;

			if (ct1.parTypes.size() != ct2.parTypes.size()) {
				return false;
			}

			// We first compare the return type
			if (!equivalent(ct1.resType, ct2.resType)) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.parTypes.iterator();
			for (TYP.Type par1 : ct1.parTypes) {
				TYP.Type par2 = it2.next();
				// Then compare the actual types
				if (!equivalent(par1, par2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		return false;
	}

	/**
	 * Returns true if type t1 can be coerced into type t2
	 */
	private boolean coerced(TYP.Type t1, TYP.Type t2) {
		// COE1 - 4
		if (t1 instanceof TYP.AtomType) {
			if (equivalent(t2, TYP.IntType.type) && t1.equals(TYP.IntType.type))
				return true;
			if (equivalent(t2, TYP.CharType.type) && t1.equals(TYP.CharType.type))
				return true;
			if (equivalent(t2, TYP.BoolType.type) && t1.equals(TYP.BoolType.type))
				return true;
			if (equivalent(t2, TYP.VoidType.type) && t1.equals(TYP.VoidType.type))
				return true;
			return false;
		}
		// COE5 - 6
		if (t1 instanceof TYP.PtrType) {
			TYP.PtrType ct1 = (TYP.PtrType) t1;
			// COE6
			// I assume that PtrTypes can only be nametypes or raw ptr types so using
			// actualType is enough
			if (ct1.baseType.equals(TYP.VoidType.type) && t2.actualType() instanceof TYP.PtrType) {
				// TODO: Check if this is correct
				return true;
			}
			// COE5
			if (t2 instanceof TYP.PtrType) {
				TYP.PtrType ct2 = (TYP.PtrType) t2;
				return coerced(ct1.baseType, ct2.baseType);
			}

			return false;
		}
		// COE7
		// TODO: Ask if it is intended behaviour we can't coerce an array into a named
		// type?
		if (t1 instanceof TYP.ArrType && t2 instanceof TYP.ArrType) {
			TYP.ArrType ct1 = (TYP.ArrType) t1;
			TYP.ArrType ct2 = (TYP.ArrType) t2;
			return ct1.numElems == ct2.numElems && coerced(ct1.elemType, ct2.elemType);
		}
		// COE8
		if (t1 instanceof TYP.StrType && t2 instanceof TYP.StrType) {
			TYP.StrType ct1 = (TYP.StrType) t1;
			TYP.StrType ct2 = (TYP.StrType) t2;
			if (ct1.compTypes.size() != ct2.compTypes.size()) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.compTypes.iterator();
			int i = 0;
			for (TYP.Type comp1 : ct1.compTypes) {
				TYP.Type comp2 = it2.next();
				// First compare the identifiers of the components
				if (!ct1.compTypeNames.get(i).equals(ct2.compTypeNames.get(i))) {
					return false;
				}
				// Then compare the actual types
				if (!coerced(comp1, comp2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		// COE9
		if (t1 instanceof TYP.UniType && t2 instanceof TYP.UniType) {
			TYP.UniType ct1 = (TYP.UniType) t1;
			TYP.UniType ct2 = (TYP.UniType) t2;
			if (ct1.compTypes.size() != ct2.compTypes.size()) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.compTypes.iterator();
			int i = 0;
			for (TYP.Type comp1 : ct1.compTypes) {
				TYP.Type comp2 = it2.next();
				// First compare the identifiers of the components
				if (!ct1.compTypeNames.get(i).equals(ct2.compTypeNames.get(i))) {
					return false;
				}
				// Then compare the actual types
				if (!coerced(comp1, comp2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		// COE10
		if (t1 instanceof TYP.FunType && t2 instanceof TYP.FunType) {
			TYP.FunType ct1 = (TYP.FunType) t1;
			TYP.FunType ct2 = (TYP.FunType) t2;
			if (ct1.parTypes.size() != ct2.parTypes.size()) {
				return false;
			}
			Iterator<TYP.Type> it2 = ct2.parTypes.iterator();
			for (TYP.Type comp1 : ct1.parTypes) {
				TYP.Type comp2 = it2.next();
				if (!coerced(comp1, comp2)) {
					return false;
				}
			}
			// All checks passed, we return true
			return true;
		}
		// COE11
		if (t1 instanceof TYP.NameType && t2 instanceof TYP.NameType) {
			TYP.NameType ct1 = (TYP.NameType) t1;
			TYP.NameType ct2 = (TYP.NameType) t2;
			return ct1.name.equals(ct2.name) && equivalent(ct1.actualType(), ct2.actualType());
		}
		return false;
	}

	/**
	 * Checks whether the type is equal (not equivalent!) to any of the following
	 * types: int, char, bool, any pointer type, any function type
	 */
	private boolean checkCommonTypes(TYP.Type type) {
		return type instanceof TYP.BoolType || type instanceof TYP.CharType || type instanceof TYP.IntType
				|| type instanceof TYP.PtrType || type instanceof TYP.FunType;
	}

	/**
	 * Checks whether the type is equal (not equivalent!) to any of the following
	 * types: int, char, bool, any pointer type, any function type, void
	 */
	private boolean checkCommonTypesVoid(TYP.Type type) {
		return type instanceof TYP.VoidType || checkCommonTypes(type);
	}

	// ----- Trees -----

	private boolean isTopLevel = true;

	@Override
	public TYP.Type visit(AST.Nodes<? extends AST.Node> nodes, Argument arg) {
		// If we are in the top level we store all the definitions and search for main
		if (isTopLevel == true) {
			isTopLevel = false;
			boolean foundMain = false;
			for (final AST.Node node : nodes) {
				node.accept(this, arg);
				if (node instanceof AST.DefFunDefn && ((AST.DefFunDefn) node).name.equals("main")) {
					TYP.FunType funType = (TYP.FunType) SemAn.ofType.get(node);
					if (!(funType.resType instanceof TYP.IntType)) {
						throw new Report.Error(node, "Function 'main' must return an int.");
					}
					if (funType.parTypes.size() != 0) {
						throw new Report.Error(node, "Function 'main' does not take any parameters.");
					}
					foundMain = true;
				}
			}
			if (!foundMain) {
				throw new Report.Error("'main' function not found.");
			}
			return TYP.VoidType.type;
		} else {
			for (final AST.Node node : nodes) {
				node.accept(this, arg);
			}
			return null;
		}
	}

	// ----- Expressions -----

	@Override
	public TYP.Type visit(AST.ArrExpr arrExpr, Argument arg) {
		TYP.Type arrType = arrExpr.arrExpr.accept(this, arg).actualType();
		TYP.Type idxType = arrExpr.idx.accept(this, arg);
		if (!(arrType instanceof TYP.ArrType)) {
			throw new Report.Error(arrExpr, "Cannot perform an array access on a non-array expression.");
		}
		if (!(equivalent(idxType, TYP.IntType.type))) {
			throw new Report.Error(arrExpr, "Cannot index an array with a non-integer expression.");
		}
		TYP.Type type = ((TYP.ArrType) arrType).elemType;
		SemAn.ofType.put(arrExpr, type);
		SemAn.isConst.put(arrExpr, false);
		SemAn.isAddr.put(arrExpr, true);
		return type;
	}

	@Override
	public TYP.Type visit(AST.AtomExpr atomExpr, Argument arg) {
		TYP.Type type = null;
		switch (atomExpr.type) {
			case INT:
				type = TYP.IntType.type;
				break;
			case STR:
				type = new TYP.PtrType(TYP.CharType.type);
				break;
			case CHAR:
				type = TYP.CharType.type;
				break;
			case BOOL:
				type = TYP.BoolType.type;
				break;
			case PTR:
				// Pointer constants can only be a null pointer
				// All others should be considered as prefix expressions
				if (!atomExpr.value.equals("0")) {
					throw new Report.InternalError();
				}
				type = new TYP.PtrType(TYP.VoidType.type);
				break;
		}
		if (type == null) {
			throw new Report.InternalError();
		}
		SemAn.isConst.put(atomExpr, true);
		SemAn.isAddr.put(atomExpr, false);
		SemAn.ofType.put(atomExpr, type);
		return type;
	}

	@Override
	public TYP.Type visit(AST.BinExpr binExpr, Argument arg) {
		TYP.Type t1 = binExpr.fstExpr.accept(this, arg);
		TYP.Type t2 = binExpr.sndExpr.accept(this, arg);
		TYP.Type type = null;
		switch (binExpr.oper) {
			case AND:
			case OR:
				if (!(equivalent(t1, TYP.BoolType.type) && equivalent(t2, TYP.BoolType.type))) {
					throw new Report.Error(binExpr,
							"Logical operators '&' and '|' require boolean operands. Found: " +
									t1 + " and " + t2);
				}
				if (coerced(t1, t2)) {
					type = t2;
				} else if (coerced(t2, t1)) {
					type = t1;
				} else {
					throw new Report.Error(binExpr,
							String.format("Types %s and %s are not compatible", t1.toString(), t2.toString()));
				}
				break;
			case MUL:
			case DIV:
			case MOD:
			case ADD:
			case SUB:
				if (!(equivalent(t1, TYP.IntType.type) && equivalent(t2, TYP.IntType.type))) {
					throw new Report.Error(binExpr,
							"Arithmetic operators require integer operands. Found: " +
									t1 + " and " + t2);
				}
				if (coerced(t1, t2)) {
					type = t2;
				} else if (coerced(t2, t1)) {
					type = t1;
				} else {
					throw new Report.Error(binExpr,
							String.format("Types %s and %s are not compatible", t1.toString(), t2.toString()));
				}
				break;
			case EQU:
			case NEQ:
			case GTH:
			case GEQ:
			case LTH:
			case LEQ:
				TYP.Type act1 = t1.actualType();
				TYP.Type act2 = t2.actualType();
				if (!(checkCommonTypes(act1) && checkCommonTypes(act2))) {
					throw new Report.Error(binExpr,
							"Unsupported type for comparison operation. Supported types: int, char, bool, pointers, functions");
				}
				if (!(coerced(t1, t2) || coerced(t2, t1))) {
					throw new Report.Error(binExpr,
							String.format("Types %s and %s are not compatible", t1.toString(), t2.toString()));
				}
				type = TYP.BoolType.type;
				break;

		}

		if (type == null) {
			throw new Report.InternalError();
		}
		SemAn.ofType.put(binExpr, type);
		SemAn.isConst.put(binExpr, SemAn.isConst.get(binExpr.fstExpr) && SemAn.isConst.get(binExpr.sndExpr));
		SemAn.isAddr.put(binExpr, false);
		return type;
	}

	@Override
	public TYP.Type visit(AST.CallExpr callExpr, Argument arg) {
		TYP.Type actFunType = callExpr.funExpr.accept(this, arg).actualType();
		if (!(actFunType instanceof TYP.FunType)) {
			throw new Report.Error(callExpr, "Cannot a call a non-function.");
		}

		TYP.FunType funType = (TYP.FunType) actFunType;

		if (funType.parTypes.size() != callExpr.argExprs.size()) {
			throw new Report.Error(callExpr,
					String.format("Unexpected number of arguments. Expected: %d, got: %d", funType.parTypes.size(),
							callExpr.argExprs.size()));
		}

		for (int i = 0; i < funType.parTypes.size(); i++) {
			TYP.Type parType = funType.parTypes.get(i);
			TYP.Type callType = callExpr.argExprs.get(i).accept(this, arg);
			if (!coerced(callType, parType)) {
				throw new Report.Error(callExpr.argExprs.get(i), String.format(
						"Type mismatch for argument %d in call to function '%s'. Expected type '%s', but got '%s'",
						i + 1, callExpr.funExpr.toString(), parType.toString(), callType.toString()));
			}
		}
		TYP.Type retType = funType.resType;
		SemAn.ofType.put(callExpr, retType);
		SemAn.isConst.put(callExpr, false);
		SemAn.isAddr.put(callExpr, false);
		return retType;
	}

	@Override
	public TYP.Type visit(AST.CastExpr castExpr, Argument arg) {
		TYP.Type exprType = castExpr.expr.accept(this, arg);
		TYP.Type castType = SemAn.isType.get(castExpr.type);
		if (equivalent(exprType, TYP.VoidType.type)) {
			throw new Report.Error(castExpr, "Cannot cast an expression of type void.");
		}
		if (equivalent(castType, TYP.VoidType.type)) {
			throw new Report.Error(castExpr, "Cannot cast an expression to void.");
		}
		SemAn.ofType.put(castExpr, castType);
		SemAn.isConst.put(castExpr, SemAn.isConst.get(castExpr.expr));
		SemAn.isAddr.put(castExpr, SemAn.isAddr.get(castExpr.expr));
		return castType;
	}

	@Override
	public TYP.Type visit(AST.CompExpr compExpr, Argument arg) {
		TYP.Type type = compExpr.recExpr.accept(this, arg).actualType();
		if (!(type instanceof TYP.RecType)) {
			throw new Report.Error(compExpr,
					"Cannot perform component access on an expression that isn't of a union or struct type");
		}
		TYP.RecType recType = (TYP.RecType) type;
		int elIdx = recType.compTypeNames.indexOf(compExpr.name);
		if (elIdx == -1) {
			throw new Report.Error(compExpr,
					String.format("Component with name '%s' does not exist in the type '%s'", compExpr.name,
							SemAn.ofType.get(compExpr.recExpr)));
		}

		if (!SemAn.isAddr.get(compExpr.recExpr)) {
			throw new Report.Error(compExpr,
					"Cannot perform component access on an expression that doesn't represent and address.");
		}

		TYP.Type exprType = recType.compTypes.get(elIdx);
		SemAn.ofType.put(compExpr, exprType);
		SemAn.isConst.put(compExpr, false);
		SemAn.isAddr.put(compExpr, true);
		return exprType;
	}

	@Override
	public TYP.Type visit(AST.NameExpr nameExpr, Argument arg) {
		AST.Node defn = SemAn.defAt.get(nameExpr);
		if (!(defn instanceof AST.VarDefn || defn instanceof AST.FunDefn || defn instanceof AST.ParDefn)) {
			throw new Report.Error(nameExpr, "The symbol '" + nameExpr.name + "' is not a valid expression.");
		}
		TYP.Type type = SemAn.ofType.get(SemAn.defAt.get(nameExpr));
		SemAn.ofType.put(nameExpr, type);
		SemAn.isConst.put(nameExpr, false);
		SemAn.isAddr.put(nameExpr, true);
		return type;
	}

	@Override
	public TYP.Type visit(AST.PfxExpr pfxExpr, Argument arg) {
		TYP.Type type = null;
		TYP.Type subType = pfxExpr.subExpr.accept(this, arg);
		switch (pfxExpr.oper) {
			case ADD:
			case SUB:
				if (!equivalent(subType, TYP.IntType.type)) {
					throw new Report.Error(pfxExpr,
							String.format("Prefix operators '+' and '-' require an integer operand. Found: %s",
									subType));
				}
				type = subType;
				SemAn.isConst.put(pfxExpr, SemAn.isConst.get(pfxExpr.subExpr));
				break;
			case NOT:
				if (!equivalent(subType, TYP.BoolType.type)) {
					throw new Report.Error(pfxExpr,
							String.format("Prefix operator '!' requires a boolean operand. Found: %s", subType));
				}
				type = subType;
				SemAn.isConst.put(pfxExpr, SemAn.isConst.get(pfxExpr.subExpr));
				break;
			case PTR:
				if (equivalent(subType, TYP.VoidType.type)) {
					throw new Report.Error(pfxExpr, "Cannot create pointer from an expression of type 'void'.");
				}
				if (!SemAn.isAddr.get(pfxExpr.subExpr)) {
					throw new Report.Error(pfxExpr,
							"Cannot create a pointer from an expression that is not an address");
				}
				type = new TYP.PtrType(subType);
				SemAn.isConst.put(pfxExpr, false);
				break;
		}
		if (type == null) {
			throw new Report.InternalError();
		}
		SemAn.ofType.put(pfxExpr, type);
		SemAn.isAddr.put(pfxExpr, false);
		return type;
	}

	@Override
	public TYP.Type visit(AST.SfxExpr sfxExpr, Argument arg) {
		TYP.Type type = null;
		TYP.Type subType = sfxExpr.subExpr.accept(this, arg).actualType();
		switch (sfxExpr.oper) {
			case PTR:
				if (!(subType instanceof TYP.PtrType)) {
					throw new Report.Error(sfxExpr, "Cannot dereference a non-pointer type");
				}
				TYP.PtrType ptrType = (TYP.PtrType) subType;
				if (equivalent(ptrType, TYP.VoidType.type)) {
					throw new Report.Error(sfxExpr,
							"Void type not allowed for the dereference operator.");
				}
				if (SemAn.isConst.get(sfxExpr.subExpr)) {
					throw new Report.Error(sfxExpr.subExpr, "Cannot dereference a constant expression");
				}
				type = ptrType.baseType;
				break;
		}
		if (type == null) {
			throw new Report.InternalError();
		}
		SemAn.ofType.put(sfxExpr, type);
		SemAn.isConst.put(sfxExpr, false);
		SemAn.isAddr.put(sfxExpr, true);
		return type;
	}

	@Override
	public TYP.Type visit(AST.SizeExpr sizeExpr, Argument arg) {
		TYP.Type subType = SemAn.isType.get(sizeExpr.type);
		if (equivalent(subType, TYP.VoidType.type)) {
			throw new Report.Error(sizeExpr, "Cannot determine size of void.");
		}
		SemAn.ofType.put(sizeExpr, TYP.IntType.type);
		SemAn.isAddr.put(sizeExpr, false);
		SemAn.isConst.put(sizeExpr, true);
		return TYP.IntType.type;
	}

	// ----- Types ------

	@Override
	public TYP.Type visit(AST.ArrType arrType, Argument arg) {
		if (equivalent(SemAn.isType.get(arrType.elemType), TYP.VoidType.type)) {
			throw new Report.Error(arrType, "Cannot declare an array of type void.");
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.StrType strType, Argument arg) {
		for (AST.CompDefn compDefn : strType.comps) {
			if (equivalent(SemAn.ofType.get(compDefn), TYP.VoidType.type)) {
				throw new Report.Error(compDefn, "Cannot declare a component of type void.");
			}
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.UniType uniType, Argument arg) {
		for (AST.CompDefn compDefn : uniType.comps) {
			if (equivalent(SemAn.isType.get(compDefn.type), TYP.VoidType.type)) {
				throw new Report.Error(compDefn, "Cannot declare a component of type void.");
			}
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.FunType funType, Argument arg) {
		TYP.Type resType = SemAn.isType.get(funType.resType).actualType();
		if (!(checkCommonTypesVoid(resType))) {
			throw new Report.Error(funType.resType, String.format(
					"Invalid result type '%s' for a function. Valid types: int, char, bool, void, any pointer, any function.",
					resType.toString()));
		}
		for (AST.Type astType : funType.parTypes) {
			if (!checkCommonTypes(SemAn.isType.get(astType).actualType())) {
				throw new Report.Error(astType, String.format(
						"Invalid parameter type '%s' for a function. Valid types: int, char, bool, any pointer, any function.",
						SemAn.isType.get(astType).toString()));
			}
		}
		return null;
	}

	// ----- Statements -----

	@Override
	public TYP.Type visit(AST.LetStmt letStmt, Argument arg) {
		letStmt.defns.accept(this, arg);
		letStmt.stmts.accept(this, arg);
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.AssignStmt assignStmt, Argument arg) {
		TYP.Type t1 = assignStmt.dstExpr.accept(this, arg);
		TYP.Type t2 = assignStmt.srcExpr.accept(this, arg);
		if (!checkCommonTypes(t1.actualType())) {
			throw new Report.Error(assignStmt.dstExpr,
					"Invalid type for a destination expression. Valid types: int, char, bool, any pointer, any function.");

		}
		if (!checkCommonTypes(t2.actualType())) {
			throw new Report.Error(assignStmt.dstExpr,
					"Invalid type for a source expression. Valid types: int, char, bool, any pointer, any function.");

		}

		if (!coerced(t2, t1)) {
			throw new Report.Error(assignStmt,
					String.format("Types %s and %s are not compatible", t1.toString(), t2.toString()));
		}

		if (!SemAn.isAddr.get(assignStmt.dstExpr)) {
			throw new Report.Error(assignStmt.dstExpr, "Assignment requires and adressable expression.");
		}

		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.ExprStmt callStmt, Argument arg) {
		if ((callStmt.expr != null) || (!compiler.Compiler.devMode()))
			callStmt.expr.accept(this, arg);
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.IfThenStmt ifThenStmt, Argument arg) {
		TYP.Type condType = ifThenStmt.condExpr.accept(this, arg);
		if (!equivalent(condType, TYP.BoolType.type)) {
			throw new Report.Error(ifThenStmt.condExpr,
					"The condition expression must be of type bool. Found: " + condType.toString());
		}
		for (AST.Node node : ifThenStmt.thenStmt) {
			if (!equivalent(node.accept(this, arg), TYP.VoidType.type)) {
				// Something went wrong
				throw new Report.InternalError();
			}
		}
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.IfThenElseStmt ifThenElseStmt, Argument arg) {
		TYP.Type condType = ifThenElseStmt.condExpr.accept(this, arg);
		if (!equivalent(condType, TYP.BoolType.type)) {
			throw new Report.Error(ifThenElseStmt.condExpr, "The condition expression must be of type bool.");
		}

		ifThenElseStmt.thenStmt.accept(this, arg);
		ifThenElseStmt.elseStmt.accept(this, arg);
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.ReturnStmt returnStmt, Argument arg) {
		// This should be unreachable but we check anyway
		if (this.containingFunctionType == null) {
			throw new Report.InternalError();
		}

		TYP.Type t1 = this.containingFunctionType.resType;
		TYP.Type t2 = returnStmt.retExpr.accept(this, arg);

		if (!coerced(t2, t1)) {
			throw new Report.Error(returnStmt.retExpr,
					String.format("Type %s is not compatible with return type %s.", t2.toString(), t1.toString()));
		}

		// This should in theory already be covered by the coercion and the type check
		// for the function definition, but we do it anyway
		if (!checkCommonTypes(t2.actualType())) {
			throw new Report.Error(returnStmt.retExpr,
					"Invalid type for a return expression. Valid types: int, char, bool, any pointer, any function.");
		}

		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.WhileStmt whileStmt, Argument arg) {
		TYP.Type condType = whileStmt.condExpr.accept(this, arg);
		if (!equivalent(condType, TYP.BoolType.type)) {
			throw new Report.Error(whileStmt.condExpr,
					"The condition expression must be of type bool. Found: " + condType.toString());
		}
		whileStmt.stmts.accept(this, arg);
		return TYP.VoidType.type;
	}

	// ----- Definitions ------

	@Override
	public TYP.Type visit(AST.DefFunDefn defFunDefn, Argument arg) {
		TYP.FunType funType = (TYP.FunType) SemAn.ofType.get(defFunDefn);

		for (int i = 0; i < funType.parTypes.size(); i++) {
			TYP.Type parType = funType.parTypes.get(i);
			if (!checkCommonTypes(parType.actualType())) {
				throw new Report.Error(defFunDefn.type, String.format(
						"Invalid type '%s' for a function parameter. Valid types: int, char, bool, any pointer, any function.",
						parType.toString()));
			}
		}

		TYP.Type retType = funType.resType;
		if (!(checkCommonTypesVoid(retType.actualType()))) {
			throw new Report.Error(defFunDefn.type, String.format(
					"Invalid type '%s' for a result type. Valid types: int, char, bool, void, any pointer, any function.",
					retType.toString()));
		}

		defFunDefn.pars.accept(this, arg);
		defFunDefn.type.accept(this, arg);
		TYP.FunType oldFunType = this.containingFunctionType;

		this.containingFunctionType = (TYP.FunType) SemAn.ofType.get(defFunDefn);
		defFunDefn.stmts.accept(this, arg);
		this.containingFunctionType = oldFunType;

		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.TypDefn typDefn, Argument arg) {
		typDefn.type.accept(this, arg);
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.VarDefn varDefn, Argument arg) {
		varDefn.type.accept(this, arg);
		if (equivalent(SemAn.isType.get(varDefn.type), TYP.VoidType.type)) {
			throw new Report.Error(varDefn.type, "Variables cannot have a type of void.");
		}
		return TYP.VoidType.type;
	}

	@Override
	public TYP.Type visit(AST.ExtFunDefn extFunDefn, Argument arg) {

		TYP.FunType funType = (TYP.FunType) SemAn.ofType.get(extFunDefn);

		for (int i = 0; i < funType.parTypes.size(); i++) {
			TYP.Type parType = funType.parTypes.get(i);
			if (!checkCommonTypes(parType.actualType())) {
				throw new Report.Error(extFunDefn.type, String.format(
						"Invalid type '%s' for a function parameter. Valid types: int, char, bool, any pointer, any function.",
						parType.toString()));
			}
		}

		TYP.Type retType = funType.resType;
		if (!(checkCommonTypesVoid(retType.actualType()))) {
			throw new Report.Error(extFunDefn.type, String.format(
					"Invalid type '%s' for a result type. Valid types: int, char, bool, void, any pointer, any function.",
					retType.toString()));
		}

		extFunDefn.pars.accept(this, arg);
		extFunDefn.type.accept(this, arg);
		return TYP.VoidType.type;
	}
}
