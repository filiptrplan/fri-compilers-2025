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
	Vector<IMC.Stmt> currentStmts = new Vector<>();

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
	public IMC.Instr visit(IMC.IMC.CALL call, IMC.Stmt arg) {
		IMC.CALL origCallInstr = call;

		Vector<IMC.Expr> newargsVector = new Vector<>();

		for (IMC.Expr expr : call.args) {
			IMC.Expr newArg = (IMC.Expr) expr.accept(this, null);
			IMC.TEMP argTemp = new IMC.TEMP(new MEM.Temp());
			// TODO: If i always wrap with MEM8 is there anything wrong with that?
			// temp variables should always be either registers or aligned on 8 bytes.
			IMC.MOVE moveArg = new IMC.MOVE(new IMC.MEM8(argTemp), newArg);
			currentStmts.add(moveArg);
			newargsVector.add(argTemp);
		}

		IMC.CALL newCallInstr = new IMC.CALL(origCallInstr.addr, origCallInstr.offs, newargsVector);

		return newCallInstr;
	}

	public IMC.Expr visit(IMC.BINOP binOp, IMC.Stmt visArg) {
		IMC.Expr ex1 = (IMC.Expr) binOp.fstExpr.accept(this, visArg);
		IMC.Expr ex2 = (IMC.Expr) binOp.sndExpr.accept(this, visArg);
		return new IMC.BINOP(binOp.oper, ex1, ex2);
	}

	public IMC.Expr visit(IMC.CJUMP cjump, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.CONST constant, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.ESTMT eStmt, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.JUMP jump, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.LABEL label, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.MEM1 mem, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.MEM8 mem, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.MOVE move, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.NAME name, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.SEXPR sExpr, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.STMTS stmts, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.TEMP temp, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}

	public IMC.Expr visit(IMC.UNOP unOp, IMC.Stmt visArg) {
		throw new Report.InternalError();
	}
}
