package compiler.phase.asmgen;

import java.util.List;
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

	public static final List<Tile> availableTiles = List.of(
			new MoveTempConstTile(),
			new MoveTempTempTile(),
			new MoveTempNameTile(),
			new MoveMem8ImmTile(),
			new MoveMem1ImmTile(),
			new MoveMem8RegTile(),
			new MoveMem1RegTile(),
			new MoveTempTile(),

			new Mem8ImmTile(),
			new Mem1ImmTile(),
			new Mem8RegTile(),
			new Mem1RegTile(),

			new ConstTile(),
			new TempTile(),
			new NameTile(),
			new BinopTile(),
			new UnopTile(),
			new CallTile(),

			new LabelTile(),
			new JumpNameTile(),
			new JumpTile(),
			new CJumpTile());

	// --- Statement Tiles ---

	/**
	 * Tile for LABEL(label) -> label:
	 */
	public static class LabelTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.LABEL;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.LABEL label = (IMC.LABEL) instr;
			instructions.add(new ASM.Label(label.label.name));
			return label.label.name;
		}
	}

	/** Tile for JUMP(expr) -> jr reg */
	public static class JumpTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.JUMP;
		}

		@Override
		public int getSize() {
			return 2;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.JUMP jump = (IMC.JUMP) instr;
			String targetRegister = jump.addr.accept(generator, instructions);
			instructions.add(new ASM.Oper("jr #1", null, singleOp(targetRegister)));
			return null;
		}
	}

	/**
	 * Tile for JUMP(NAME(label)) -> jr reg
	 */
	public static class JumpNameTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.JUMP jump) {
				return jump.addr instanceof IMC.NAME;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 3;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.JUMP jump = (IMC.JUMP) instr;
			IMC.NAME name = (IMC.NAME) jump.addr;
			instructions.add(new ASM.Oper("j #1", null, singleOp(name.label.name)));
			return null;
		}
	}

	/**
	 * Tile for CJUMP(cond, NAME(trueLabel), NAME(falseLabel)) -> bnez condReg,
	 * trueLabel
	 * Assumes the falseLabel immediately follows this instruction due to
	 * linearization.
	 */
	public static class CJumpTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.CJUMP cjump) {
				// Check if both targets are NAMEs, as expected after linearization
				return cjump.posAddr instanceof IMC.NAME && cjump.negAddr instanceof IMC.NAME;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 3;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.CJUMP cjump = (IMC.CJUMP) instr;

			String condReg = cjump.cond.accept(generator, instructions);
			// We only need the true label, as the false label is the next instruction
			String trueLabel = ((IMC.NAME) cjump.posAddr).label.name;

			// Branch if condition register is not zero (true)
			instructions.add(new ASM.Oper("bnez #1, #2", null, doubleOp(condReg, trueLabel)));

			return null;
		}
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

	// --- Expression tiles ---

	public static class ConstTile implements Tile {
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.CONST;
		}

		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<ASM.Instr> instructions, IMC.Instr instr) {
			return String.valueOf(((IMC.CONST) instr).value);
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
			instructions.add(new ASM.Oper("la $1, #1", singleOp(temp.toString()), singleOp(name.label.name)));
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

	public static class CallTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			return instr instanceof IMC.CALL;
		}

		@Override
		public int getSize() {
			return 1;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.CALL call = (IMC.CALL) instr;
			String temp = newTemp();
			String func = call.addr.accept(generator, instructions);
			Vector<String> args = new Vector<>();
			for (IMC.Expr arg : call.args) {
				// Because of linearization these will always be temps
				args.add(arg.accept(generator, instructions));
			}

			for (int i = 0; i < args.size(); i++) {
				instructions
						.add(new ASM.Oper("sd #1, " + call.offs.get(i) + "(#2)", null, doubleOp(args.get(i), "SP")));
			}

			instructions.add(new ASM.Oper("jal $1, #1", singleOp(temp), singleOp(func)));
			// The result is stored on the stack, we denote this with RV
			return "RV";
		}
	}

	// --- MOVE Tiles ---

	/**
	 * Tile for MOVE(TEMP(r1), CONST(const)) -> li r1, const
	 */
	public static class MoveTempConstTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.TEMP && move.src instanceof IMC.CONST;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 3;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			String r1 = move.dst.accept(generator, null);
			String constant = move.src.accept(generator, null);

			instructions.add(new ASM.Oper("li $1, #1", singleOp(r1), singleOp(constant)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(TEMP(r1), TEMP(r2)) -> mv r1, r2
	 */
	public static class MoveTempTempTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.TEMP && move.src instanceof IMC.TEMP;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 3;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			String r2 = move.src.accept(generator, null);
			String r1 = move.dst.accept(generator, null);

			instructions.add(new ASM.Move("mv $1, #1", singleOp(r1), singleOp(r2)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(TEMP(r1), r2) -> mv r1, r2
	 * Differs from `MoveTempTempTile` because it generates an ASM.Oper, not an
	 * ASM.Move
	 */
	public static class MoveTempTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.TEMP;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 2;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			String r2 = move.src.accept(generator, null);
			String r1 = move.dst.accept(generator, null);

			instructions.add(new ASM.Oper("mv $1, #1", singleOp(r1), singleOp(r2)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(TEMP(r1), NAME(name)) -> la r1, name
	 */
	public static class MoveTempNameTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.TEMP && move.src instanceof IMC.NAME;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 3;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<ASM.Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			String r1 = move.dst.accept(generator, null);
			String name = move.src.accept(generator, null);

			instructions.add(new ASM.Oper("la $1, #1", singleOp(r1), singleOp(name)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(MEM8(r1), r2) -> sd r2, 0(r1)
	 */
	public static class MoveMem8RegTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.MEM8;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 2;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			IMC.MEM8 dstMem = (IMC.MEM8) move.dst;

			String r1 = dstMem.addr.accept(generator, instructions);
			String r2 = move.src.accept(generator, instructions);

			instructions.add(new ASM.Oper("sd #1, 0(#2)", null, doubleOp(r2, r1)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(MEM8(BINOP(ADD, TEMP(r1), CONST(imm))), r2) -> sd r2, imm(r1)
	 */
	public static class MoveMem8ImmTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				if (move.dst instanceof IMC.MEM8 mem8) {
					if (mem8.addr instanceof IMC.BINOP binop) {
						return binop.oper == Oper.ADD && binop.fstExpr instanceof IMC.TEMP
								&& binop.sndExpr instanceof IMC.CONST;
					}
				}
			}
			return false;
		}

		@Override
		public int getSize() {
			return 5;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			IMC.MEM8 dstMem = (IMC.MEM8) move.dst;
			IMC.BINOP binop = (IMC.BINOP) dstMem.addr;

			String r1 = binop.fstExpr.accept(generator, null);
			String imm = binop.sndExpr.accept(generator, null);
			String r2 = move.src.accept(generator, instructions);

			Vector<String> useVec = new Vector<>();
			useVec.addAll(List.of(r2, imm, r1));

			instructions.add(new ASM.Oper("sd #1, #2(#3)", null, useVec));
			return null;
		}
	}

	/**
	 * Tile for MOVE(MEM1(r1), r2) -> sb r2, 0(r1)
	 */
	public static class MoveMem1RegTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				return move.dst instanceof IMC.MEM1;
			}
			return false;
		}

		@Override
		public int getSize() {
			return 2;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			IMC.MEM1 dstMem = (IMC.MEM1) move.dst;

			String r1 = dstMem.addr.accept(generator, instructions);
			String r2 = move.src.accept(generator, instructions);

			// sb r2, 0(r1)
			instructions.add(new ASM.Oper("sb #1, 0(#2)", null, doubleOp(r2, r1)));
			return null;
		}
	}

	/**
	 * Tile for MOVE(MEM1(BINOP(ADD, TEMP(r1), CONST(imm))), r2) -> sb r2, imm(r1)
	 */
	public static class MoveMem1ImmTile implements Tile {
		@Override
		public boolean matches(IMC.Instr instr) {
			if (instr instanceof IMC.MOVE move) {
				if (move.dst instanceof IMC.MEM1 mem1) {
					if (mem1.addr instanceof IMC.BINOP binop) {
						return binop.oper == Oper.ADD && binop.fstExpr instanceof IMC.TEMP
								&& binop.sndExpr instanceof IMC.CONST;
					}
				}
			}
			return false;
		}

		@Override
		public int getSize() {
			return 5;
		}

		@Override
		public String emit(AsmGenerator generator, Vector<Instr> instructions, IMC.Instr instr) {
			IMC.MOVE move = (IMC.MOVE) instr;
			IMC.MEM1 dstMem = (IMC.MEM1) move.dst;
			IMC.BINOP binop = (IMC.BINOP) dstMem.addr;

			String r1 = binop.fstExpr.accept(generator, null);
			String imm = binop.sndExpr.accept(generator, null);
			String r2 = move.src.accept(generator, instructions);

			Vector<String> useVec = new Vector<>();
			useVec.addAll(List.of(r2, imm, r1));

			instructions.add(new ASM.Oper("sb #1, #2(#3)", null, useVec));
			return null;
		}
	}

}
