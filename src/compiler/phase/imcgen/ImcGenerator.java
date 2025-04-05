package compiler.phase.imcgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.abstr.*;
import compiler.phase.abstr.AST.Stmt;
import compiler.phase.imcgen.IMC.UNOP.Oper;
import compiler.phase.memory.MEM;
import compiler.phase.memory.MemEvaluator;
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

	/** The nodes of the current function we are in */
	private AST.Node currentFunctionNode = null;

	/** Checks if the node is of size 1 or not */
	private boolean isMem1(AST.Node node) {
		TYP.Type type = SemAn.ofType.get(node);
		return TypeChecker.equivalent(type, TYP.BoolType.type) || TypeChecker.equivalent(type, TYP.CharType.type);
	}

	/** Wraps the address expression in MEM1 / MEM8 depending on the node type */
	private IMC.Expr wrapMem(IMC.Expr addr, AST.Node node) {
		if (isMem1(node)) {
			return new IMC.MEM1(addr);
		} else {
			return new IMC.MEM8(addr);
		}
	}

	/** Get the address this expression represents */
	private IMC.Expr getAddress(IMC.Expr expr) {
		if (expr instanceof IMC.MEM8) {
			IMC.Expr exprAddr = ((IMC.MEM8) expr).addr;
			return exprAddr;
		} else if (expr instanceof IMC.MEM1) {
			// According to the type rules, we shouldn't reach this
			throw new Report.InternalError();
		} else {
			return expr;
		}
	}

	// ----- Trees -----

	@Override
	public IMC.Instr visit(AST.Nodes<? extends AST.Node> nodes, Object arg) {
		if (nodes.size() == 0) {
			return null;
		}

		IMC.Instr instr = null;
		if (nodes.get(0) instanceof AST.Stmt) {
			Vector<IMC.Stmt> stmts = new Vector<>();
			MEM.Label startLabel = new MEM.Label();
			MEM.Label endLabel = new MEM.Label();
			stmts.add(new IMC.LABEL(startLabel));
			for (final AST.Node node : nodes)
				stmts.add((IMC.Stmt) node.accept(this, arg));
			stmts.add(new IMC.LABEL(endLabel));
			instr = new IMC.STMTS(stmts, startLabel, endLabel);
		} else {
			for (final AST.Node node : nodes)
				node.accept(this, arg);
		}
		return instr;
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
		AST.Node oldNode = currentFunctionNode;

		currentCallFrame = Memory.frames.get(defFunDefn);
		currentFunctionNode = defFunDefn;

		MEM.Label entryLabel = new MEM.Label();
		MEM.Label exitLabel = new MEM.Label();
		ImcGen.entryLabel.put(defFunDefn, entryLabel);
		ImcGen.exitLabel.put(defFunDefn, exitLabel);
		defFunDefn.stmts.accept(this, arg);

		currentCallFrame = oldFrame;
		currentFunctionNode = oldNode;
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
		IMC.Expr dstExpr = (IMC.Expr) assignStmt.dstExpr.accept(this, arg);
		IMC.Expr dstAddr = getAddress(dstExpr);
		IMC.Expr srcExpr = (IMC.Expr) assignStmt.srcExpr.accept(this, arg);
		IMC.Stmt stmt = new IMC.MOVE(dstAddr, srcExpr);
		ImcGen.stmt.put(assignStmt, stmt);
		return stmt;
	}

	@Override
	public IMC.Instr visit(AST.ExprStmt callStmt, Object arg) {
		IMC.Expr expr = (IMC.Expr) callStmt.expr.accept(this, arg);
		IMC.Stmt stmt = new IMC.ESTMT(expr);
		ImcGen.stmt.put(callStmt, stmt);
		return stmt;
	}

	@Override
	public IMC.Instr visit(AST.IfThenStmt ifThenStmt, Object arg) {
		IMC.Expr condExpr = (IMC.Expr) ifThenStmt.condExpr.accept(this, arg);
		IMC.STMTS thenStmt = (IMC.STMTS) ifThenStmt.thenStmt.accept(this, arg);
		MEM.Label entryLabel = thenStmt.startLabel;
		MEM.Label exitLabel = thenStmt.endLabel;
		IMC.Stmt stmt = new IMC.CJUMP(condExpr, new IMC.NAME(entryLabel),
				new IMC.NAME(exitLabel));
		ImcGen.stmt.put(ifThenStmt, stmt);
		return stmt;
	}

	@Override
	public IMC.Instr visit(AST.IfThenElseStmt ifThenElseStmt, Object arg) {
		IMC.Expr condExpr = (IMC.Expr) ifThenElseStmt.condExpr.accept(this, arg);
		IMC.STMTS thenStmt = (IMC.STMTS) ifThenElseStmt.thenStmt.accept(this, arg);
		IMC.STMTS elseStmt = (IMC.STMTS) ifThenElseStmt.elseStmt.accept(this, arg);
		MEM.Label label1 = thenStmt.startLabel;
		MEM.Label label2 = elseStmt.startLabel;
		IMC.Stmt stmt = new IMC.CJUMP(condExpr, new IMC.NAME(label1), new IMC.NAME(label2));
		ImcGen.stmt.put(ifThenElseStmt, stmt);
		return stmt;
	}

	@Override
	public IMC.Instr visit(AST.ReturnStmt returnStmt, Object arg) {
		IMC.Expr expr = (IMC.Expr) returnStmt.retExpr.accept(this, arg);
		Vector<IMC.Stmt> stmts = new Vector<>();
		// Move the result to the RV register
		stmts.add(new IMC.MOVE(new IMC.TEMP(currentCallFrame.RV), expr));
		// Jump to the function epilogue
		stmts.add(new IMC.JUMP(new IMC.NAME(ImcGen.exitLabel.get(currentFunctionNode))));
		IMC.Stmt instr = new IMC.STMTS(stmts, new MEM.Label(), new MEM.Label());
		ImcGen.stmt.put(returnStmt, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.WhileStmt whileStmt, Object arg) {
		IMC.Expr expr = (IMC.Expr) whileStmt.condExpr.accept(this, arg);
		whileStmt.stmts.accept(this, arg);
		// This label denotes the top of the while loop
		MEM.Label whileLabel = new MEM.Label();
		MEM.Label whileBodyLabel = ImcGen.entryLabel.get(whileStmt.stmts);
		MEM.Label whileExitLabel = new MEM.Label();

		Vector<IMC.Stmt> stmts = new Vector<>();
		// We first label our while loop
		stmts.add(new IMC.LABEL(whileLabel));
		// Then we check the condition
		stmts.add(new IMC.CJUMP(expr, new IMC.NAME(whileBodyLabel), new IMC.NAME(whileExitLabel)));
		// At the end we jump to the condition again
		stmts.add(new IMC.JUMP(new IMC.NAME(whileLabel)));
		// Then we also add the label for exiting the function
		stmts.add(new IMC.LABEL(whileExitLabel));

		IMC.Stmt instr = new IMC.STMTS(stmts, whileLabel, whileExitLabel);
		ImcGen.stmt.put(whileStmt, instr);
		return instr;
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

		IMC.Expr arrExprAddr = getAddress(arrExprInstr);

		IMC.Expr addrInstr = new IMC.BINOP(IMC.BINOP.Oper.ADD, arrExprAddr,
				new IMC.BINOP(IMC.BINOP.Oper.MUL, idxInstr, new IMC.CONST(elSize)));

		IMC.Expr instr = wrapMem(addrInstr, arrExpr);
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
				// We just create an anonymous label for the string
				MEM.AbsAccess access = Memory.strings.get(atomExpr);
				instr = new IMC.NAME(access.label);
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
		IMC.Expr fstExpr = (IMC.Expr) binExpr.fstExpr.accept(this, arg);
		IMC.Expr sndExpr = (IMC.Expr) binExpr.sndExpr.accept(this, arg);

		IMC.BINOP.Oper oper = null;

		switch (binExpr.oper) {
			case OR:
				oper = IMC.BINOP.Oper.OR;
				break;
			case AND:
				oper = IMC.BINOP.Oper.AND;
				break;
			case EQU:
				oper = IMC.BINOP.Oper.EQU;
				break;
			case NEQ:
				oper = IMC.BINOP.Oper.NEQ;
				break;
			case LTH:
				oper = IMC.BINOP.Oper.LTH;
				break;
			case GTH:
				oper = IMC.BINOP.Oper.GTH;
				break;
			case LEQ:
				oper = IMC.BINOP.Oper.LEQ;
				break;
			case GEQ:
				oper = IMC.BINOP.Oper.GEQ;
				break;
			case MUL:
				oper = IMC.BINOP.Oper.MUL;
				break;
			case DIV:
				oper = IMC.BINOP.Oper.DIV;
				break;
			case MOD:
				oper = IMC.BINOP.Oper.MOD;
				break;
			case ADD:
				oper = IMC.BINOP.Oper.ADD;
				break;
			case SUB:
				oper = IMC.BINOP.Oper.SUB;
				break;
		}

		IMC.Expr instr = new IMC.BINOP(oper, fstExpr, sndExpr);
		ImcGen.expr.put(binExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.CallExpr callExpr, Object arg) {
		// We know that the funExpr will return the address of the function
		IMC.Expr funExprAddr = (IMC.Expr) callExpr.funExpr.accept(this, arg);
		Vector<IMC.Expr> args = new Vector<>();
		Vector<Long> offs = new Vector<>();

		// I will consider the offset to be from the preallocated SP up
		long offset = 0;
		// First we add the static link
		MEM.Temp containingFP = currentCallFrame.FP;
		args.add(new IMC.TEMP(containingFP));
		offs.add(offset);
		offset += 8;
		for (int i = 0; i < callExpr.argExprs.size(); i++) {
			AST.Node argExpr = callExpr.argExprs.get(i);
			IMC.Expr argExprInstr = (IMC.Expr) argExpr.accept(this, arg);
			args.add(argExprInstr);
			// TODO: are these offsets correct?
			offset += MemEvaluator.alignedSize(SemAn.ofType.get(argExpr).actualType());
			offs.add(offset);
		}
		IMC.Expr instr = new IMC.CALL(funExprAddr, offs, args);
		ImcGen.expr.put(callExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.CastExpr castExpr, Object arg) {
		castExpr.type.accept(this, arg);
		IMC.Expr subExpr = (IMC.Expr) castExpr.expr.accept(this, arg);
		TYP.Type castType = SemAn.isType.get(castExpr.type).actualType();

		IMC.Expr instr;
		if (castType instanceof TYP.BoolType) {
			instr = new IMC.BINOP(IMC.BINOP.Oper.MOD, subExpr, new IMC.CONST(2));
		} else if (castType instanceof TYP.CharType) {
			instr = new IMC.BINOP(IMC.BINOP.Oper.MOD, subExpr, new IMC.CONST(256));
		} else {
			instr = subExpr;
		}
		ImcGen.expr.put(castExpr, instr);
		return instr;
	}

	@Override
	public IMC.Instr visit(AST.CompExpr compExpr, Object arg) {
		IMC.Expr recExprInstr = (IMC.Expr) compExpr.recExpr.accept(this, arg);
		IMC.Expr recExprAddr = getAddress(recExprInstr);

		TYP.RecType recordType = (TYP.RecType) SemAn.ofType.get(compExpr.recExpr).actualType();
		// Otherwise it is a struct
		boolean isUnion = recordType instanceof TYP.UniType;

		IMC.Expr compExprAddr = null;

		if (isUnion) {
			// Unions always have an offset of 0
			compExprAddr = recExprAddr;
		} else {
			// We must find the offset of the component in the struct
			String compName = compExpr.name;
			long offset = 0;
			for (int i = 0; i < recordType.compTypeNames.size(); i++) {
				String curName = recordType.compTypeNames.get(i);
				if (curName.equals(compName)) {
					break;
				}
				TYP.Type curType = recordType.compTypes.get(i);
				offset += MemEvaluator.alignedSize(curType);
			}
			compExprAddr = new IMC.BINOP(IMC.BINOP.Oper.ADD, recExprAddr, new IMC.CONST(offset));
		}

		IMC.Expr instr = wrapMem(compExprAddr, compExpr);
		ImcGen.expr.put(compExpr, instr);

		return instr;
	}

	@Override
	public IMC.Instr visit(AST.NameExpr nameExpr, Object arg) {
		AST.Node defn = SemAn.defAt.get(nameExpr);
		IMC.Expr addressInstr = null;

		// We handle variables and functions differently
		if (defn instanceof AST.FunDefn) {
			MEM.Frame callFrame = Memory.frames.get(defn);
			MEM.Label label = null;
			if (defn instanceof AST.ExtFunDefn) {
				label = callFrame.label;
			} else {
				label = ImcGen.entryLabel.get(defn);
			}
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

		IMC.Expr memAccessInstr = wrapMem(addressInstr, nameExpr);
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
				instr = wrapMem(subExpr, sfxExpr);
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
