package compiler.phase.seman;

import java.util.*;
import java.util.function.*;

import compiler.common.report.*;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.*;
import compiler.phase.seman.NameResolver.SymbTable.CannotFndNameException;
import compiler.phase.seman.NameResolver.SymbTable.CannotInsNameException;

/**
 * Name resolver.
 * 
 * The name resolver connects each node of a abstract syntax tree where a name
 * is used with the node where it is defined. The only exceptions are struct and
 * union component names which are connected with their definitions by the type
 * resolver. The results of the name resolver are stored in
 * {@link compiler.phase.seman.SemAn#defAt}.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class NameResolver implements AST.FullVisitor<Object, NameResolver.Mode> {

	/** Constructs a new name resolver. */
	public NameResolver() {
	}

	/** Two passes of name resolving. */
	protected enum Mode {
		/** The first pass: declaring names. */
		DECLARE,
		/** The second pass: resolving names. */
		RESOLVE,
	}

	/** The symbol table. */
	private SymbTable symbTable = new SymbTable();

	// ===== SYMBOL TABLE =====

	/**
	 * A symbol table.
	 */
	public class SymbTable {

		/**
		 * A symbol table record denoting a definition of a name within a certain scope.
		 */
		private class ScopedDefn {

			/** The depth of the scope the definition belongs to. */
			public final int depth;

			/** The definition. */
			public final AST.Defn defn;

			/**
			 * Constructs a new record denoting a definition of a name within a certain
			 * scope.
			 * 
			 * @param depth The depth of the scope the definition belongs to.
			 * @param defn  The definition.
			 */
			public ScopedDefn(int depth, AST.Defn defn) {
				this.depth = depth;
				this.defn = defn;
			}

		}

		/**
		 * A mapping of names into lists of records denoting definitions at different
		 * scopes. At each moment during the lifetime of a symbol table, the definition
		 * list corresponding to a particular name contains all definitions that name
		 * within currently active scopes: the definition at the inner most scope is the
		 * first in the list and is visible, the other definitions are hidden.
		 */
		private final HashMap<String, LinkedList<ScopedDefn>> allDefnsOfAllNames;

		/**
		 * The list of scopes. Each scope is represented by a list of names defined
		 * within it.
		 */
		private final LinkedList<LinkedList<String>> scopes;

		/** The depth of the currently active scope. */
		private int currDepth;

		/** Whether the symbol table can no longer be modified or not. */
		private boolean lock;

		/**
		 * Constructs a new symbol table.
		 */
		public SymbTable() {
			allDefnsOfAllNames = new HashMap<String, LinkedList<ScopedDefn>>();
			scopes = new LinkedList<LinkedList<String>>();
			currDepth = 0;
			lock = false;
			newScope();
		}

		/**
		 * Returns the depth of the currently active scope.
		 * 
		 * @return The depth of the currently active scope.
		 */
		public int currDepth() {
			return currDepth;
		}

		/**
		 * Inserts a new definition of a name within the currently active scope or
		 * throws an exception if this name has already been defined within this scope.
		 * Once the symbol table is locked, any attempt to insert further definitions
		 * results in an internal error.
		 * 
		 * @param name The name.
		 * @param defn The definition.
		 * @throws CannotInsNameException Thrown if this name has already been defined
		 *                                within the currently active scope.
		 */
		public void ins(String name, AST.Defn defn) throws CannotInsNameException {
			if (lock)
				throw new Report.InternalError();

			LinkedList<ScopedDefn> allDefnsOfName = allDefnsOfAllNames.get(name);
			if (allDefnsOfName == null) {
				allDefnsOfName = new LinkedList<ScopedDefn>();
				allDefnsOfAllNames.put(name, allDefnsOfName);
			}

			if (!allDefnsOfName.isEmpty()) {
				ScopedDefn defnOfName = allDefnsOfName.getFirst();
				if (defnOfName.depth == currDepth)
					throw new CannotInsNameException();
			}

			allDefnsOfName.addFirst(new ScopedDefn(currDepth, defn));
			scopes.getFirst().addFirst(name);
		}

		/**
		 * Returns the currently visible definition of the specified name. If no
		 * definition of the name exists within these scopes, an exception is thrown.
		 * 
		 * @param name The name.
		 * @return The definition.
		 * @throws CannotFndNameException Thrown if the name is not defined within the
		 *                                currently active scope or any scope enclosing
		 *                                it.
		 */
		public AST.Defn fnd(String name) throws CannotFndNameException {
			LinkedList<ScopedDefn> allDefnsOfName = allDefnsOfAllNames.get(name);
			if (allDefnsOfName == null)
				throw new CannotFndNameException();

			if (allDefnsOfName.isEmpty())
				throw new CannotFndNameException();

			return allDefnsOfName.getFirst().defn;
		}

		/** Used for selecting the range of scopes. */
		public enum XScopeSelector {
			/** All live scopes. */
			ALL,
			/** Currently active scope. */
			ACT,
		}

		/**
		 * Constructs a new scope within the currently active scope. The newly
		 * constructed scope becomes the currently active scope.
		 */
		public void newScope() {
			if (lock)
				throw new Report.InternalError();

			currDepth++;
			scopes.addFirst(new LinkedList<String>());
		}

		/**
		 * Destroys the currently active scope by removing all definitions belonging to
		 * it from the symbol table. Makes the enclosing scope the currently active
		 * scope.
		 */
		public void oldScope() {
			if (lock)
				throw new Report.InternalError();

			if (currDepth == 0)
				throw new Report.InternalError();

			for (String name : scopes.getFirst()) {
				allDefnsOfAllNames.get(name).removeFirst();
			}
			scopes.removeFirst();
			currDepth--;
		}

		/**
		 * Prevents further modifications of this symbol table.
		 */
		public void lock() {
			lock = true;
		}

		/**
		 * An exception thrown when the name cannot be inserted into a symbol table.
		 */
		@SuppressWarnings("serial")
		public class CannotInsNameException extends Exception {

			/**
			 * Constructs a new exception.
			 */
			private CannotInsNameException() {
			}

		}

		/**
		 * An exception thrown when the name cannot be found in the symbol table.
		 */
		@SuppressWarnings("serial")
		public class CannotFndNameException extends Exception {

			/**
			 * Constructs a new exception.
			 */
			private CannotFndNameException() {
			}

		}

	}

	private void declareName(Defn defn) {
		try {
			symbTable.ins(defn.name, defn);
		} catch (CannotInsNameException e) {
			throw new Report.Error(defn.location(),
					"Cannot re-declare symbol with name '" + defn.name + "' in the same scope.");
		}
	}

	private void resolveName(Node node, String name) {
		try {
			SemAn.defAt.put(node, symbTable.fnd(name));
		} catch (CannotFndNameException e) {
			throw new Report.Error(node.location(), "Cannot find symbol with name '" + name + "'");
		}
	}

	@Override
	public Object visit(Nodes<? extends Node> nodes, Mode arg) {
		// First we do a pass of declaration, then we resolve
		for (final Node node : nodes) {
			// Doesn't make sense to declare statements
			if (!(node instanceof Defn)) {
				break;
			}
			if ((node != null) || (!compiler.Compiler.devMode()))
				node.accept(this, Mode.DECLARE);
		}
		for (final Node node : nodes) {
			// Names are only resolved in the bodies of statements or full definitions
			if ((node != null) || (!compiler.Compiler.devMode()))
				node.accept(this, Mode.RESOLVE);
		}
		return null;
	}

	// ----- Definitions -----

	@Override
	public Object visit(TypDefn typDefn, NameResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			// TODO: Nekako razresi to da ne more biti self-referential
			typDefn.type.accept(this, arg);
		}

		if (arg == Mode.DECLARE) {
			declareName(typDefn);
		}
		return null;
	}

	@Override
	public Object visit(VarDefn varDefn, NameResolver.Mode arg) {
		if (arg == Mode.DECLARE) {
			declareName(varDefn);
		}
		if (arg == Mode.RESOLVE) {
			varDefn.type.accept(this, arg);
		}
		return null;
	}

	@Override
	public Object visit(DefFunDefn defFunDefn, NameResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			symbTable.newScope();
			defFunDefn.pars.accept(this, arg);
			defFunDefn.type.accept(this, arg);
			defFunDefn.stmts.accept(this, arg);
			symbTable.oldScope();
		}
		if (arg == Mode.DECLARE) {
			declareName(defFunDefn);
		}
		return null;
	}

	@Override
	public Object visit(ExtFunDefn extFunDefn, NameResolver.Mode arg) {
		if (arg == Mode.DECLARE) {
			declareName(extFunDefn);
		}
		if (arg == Mode.RESOLVE) {
			symbTable.newScope();
			extFunDefn.pars.accept(this, arg);
			extFunDefn.type.accept(this, arg);
			symbTable.oldScope();
		}
		return null;
	}

	@Override
	public Object visit(ParDefn parDefn, NameResolver.Mode arg) {
		if (arg == Mode.DECLARE) {
			declareName(parDefn);
		}
		if (arg == Mode.RESOLVE) {
			parDefn.type.accept(this, arg);
		}
		return null;
	}

	// ----- Statements -----

	@Override
	public Object visit(LetStmt letStmt, NameResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			this.symbTable.newScope();
			letStmt.defns.accept(this, arg);
			letStmt.stmts.accept(this, arg);
			this.symbTable.oldScope();
		}
		return null;
	}

	// ----- Types -----

	@Override
	public Object visit(NameType nameType, NameResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			resolveName(nameType, nameType.name);
		}
		return null;
	}

	// ----- Expressions -----

	@Override
	public Object visit(NameExpr nameExpr, NameResolver.Mode arg) {
		if (arg == Mode.RESOLVE) {
			resolveName(nameExpr, nameExpr.name);
		}
		return null;
	}

}
