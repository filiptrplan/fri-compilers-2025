package compiler.phase.seman;

import java.math.BigInteger;
import java.util.*;

import compiler.common.report.*;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.*;
import compiler.phase.seman.TYP;
import compiler.phase.seman.TYP.NameType;

/**
 * Type resolver.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class TypeResolver implements AST.FullVisitor<TYP.Type, TypeResolver.Mode> {

	/** Constructs a new name resolver. */
	public TypeResolver() {
	}

	protected enum Mode {
		DEFINE,
		RESOLVE,
	}
	// ----- Trees -----

	@Override
	public TYP.Type visit(AST.Nodes<? extends Node> nodes, Mode arg) {
		// We do two passes to resolve names without recursing infinitely
		// First we assign NameTypes in the isType table. That way, we make sure
		//
		// that all the names that can currently be used (and the definitions they point
		// to)
		// currently have types.
		// After that we can recurse into the "bodies" of the types and resolve ACTUAL
		// types of the NameTypes

		if (nodes.size() == 0) {
			return null;
		}

		// If we have definitions we have the two modes, otherwise we just
		// recurse into the statements
		if (nodes.iterator().next() instanceof Defn) {
			for (final Node node : nodes) {
				node.accept(this, Mode.DEFINE);
			}
			for (final Node node : nodes) {
				node.accept(this, Mode.RESOLVE);
			}
		} else {
			for (final Node node : nodes)
				node.accept(this, Mode.RESOLVE);
		}

		// On the top level pass we also do a cycle check
		if (arg == null) {
			CycleChecker checker = new CycleChecker();
			nodes.accept(checker, null);
		}

		return null;
	}

	// ------- Definitions --------

	@Override
	public TYP.Type visit(AST.TypDefn typDefn, TypeResolver.Mode arg) {
		// If we are in DECLARE mode we are in the first pass we store a NameType there
		// without actually resolving the actual type
		if (arg == Mode.DEFINE) {
			SemAn.isType.put(typDefn, new TYP.NameType(typDefn.name));
		}
		if (arg == Mode.RESOLVE) {
			TYP.Type type = typDefn.type.accept(this, arg);
			TYP.Type nameType = SemAn.isType.get(typDefn);
			if (!(nameType instanceof TYP.NameType)) {
				throw new Report.Error("This should be unreachable. The compiler dev messed up.");
			}
			((TYP.NameType) nameType).setActType(type);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.VarDefn varDefn, TypeResolver.Mode arg) {
		// We are not defining any types here
		if (arg == Mode.RESOLVE) {
			SemAn.ofType.put(varDefn, varDefn.type.accept(this, arg));
			SemAn.isConst.put(varDefn, false);
			SemAn.isAddr.put(varDefn, true);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.DefFunDefn defFunDefn, TypeResolver.Mode arg) {
		// We first should wait for all the types to be defined. That's why we are not
		// doing anything in the DEFINE mode.
		if (arg == Mode.RESOLVE) {
			TYP.Type returnType = defFunDefn.type.accept(this, arg);
			List<TYP.Type> typeList = new ArrayList<>();
			for (AST.ParDefn parDefn : defFunDefn.pars) {
				TYP.Type type = parDefn.accept(this, arg);
				typeList.add(type);
				SemAn.ofType.put(parDefn, type);
				SemAn.isConst.put(parDefn, false);
				SemAn.isAddr.put(parDefn, true);
			}
			SemAn.ofType.put(defFunDefn, new TYP.FunType(typeList, returnType));
			SemAn.isAddr.put(defFunDefn, false);
			SemAn.isConst.put(defFunDefn, false);
			defFunDefn.stmts.accept(this, arg);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.ExtFunDefn extFunDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			TYP.Type returnType = extFunDefn.type.accept(this, arg);
			List<TYP.Type> typeList = new ArrayList<>();
			for (AST.ParDefn parDefn : extFunDefn.pars) {
				TYP.Type type = parDefn.accept(this, arg);
				typeList.add(type);
				SemAn.ofType.put(parDefn, type);
			}
			SemAn.ofType.put(extFunDefn, new TYP.FunType(typeList, returnType));
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.ParDefn parDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			TYP.Type type = parDefn.type.accept(this, arg);
			SemAn.ofType.put(parDefn, type);
			return type;
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.CompDefn compDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			TYP.Type type = compDefn.type.accept(this, arg);
			SemAn.ofType.put(compDefn, type);
			return type;
		}
		return null;
	}

	// ------- Types ---------

	@Override
	public TYP.Type visit(AST.AtomType atomType, TypeResolver.Mode arg) {
		TYP.Type type = null;
		switch (atomType.type) {
			case INT:
				type = TYP.IntType.type;
				break;
			case VOID:
				type = TYP.VoidType.type;
				break;
			case CHAR:
				type = TYP.CharType.type;
				break;
			case BOOL:
				type = TYP.BoolType.type;
				break;
		}
		if (type == null) {
			throw new Report.InternalError();
		}
		SemAn.isType.put(atomType, type);
		return type;
	}

	@Override
	public TYP.Type visit(AST.ArrType arrType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			try {
				BigInteger numElems = new BigInteger(arrType.numElems);
				if (numElems.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
					throw new Report.Error(arrType, "Array length can't be larger than 2^63");
				}
				if (numElems.compareTo(BigInteger.ZERO) == 0) {
					throw new Report.Error(arrType, "Array can't have a length of 0");
				}
				TYP.Type type = new TYP.ArrType(arrType.elemType.accept(this, arg), numElems.longValueExact());
				SemAn.isType.put(arrType, type);
				return type;
			} catch (NumberFormatException e) {
				throw new Report.Error(arrType, "Invalid integer constant: '" + arrType.numElems + "'");
			}
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.PtrType ptrType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			TYP.Type type = ptrType.baseType.accept(this, arg);
			if (type.equals(TYP.VoidType.type)) {
				throw new Report.Error(ptrType, "Can't construct pointer type ptr(VOID)");
			}
			TYP.Type actType = new TYP.PtrType(type);
			SemAn.isType.put(ptrType, actType);
			return actType;
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.StrType strType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			List<TYP.Type> typeList = new ArrayList<>();
			List<String> names = new ArrayList<String>();
			for (AST.CompDefn node : strType.comps) {
				TYP.Type type = node.accept(this, arg);
				SemAn.ofType.put(node, type);
				typeList.add(type);
				names.add(node.name);
			}
			TYP.Type type = new TYP.StrType(typeList, names);
			SemAn.isType.put(strType, type);
			return type;
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.UniType uniType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			List<TYP.Type> typeList = new ArrayList<>();
			List<String> names = new ArrayList<String>();
			for (AST.CompDefn node : uniType.comps) {
				TYP.Type type = node.accept(this, arg);
				SemAn.ofType.put(node, type);
				typeList.add(type);
				names.add(node.name);
			}
			TYP.Type type = new TYP.UniType(typeList, names);
			SemAn.isType.put(uniType, type);
			return type;
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.FunType funType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			TYP.Type returnType = funType.resType.accept(this, arg);
			List<TYP.Type> typeList = new ArrayList<>();
			for (AST.Node node : funType.parTypes) {
				typeList.add(node.accept(this, arg));
			}
			TYP.Type type = new TYP.FunType(typeList, returnType);
			SemAn.isType.put(funType, type);
			return type;
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.NameType nameType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			// We know that the name is defined, but we need to check whether it is a type
			AST.Node defType = SemAn.defAt.get(nameType);
			if (!(defType instanceof TypDefn)) {
				throw new Report.Error("The symbol '" + nameType.name + "' is not a type.");
			}
			// Now that we know it is a type we can get it from the isType table
			// it should be already defined as we are in the RESOLVE stage.
			TYP.Type type = SemAn.isType.get(defType);
			SemAn.isType.put(nameType, type);
			return type;
		}
		return null;
	}

}
