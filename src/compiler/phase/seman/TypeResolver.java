package compiler.phase.seman;

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

	// ----- Types -----

	@Override
	public TYP.Type visit(AST.AtomType atomType, TypeResolver.Mode arg) {
		switch (atomType.type) {
			case INT:
				return TYP.IntType.type;
			case VOID:
				return TYP.VoidType.type;
			case CHAR:
				return TYP.CharType.type;
			case BOOL:
				return TYP.BoolType.type;
		}
		// Unreachable
		return null;
	}

	protected enum Mode {
		DEFINE,
		RESOLVE
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
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.DefFunDefn defFunDefn, TypeResolver.Mode arg) {
		// We first should wait for all the types to be defined. That's why we are not
		// doing anything in the DEFINE mode.
		if (arg == Mode.RESOLVE) {
			SemAn.ofType.put(defFunDefn, defFunDefn.type.accept(this, arg));
			defFunDefn.pars.accept(this, arg);
			defFunDefn.stmts.accept(this, arg);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.ExtFunDefn extFunDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			SemAn.ofType.put(extFunDefn, extFunDefn.type.accept(this, arg));
			extFunDefn.pars.accept(this, arg);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.ParDefn parDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			SemAn.ofType.put(parDefn, parDefn.type.accept(this, arg));
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.CompDefn compDefn, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			SemAn.ofType.put(compDefn, compDefn.type.accept(this, arg));
		}
		return null;
	}

	// ------- Types ---------

	@Override
	public TYP.Type visit(AST.ArrType arrType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			try {
				Long numElems = Long.parseLong(arrType.numElems);
				return new TYP.ArrType(arrType.elemType.accept(this, arg), numElems);
			} catch (NumberFormatException e) {
				throw new Report.Error("Invalid integer constant: '" + arrType.numElems + "'");
			}
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.PtrType ptrType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			return new TYP.PtrType(ptrType.baseType.accept(this, arg));
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.StrType strType, TypeResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			strType.comps.accept(this, arg);
		}
		return null;
	}

	@Override
	public TYP.Type visit(AST.UniType uniType, TypeResolver.Mode arg) {
		if ((uniType.comps != null) || (!compiler.Compiler.devMode()))
			uniType.comps.accept(this, arg);
		return null;
	}

	@Override
	public TYP.Type visit(AST.FunType funType, TypeResolver.Mode arg) {
		if ((funType.parTypes != null) || (!compiler.Compiler.devMode()))
			funType.parTypes.accept(this, arg);
		if ((funType.resType != null) || (!compiler.Compiler.devMode()))
			funType.resType.accept(this, arg);
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
			return SemAn.isType.get(defType);
		}
		return null;
	}

}
