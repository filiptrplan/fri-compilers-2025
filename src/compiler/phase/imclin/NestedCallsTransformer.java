package compiler.phase.imclin;

import java.util.HashMap;
import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.abstr.AST;
import compiler.phase.imcgen.IMC;
import compiler.phase.imcgen.ImcGen;
import compiler.phase.imcgen.ImcGenerator;
import compiler.phase.memory.MEM;

/**
 * Mutates expression in-place to replace any nested calls with MOVEs
 * Should be called once per function and it linearizes only the expressions
 * directly in the function, not in any nested function definitions.
 */
public class NestedCallsTransformer implements IMC.Visitor<IMC.Instr, IMC.Stmt> {
	/** The current statements that need to be added */
	Vector<IMC.Stmt> currentStmts = null;

	/**
	 * @param _currentStmts This is the vector to which all the statements generated
	 *                      will be added
	 */
	public NestedCallsTransformer(Vector<IMC.Stmt> _currentStmts) {
		this.currentStmts = _currentStmts;
	}

	/** Linearizes any IMC.STMTS one level deep */
	private Vector<IMC.Stmt> linearizeStmts(Vector<IMC.Stmt> origStmts) {
		Vector<IMC.Stmt> newStmts = new Vector<>();
		for (IMC.Stmt stmt : origStmts) {
			if (stmt instanceof IMC.STMTS) {
				newStmts.addAll(((IMC.STMTS) stmt).stmts);
			} else {
				newStmts.add(stmt);
			}
		}
		return newStmts;
	}

	@Override
	public IMC.Instr visit(IMC.CALL call, IMC.Stmt arg) {
		IMC.CALL origCallInstr = call;

		Vector<IMC.Expr> newargsVector = new Vector<>();

		for (IMC.Expr expr : call.args) {
			IMC.Expr newArg = (IMC.Expr) expr.accept(this, null);
			IMC.TEMP argTemp = new IMC.TEMP(new MEM.Temp());
			IMC.MOVE moveArg = new IMC.MOVE(argTemp, newArg);
			currentStmts.add(moveArg);
			newargsVector.add(argTemp);
		}

		IMC.CALL newCallInstr = new IMC.CALL(origCallInstr.addr, origCallInstr.offs, newargsVector);

		return newCallInstr;
	}

	@Override
	public IMC.Instr visit(IMC.BINOP binOp, IMC.Stmt visArg) {
		IMC.Expr ex1 = (IMC.Expr) binOp.fstExpr.accept(this, visArg);
		IMC.Expr ex2 = (IMC.Expr) binOp.sndExpr.accept(this, visArg);
		return new IMC.BINOP(binOp.oper, ex1, ex2);
	}

	@Override
	public IMC.Instr visit(IMC.CJUMP cjump, IMC.Stmt visArg) {
		IMC.Expr newCond = (IMC.Expr) cjump.cond.accept(this, visArg);
		IMC.Expr newPosAddr = (IMC.Expr) cjump.posAddr.accept(this, visArg);
		IMC.Expr newNegAddr = (IMC.Expr) cjump.negAddr.accept(this, visArg);
		return new IMC.CJUMP(newCond, newPosAddr, newNegAddr);
	}

	@Override
	public IMC.Instr visit(IMC.CONST constant, IMC.Stmt visArg) {
		return constant;
	}

	@Override
	public IMC.Instr visit(IMC.ESTMT eStmt, IMC.Stmt visArg) {
		IMC.Expr newExpr = (IMC.Expr) eStmt.expr.accept(this, visArg);
		return new IMC.ESTMT(newExpr);
	}

	@Override
	public IMC.Instr visit(IMC.JUMP jump, IMC.Stmt visArg) {
		IMC.Expr newAddr = (IMC.Expr) jump.addr.accept(this, visArg);
		return new IMC.JUMP(newAddr);
	}

	@Override
	public IMC.Instr visit(IMC.LABEL label, IMC.Stmt visArg) {
		return label;
	}

	@Override
	public IMC.Instr visit(IMC.MEM1 mem, IMC.Stmt visArg) {
		IMC.Expr newAddr = (IMC.Expr) mem.addr.accept(this, visArg);
		return new IMC.MEM1(newAddr);
	}

	@Override
	public IMC.Instr visit(IMC.MEM8 mem, IMC.Stmt visArg) {
		IMC.Expr newAddr = (IMC.Expr) mem.addr.accept(this, visArg);
		return new IMC.MEM8(newAddr);
	}

	@Override
	public IMC.Instr visit(IMC.MOVE move, IMC.Stmt visArg) {
		IMC.Expr newSrc = (IMC.Expr) move.src.accept(this, visArg);
		IMC.Expr newDst = (IMC.Expr) move.dst.accept(this, visArg);
		return new IMC.MOVE(newDst, newSrc);
	}

	@Override
	public IMC.Instr visit(IMC.NAME name, IMC.Stmt visArg) {
		return name;
	}

	@Override
	public IMC.Instr visit(IMC.SEXPR sExpr, IMC.Stmt visArg) {
		IMC.Stmt newStmt = (IMC.Stmt) sExpr.stmt.accept(this, visArg);
		IMC.Expr newExpr = (IMC.Expr) sExpr.expr.accept(this, visArg);
		return new IMC.SEXPR(newStmt, newExpr);
	}

	@Override
	public IMC.Instr visit(IMC.STMTS stmts, IMC.Stmt visArg) {
		Vector<IMC.Stmt> newStmts = new Vector<>();
		for (IMC.Stmt stmt : stmts.stmts) {
			IMC.Stmt transformedStmt = (IMC.Stmt) stmt.accept(this, visArg);
			newStmts.addAll(currentStmts);
			currentStmts.clear();
			newStmts.add(transformedStmt);
		}
		return new IMC.STMTS(newStmts, stmts.startLabel);
	}

	@Override
	public IMC.Instr visit(IMC.TEMP temp, IMC.Stmt visArg) {
		return temp;
	}

	@Override
	public IMC.Instr visit(IMC.UNOP unOp, IMC.Stmt visArg) {
		IMC.Expr newSubExpr = (IMC.Expr) unOp.subExpr.accept(this, visArg);
		return new IMC.UNOP(unOp.oper, newSubExpr);
	}
}
