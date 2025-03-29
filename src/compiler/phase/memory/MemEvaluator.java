package compiler.phase.memory;

import java.util.*;

import compiler.phase.abstr.*;
import compiler.phase.seman.*;

/**
 * Computing memory layout: stack frames and variable accesses.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class MemEvaluator implements AST.FullVisitor<Object, Object> {

	/**
	 * Contains the information about the function we are currently in
	 */
	private class FunctionInfo {
		public long argsSize = 0;
		public long locsSize = 0;
		// We start with 8 because we are storing the static link exactly
		// on FP
		public long parsSize = 8;
	}

	private long depth = 0;
	private FunctionInfo funInfo = new FunctionInfo();

	private static class RecInfo {
		public enum RecType {
			Union,
			Struct
		}

		public RecType type;
		// Gives us the current size for the components up until now in the
		// current record definition
		public long recDefOffset = 0;

		public RecInfo(RecType recType) {
			this.type = recType;
		}
	}

	private RecInfo recInfo = new RecInfo(null);

	private SizeEvaluator sizeEvaluator = new SizeEvaluator();

	private final long ptrSize;

	public MemEvaluator() {
		ptrSize = (new TYP.PtrType(TYP.VoidType.type)).accept(sizeEvaluator, null);
	}

	/**
	 * Rounds up to nearest multiple of 8
	 */
	private long align(long size) {
		return (size + 7) & ~7;
	}

	/**
	 * Returns the size of the type aligned to 8 bytes
	 */
	private long alignedSize(TYP.Type type) {
		return align(type.accept(sizeEvaluator, null));
	}

	/**
	 * Gets size of type
	 */
	private long getSize(TYP.Type type) {
		return type.accept(sizeEvaluator, null);
	}

	/**
	 * Calculates the size of the call
	 */
	private long getCallSize(TYP.FunType funType) {
		long size = 0;
		for (TYP.Type parType : funType.parTypes) {
			size += alignedSize(parType);
		}
		// We also need the static link
		size += 8;
		size += alignedSize(funType.resType);
		return size;
	}

	@Override
	public Object visit(AST.VarDefn varDefn, Object arg) {
		// Here we have to track whether we are a global definition or a local one
		varDefn.type.accept(this, arg);
		TYP.Type type = SemAn.ofType.get(varDefn);
		long size = getSize(type);

		// We are in the global namespace
		if (depth == 0) {
			// TODO: What to put for an initial value?
			// for string you should put "", for others leave at null!
			Memory.accesses.put(varDefn, new MEM.AbsAccess(size, new MEM.Label(varDefn.name)));
		} else {
			// We are in a let statement in some function, so this will be a local variable
			// for the function
			funInfo.locsSize += align(size);
			MEM.Access access = new MEM.RelAccess(size, -funInfo.locsSize, depth);
			Memory.accesses.put(varDefn, access);
		}

		return null;
	}

	@Override
	public Object visit(AST.ParDefn parDefn, Object arg) {
		parDefn.type.accept(this, arg);

		TYP.Type type = SemAn.ofType.get(parDefn);
		long size = getSize(type);
		MEM.Access access = new MEM.RelAccess(size, funInfo.parsSize, depth);
		Memory.accesses.put(parDefn, access);

		funInfo.parsSize += align(size);

		return null;
	}

	@Override
	public Object visit(AST.StrType strType, Object arg) {
		RecInfo oldInfo = recInfo;
		recInfo = new RecInfo(RecInfo.RecType.Struct);
		strType.comps.accept(this, arg);
		recInfo = oldInfo;
		return null;
	}

	@Override
	public Object visit(AST.UniType uniType, Object arg) {
		RecInfo oldInfo = recInfo;
		recInfo = new RecInfo(RecInfo.RecType.Union);
		uniType.comps.accept(this, arg);
		recInfo = oldInfo;
		return null;
	}

	@Override
	public Object visit(AST.CompDefn compDefn, Object arg) {
		compDefn.type.accept(this, arg);

		TYP.Type type = SemAn.ofType.get(compDefn);
		long size = getSize(type);
		MEM.Access access = null;

		switch (recInfo.type) {
			case Struct:
				access = new MEM.RelAccess(size, recInfo.recDefOffset, -1);
				recInfo.recDefOffset += align(size);
				break;
			case Union:
				// Union components overlap so they are always at offset 0 and we only need the
				// biggest size
				access = new MEM.RelAccess(size, 0, -1);
				recInfo.recDefOffset = Math.max(recInfo.recDefOffset, align(size));
				break;
		}

		// TODO: Ali so offset za record definitions negativni ali pozitivni?
		Memory.accesses.put(compDefn, access);

		return null;
	}

	@Override
	public Object visit(AST.CallExpr callExpr, Object arg) {
		callExpr.funExpr.accept(this, arg);
		callExpr.argExprs.accept(this, arg);

		TYP.Type funType = SemAn.ofType.get(callExpr.funExpr);
		long callSize = getCallSize((TYP.FunType) funType.actualType());
		// We only need as much space as the biggest function call
		funInfo.argsSize = Math.max(funInfo.argsSize, callSize);

		return null;
	}

	@Override
	public Object visit(AST.DefFunDefn funDefn, Object arg) {
		MEM.Label label = null;

		if (depth == 0) {
			label = new MEM.Label(funDefn.name);
		} else {
			label = new MEM.Label();
		}

		depth += 1;

		FunctionInfo oldInfo = funInfo;
		funInfo = new FunctionInfo();

		funDefn.pars.accept(this, arg);
		funDefn.type.accept(this, arg);
		funDefn.stmts.accept(this, arg);

		// We also save two pointers: the old FP pointer and the return address
		long totalSize = funInfo.argsSize + funInfo.locsSize + 2 * ptrSize;
		MEM.Frame frame = new MEM.Frame(label, depth - 1, funInfo.locsSize, funInfo.argsSize, totalSize);
		Memory.frames.put(funDefn, frame);

		depth -= 1;
		funInfo = oldInfo;

		return null;
	}
}
