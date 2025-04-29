package compiler.phase.asmgen;

import java.util.Vector;

import compiler.phase.imcgen.IMC;
import compiler.phase.memory.MEM;

/** Generates assembly code from intermediate code */
public class AsmGenerator implements IMC.Visitor<String, Object> {
	/**
	 * A comment on what the return value of the visitor means.
	 * The return value of an expression is the register that contains the result of
	 * the expression. So it will return a String with the format T{number}
	 * If the return value is a constant, then the constant is the return value (aka
	 * a string with the format C{number}).
	 * The return value of a statement is null.
	 */

	private final Vector<ASM.Instr> instructions;

	public AsmGenerator(Vector<ASM.Instr> instructions) {
		this.instructions = instructions;
	}

	@Override
	public Object visit(IMC.STMTS stmts, Object visArg) {
		for (IMC.Stmt stmt : stmts.stmts)
			stmt.accept(this, visArg);
		return null;
	}

	@Override
	public Object visit(IMC.CONST constant, Object visArg) {
		return constant.value;
	}

	@Override
	public Object visit(IMC.TEMP temp, Object visArg) {
		return temp.temp;
	}

	@Override
	public Object visit(IMC.NAME name, Object visArg) {
		MEM.Temp temp = new MEM.Temp();
		instructions.add(new ASM.Oper("la $1, #1", singleOp(temp.toString()), singleOp(name.label.toString())));
		return temp.toString();
	}
}
