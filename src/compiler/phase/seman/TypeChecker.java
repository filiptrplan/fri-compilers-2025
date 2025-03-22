package compiler.phase.seman;

import java.util.*;

import compiler.common.report.*;
import compiler.phase.abstr.*;

/**
 * Type checker.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
class Argument {

}

public class TypeChecker implements AST.FullVisitor<TYP.Type, Argument> {

	/** Constructs a new name checker. */
	public TypeChecker() {
	}

	/*
	 * Returns true if the two types are equivalent
	 */
	private boolean equivalent(TYP.Type t1, TYP.Type t2) {
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
	}

	/**
	 * Returns true if type t1 can be coerced into type t2
	 */
	private boolean coerced(TYP.Type t1, TYP.Type t2) {
		// COE1 - 4
		if (t2 instanceof TYP.AtomType) {
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
				return coerced(t1, t2);
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
	}

}
