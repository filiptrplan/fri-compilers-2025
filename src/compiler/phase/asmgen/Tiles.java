package compiler.phase.asmgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.asmgen.ASM.Instr;
import compiler.phase.imcgen.IMC;
import compiler.phase.imcgen.IMC.BINOP;
import compiler.phase.imcgen.IMC.BINOP.Oper;
import compiler.phase.memory.MEM;

public class Tiles {
	private static Vector<String> singleOp(String op) {
		Vector<String> vec = new Vector<>();
		vec.add(op);
		return vec;
	}

	private static Vector<String> doubleOp(String op1, String op2) {
		Vector<String> vec = new Vector<>();
		vec.add(op1);
		vec.add(op2);
		return vec;
	}

	private static String newTemp() {
		return (new MEM.Temp()).toString();
	}

	public static class ConstTile implements Tile {
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.CONST;
		}

		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			return Long.toString(((IMC.CONST) instr).value);
		}
	}

	public static class TempTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.TEMP;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			return ((IMC.TEMP) instr).toString();
		}
	}

	public static class NameTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.NAME;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.NAME name = (IMC.NAME) instr;
			MEM.Temp temp = new MEM.Temp();
			instructions.add(new ASM.Oper("la $1, #1", singleOp(temp.toString()), singleOp(name.label.toString())));
			return temp.toString();
		}
	}

	public static class Mem1RegTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.MEM1;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MEM1 mem1 = (IMC.MEM1) instr;
			String temp = newTemp();
			String register = mem1.accept(generator, null);
			instructions.add(new ASM.Oper("lb $1, 0(#1)", singleOp(temp), singleOp(register)));
			return temp;
		}
	}

	public static class Mem1ImmTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MEM1) {
				IMC.MEM1 mem1 = (IMC.MEM1) instr;
				if (mem1.addr instanceof BINOP) {
					IMC.BINOP binop = (IMC.BINOP) mem1.addr;
					return binop.oper == IMC.BINOP.Oper.ADD && binop.fstExpr instanceof IMC.TEMP
							&& binop.sndExpr instanceof IMC.CONST;
				}
			}
			return false;
		}

		@Override
		public int getSize() {
			return 4;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MEM1 mem1 = (IMC.MEM1) instr;
			IMC.BINOP binop = (IMC.BINOP) mem1.addr;
			String constant = binop.sndExpr.accept(generator, null);
			String reg = binop.fstExpr.accept(generator, null);
			String temp = newTemp();

			instructions.add(new ASM.Oper("lb $1, #1(#2)", singleOp(temp), doubleOp(constant, reg)));
			return temp;
		}
	}

	public static class Mem8RegTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.MEM8;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MEM8 mem8 = (IMC.MEM8) instr;
			String temp = newTemp();
			String register = mem8.addr.accept(generator, null);
			instructions.add(new ASM.Oper("ld $1, 0(#1)", singleOp(temp), singleOp(register)));
			return temp;
		}
	}

	public static class Mem8ImmTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MEM8) {
				IMC.MEM8 mem8 = (IMC.MEM8) instr;
				if (mem8.addr instanceof BINOP) {
					IMC.BINOP binop = (IMC.BINOP) mem8.addr;
					return binop.oper == IMC.BINOP.Oper.ADD && binop.fstExpr instanceof IMC.TEMP
							&& binop.sndExpr instanceof IMC.CONST;
				}
			}
			return false;
		}

		@Override
		public int getSize() {
			return 4;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MEM8 mem8 = (IMC.MEM8) instr;
			IMC.BINOP binop = (IMC.BINOP) mem8.addr;
			String constant = binop.sndExpr.accept(generator, null);
			String reg = binop.fstExpr.accept(generator, null);
			String temp = newTemp();

			instructions.add(new ASM.Oper("ld $1, #1(#2)", singleOp(temp), doubleOp(constant, reg)));
			return temp;
		}
	}

	public static class BinopTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.BINOP;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.BINOP binop = (IMC.BINOP) instr;
			String r1 = binop.fstExpr.accept(generator, instructions);
			String r2 = binop.sndExpr.accept(generator, instructions);
			String rd = newTemp();

			switch (binop.oper) {
				case ADD:
					instructions.add(new ASM.Oper("add $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case SUB:
					instructions.add(new ASM.Oper("sub $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case MUL:
					instructions.add(new ASM.Oper("mul $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case DIV:
					// Assuming signed division
					instructions.add(new ASM.Oper("div $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case MOD:
					// Assuming signed remainder
					instructions.add(new ASM.Oper("rem $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case AND:
					instructions.add(new ASM.Oper("and $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case OR:
					instructions.add(new ASM.Oper("or $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case EQU: {
					String t0 = newTemp();
					instructions.add(new ASM.Oper("sub $1, #1, #2", singleOp(t0), doubleOp(r1, r2)));
					instructions.add(new ASM.Oper("seqz $1, #1", singleOp(rd), singleOp(t0)));
					break;
				}
				case NEQ: {
					String t0 = newTemp();
					instructions.add(new ASM.Oper("sub $1, #1, #2", singleOp(t0), doubleOp(r1, r2)));
					instructions.add(new ASM.Oper("snez $1, #1", singleOp(rd), singleOp(t0)));
					break;
				}
				case LTH:
					instructions.add(new ASM.Oper("slt $1, #1, #2", singleOp(rd), doubleOp(r1, r2)));
					break;
				case LEQ:
					// rd = (r2 < r1) ? 0 : 1 which is !(r2 < r1)
					instructions.add(new ASM.Oper("slt $1, #1, #2", singleOp(rd), doubleOp(r2, r1))); // rd = r2 < r1
					instructions.add(new ASM.Oper("xori $1, #1, 1", singleOp(rd), singleOp(rd))); // rd = rd XOR 1
					break;
				case GTH:
					// rd = (r2 < r1)
					instructions.add(new ASM.Oper("slt $1, #1, #2", singleOp(rd), doubleOp(r2, r1)));
					break;
				case GEQ:
					// rd = (r1 < r2) ? 0 : 1 which is !(r1 < r2)
					instructions.add(new ASM.Oper("slt $1, #1, #2", singleOp(rd), doubleOp(r1, r2))); // rd = r1 < r2
					instructions.add(new ASM.Oper("xori $1, #1, 1", singleOp(rd), singleOp(rd))); // rd = rd XOR 1
					break;
				default:
					throw new Report.InternalError();
			}

			return rd;
		}
	}

	public static class UnopTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.UNOP;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.UNOP unop = (IMC.UNOP) instr;
			String r1 = unop.subExpr.accept(generator, instructions);
			String rd = newTemp();

			switch (unop.oper) {
				case NEG:
					// rd = 0 - r1
					instructions.add(new ASM.Oper("sub $1, x0, #1", singleOp(rd), singleOp(r1)));
					break;
				case NOT:
					// rd = (r1 == 0) ? 1 : 0
					instructions.add(new ASM.Oper("seqz $1, #1", singleOp(rd), singleOp(r1)));
					break;
				default:
					throw new Report.InternalError();
			}
			return rd;
		}
	}

}
