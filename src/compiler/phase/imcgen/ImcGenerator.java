package compiler.phase.imcgen;

import compiler.common.report.Report;
import compiler.phase.abstr.*;
import compiler.phase.imcgen.IMC.UNOP.Oper;
import compiler.phase.memory.MEM;
import compiler.phase.memory.Memory;
import compiler.phase.memory.SizeEvaluator;
import compiler.phase.seman.SemAn;
import compiler.phase.seman.TYP;
import compiler.phase.seman.TypeChecker;

/**
 * Intermediate code generator.
 * 
 * @author bostjan.slivnik@fri.uni-lj.si
 */
public class ImcGenerator implements AST.FullVisitor<IMC.Instr, Object> {

	private SizeEvaluator sizeEvaluator = new SizeEvaluator();

	/** The call frame of the current function we are in */
	private MEM.Frame currentCallFrame = null;

	/** Checks if the node is of size 1 or not */
	private boolean isMem1(AST.Node node) {
		TYP.Type type = SemAn.ofType.get(node);
		return TypeChecker.equivalent(type, TYP.BoolType.type) || TypeChecker.equivalent(type, TYP.CharType.type);
	}

	// ----- Trees -----

	@Override
	public IMC.Instr visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		for (final AST.Node node : nodes)
			if ((node != null) || (!compiler.Compiler.devMode()))
				node.accept(this, arg);
		return null;
	}

	// ----- Definitions -----

	@Override
	public IMC.Instr visit(AST.TypDefn typDefn, Object arg) {
		if ((typDefn.type != null) || (!compiler.Compiler.devMode()))
			typDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.VarDefn varDefn, Object arg) {
		if ((varDefn.type != null) || (!compiler.Compiler.devMode()))
			varDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.DefFunDefn defFunDefn, Object arg) {
		defFunDefn.pars.accept(this, arg);
		defFunDefn.type.accept(this, arg);
		MEM.Frame oldFrame = currentCallFrame;
		currentCallFrame = Memory.frames.get(defFunDefn);
		defFunDefn.stmts.accept(this, arg);
		currentCallFrame = oldFrame;
		return null;
	}

	@Override
	public IMC.Instr visit(AST.ExtFunDefn extFunDefn, Object arg) {
		if ((extFunDefn.pars != null) || (!compiler.Compiler.devMode()))
			extFunDefn.pars.accept(this, arg);
		if ((extFunDefn.type != null) || (!compiler.Compiler.devMode()))
			extFunDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.ParDefn parDefn, Object arg) {
		if ((parDefn.type != null) || (!compiler.Compiler.devMode()))
			parDefn.type.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.CompDefn compDefn, Object arg) {
		if ((compDefn.type != null) || (!compiler.Compiler.devMode()))
			compDefn.type.accept(this, arg);
		return null;
	}

	// ----- Statements -----

	@Override
	public IMC.Instr visit(AST.LetStmt letStmt, Object arg) {
		if ((letStmt.defns != null) || (!compiler.Compiler.devMode()))
			letStmt.defns.accept(this, arg);
		if ((letStmt.stmts != null) || (!compiler.Compiler.devMode()))
			letStmt.stmts.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.AssignStmt assignStmt, Object arg) {
		if ((assignStmt.dstExpr != null) || (!compiler.Compiler.devMode()))
			assignStmt.dstExpr.accept(this, arg);
		if ((assignStmt.srcExpr != null) || (!compiler.Compiler.devMode()))
			assignStmt.srcExpr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.ExprStmt callStmt, Object arg) {
		if ((callStmt.expr != null) || (!compiler.Compiler.devMode()))
			callStmt.expr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.IfThenStmt ifThenStmt, Object arg) {
		ifThenStmt.condExpr.accept(this, arg);
		ifThenStmt.thenStmt.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.IfThenElseStmt ifThenElseStmt, Object arg) {
		ifThenElseStmt.condExpr.accept(this, arg);
		ifThenElseStmt.thenStmt.accept(this, arg);
		ifThenElseStmt.elseStmt.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.ReturnStmt returnStmt, Object arg) {
		returnStmt.retExpr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.WhileStmt whileStmt, Object arg) {
		whileStmt.condExpr.accept(this, arg);
		whileStmt.stmts.accept(this, arg);
		return null;
	}

	// ----- Types -----

	@Override
	public IMC.Instr visit(AST.AtomType atomType, Object arg) {
		return null;
	}

	@Override
	public IMC.Instr visit(AST.ArrType arrType, Object arg) {
		arrType.elemType.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.PtrType ptrType, Object arg) {
		ptrType.baseType.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.StrType strType, Object arg) {
		strType.comps.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.UniType uniType, Object arg) {
		uniType.comps.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.FunType funType, Object arg) {
		funType.parTypes.accept(this, arg);
		funType.resType.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.NameType nameType, Object arg) {
		return null;
	}

	// ----- Expressions -----

	@Override
	public IMC.Instr visit(AST.ArrExpr arrExpr, Object arg) {
		IMC.Expr arrExprInstr = (IMC.Expr) arrExpr.arrExpr.accept(this, arg);
		IMC.Expr idxInstr = (IMC.Expr) arrExpr.idx.accept(this, arg);

		// The array access expression should be of the same type as the element type
		TYP.Type elementType = SemAn.ofType.get(arrExpr);
		long elSize = elementType.accept(sizeEvaluator, null);

		// The idx is always an INT according to the type rules
		// The arrExpr must be an address. That could be a dereferenced pointer or
		// something
		// like that or a NameExpr. So we must watch out for MEM instructions.
		// The MEM instruction should always be 8 bytes because we can't have an
		// array pointer that is dereferenced to a 1 byte data structure like char or
		// bool

		// TODO: If they are MEM calls, does it hold that they are always some
		// variables / places to assign to?

		IMC.Expr addrInstr = null;

		if (arrExprInstr instanceof IMC.MEM8) {
			IMC.Expr arrExprAddr = ((IMC.MEM8) arrExprInstr).addr;
			addrInstr = new IMC.BINOP(IMC.BINOP.Oper.ADD, arrExprAddr,
					new IMC.BINOP(IMC.BINOP.Oper.MUL, idxInstr, new IMC.CONST(elSize)));
		} else if (arrExprInstr instanceof IMC.MEM1) {
			// According to the type rules, we shouldn't reach this
			throw new Report.InternalError();
		} else {
			addrInstr = new IMC.BINOP(IMC.BINOP.Oper.ADD, arrExprInstr,
					new IMC.BINOP(IMC.BINOP.Oper.MUL, idxInstr, new IMC.CONST(elSize)));
		}
		IMC.Expr instr = null;
		if (isMem1(arrExpr)) {
			instr = new IMC.MEM1(addrInstr);
		} else {
			instr = new IMC.MEM8(addrInstr);
		}
		ImcGen.expr.put(arrExpr, instr);
		return instr;
	}

	/** Get the long value of a character constant (still surrounded by quotes) */
	private long getCharValue(String str) {
		char c = str.charAt(1);
		char result = '\00';
		if (c == '\\') {
			char next = str.charAt(2);
			switch (next) {
				case '\\':
					result = '\\';
					break;
				case '"':
					result = '"';
					break;
				case '0':
					String hex = str.substring(4, 6);
					int code = Integer.parseInt(hex, 16);
					result = (char) code;
					break;
			}
		} else {
			result = c;
		}
		return (long) result;
	}

	@Override
	public IMC.Instr visit(AST.AtomExpr atomExpr, Object arg) {
		IMC.Expr instr = null;
		switch (atomExpr.type) {
			case INT:
				instr = new IMC.CONST(Long.valueOf(atomExpr.value));
				break;
			case STR:
				// TODO: String const expression
				break;
			case CHAR:
				instr = new IMC.CONST(getCharValue(atomExpr.value));
				break;
			case BOOL:
				if (atomExpr.value.equals("true")) {
					instr = new IMC.CONST(1);
				} else {
					instr = new IMC.CONST(0);
				}
				break;
			case PTR:
				// Pointer constants can only be a null pointer
				// All others should be considered as prefix expressions
				if (!atomExpr.value.equals("0")) {
					throw new Report.InternalError();
				}
				instr = new IMC.CONST(0);
				break;
		}
		ImcGen.expr.put(atomExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.BinExpr binExpr, Object arg) {
		binExpr.fstExpr.accept(this, arg);
		binExpr.sndExpr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.CallExpr callExpr, Object arg) {
		callExpr.funExpr.accept(this, arg);
		callExpr.argExprs.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.CastExpr castExpr, Object arg) {
		castExpr.type.accept(this, arg);
		castExpr.expr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.CompExpr compExpr, Object arg) {
		compExpr.recExpr.accept(this, arg);
		return null;
	}

	@Override
	public IMC.Instr visit(AST.NameExpr nameExpr, Object arg) {
		AST.Node defn = SemAn.defAt.get(nameExpr);
		IMC.Expr addressInstr = null;

		// We handle variables and functions differently
		if (defn instanceof AST.FunDefn) {
			MEM.Frame callFrame = Memory.frames.get(defn);
			MEM.Label label = callFrame.label;
			addressInstr = new IMC.NAME(label);
		} else {
			MEM.Access access = Memory.accesses.get(defn);
			if (access instanceof MEM.AbsAccess) {
				addressInstr = new IMC.NAME(((MEM.AbsAccess) access).label);
			} else { // Else it is a RelAccess
				MEM.RelAccess relAccess = (MEM.RelAccess) access;
				IMC.Expr fp = new IMC.TEMP(currentCallFrame.FP);
				// We calculate how many times we must go up a static link
				long diffDepth = currentCallFrame.depth - relAccess.depth;
				for (int i = 0; i < diffDepth; i++) {
					fp = new IMC.MEM8(fp);
				}
				addressInstr = new IMC.BINOP(IMC.BINOP.Oper.ADD, fp, new IMC.CONST(relAccess.offset));
			}
		}

		IMC.Expr memAccessInstr;
		if (isMem1(nameExpr)) {
			memAccessInstr = new IMC.MEM1(addressInstr);
		} else {
			memAccessInstr = new IMC.MEM8(addressInstr);
		}
		ImcGen.expr.put(nameExpr, memAccessInstr);
		return memAccessInstr;
	}

	@Override
	public IMC.Instr visit(AST.PfxExpr pfxExpr, Object arg) {
		IMC.Expr subExpr = (IMC.Expr) pfxExpr.subExpr.accept(this, arg);
		IMC.Expr instr = null;
		switch (pfxExpr.oper) {
			case ADD:
				// + <something> just equals to <something>
				instr = subExpr;
				break;
			case SUB:
				instr = new IMC.UNOP(IMC.UNOP.Oper.NEG, subExpr);
				break;
			case NOT:
				instr = new IMC.UNOP(IMC.UNOP.Oper.NOT, subExpr);
				break;
			case PTR:
				// TODO: Get address of expression
				// What it could be: IMC.NAME, IMC.TEMP
				// Maybe it could be IMC.MEM because of the CompExpr?
				// AST Nodes that can be addresses: CompExpr, NameExpr, CastExpr
				if (subExpr instanceof IMC.MEM8) {
					// We unwrap the address
					instr = ((IMC.MEM8) subExpr).addr;
				} else if (subExpr instanceof IMC.MEM1) {
					instr = ((IMC.MEM1) subExpr).addr;
				} else {
					// This should not happen
					throw new Report.InternalError();
				}
				break;
		}
		ImcGen.expr.put(pfxExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.SfxExpr sfxExpr, Object arg) {
		IMC.Expr subExpr = (IMC.Expr) sfxExpr.subExpr.accept(this, arg);
		IMC.Expr instr = null;
		switch (sfxExpr.oper) {
			case PTR:
				// We already make statically sure that this is an address so we just MEM it
				// The only difference is if we are working with an 8byte or 1byte value
				if (isMem1(sfxExpr)) {
					instr = new IMC.MEM1(subExpr);
				} else {
					instr = new IMC.MEM8(subExpr);
				}
				break;
		}
		ImcGen.expr.put(sfxExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.SizeExpr sizeExpr, Object arg) {
		sizeExpr.type.accept(this, arg);
		TYP.Type sizeType = SemAn.isType.get(sizeExpr.type);
		long size = sizeType.accept(sizeEvaluator, null);
		IMC.Expr instr = new IMC.CONST(size);
		ImcGen.expr.put(sizeExpr, instr);
		return instr;
	}
}
