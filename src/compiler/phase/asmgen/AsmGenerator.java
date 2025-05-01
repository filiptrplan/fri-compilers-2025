package compiler.phase.asmgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.imcgen.IMC;
import compiler.phase.memory.MEM;

/** Generates assembly code from intermediate code */
public class AsmGenerator implements IMC.Visitor<String, Object> {

	private final Vector<ASM.Instr> instructions;
	public final MEM.Frame callFrame;

	public AsmGenerator(Vector<ASM.Instr> instructions, MEM.Frame frame) {
		this.instructions = instructions;
		this.callFrame = frame;
	}

	/** Implementation of the maximal munch algorithm */
	private String matchTiles(IMC.Instr instr) {
		int maxSize = 0;
		Tile maxTile = null;
		for (Tile tile : Tiles.availableTiles) {
			if (tile.matches(instr)) {
				int size = tile.getSize();
				if (size > maxSize) {
					maxSize = size;
					maxTile = tile;
				}
			}
		}

		if (maxTile == null) {
			// Should not happen, a tile is not matched
			Report.info(instr.toString());
			throw new Report.InternalError();
		}

		String temp = maxTile.emit(this, instructions, instr);
		return temp;
	}

	@Override
	public String visit(IMC.STMTS stmts, Object visArg) {
		for (IMC.Stmt stmt : stmts.stmts)
			matchTiles(stmt);
		return null;
	}

	@Override
	public String visit(IMC.CONST constant, Object visArg) {
		return matchTiles(constant);
	}

	@Override
	public String visit(IMC.TEMP temp, Object visArg) {
		return matchTiles(temp);
	}

	@Override
	public String visit(IMC.NAME name, Object visArg) {
		return matchTiles(name);
	}

	@Override
	public String visit(IMC.BINOP binOp, Object visArg) {
		return matchTiles(binOp);
	}

	@Override
	public String visit(IMC.CALL call, Object visArg) {
		return matchTiles(call);
	}

	@Override
	public String visit(IMC.MEM1 mem, Object visArg) {
		return matchTiles(mem);
	}

	@Override
	public String visit(IMC.MEM8 mem, Object visArg) {
		return matchTiles(mem);
	}

	@Override
	public String visit(IMC.SEXPR sExpr, Object visArg) {
		return matchTiles(sExpr);
	}

	@Override
	public String visit(IMC.UNOP unOp, Object visArg) {
		return matchTiles(unOp);
	}

	@Override
	public String visit(IMC.CJUMP cjump, Object visArg) {
		matchTiles(cjump);
		return null;
	}

	@Override
	public String visit(IMC.ESTMT eStmt, Object visArg) {
		matchTiles(eStmt.expr);
		return null;
	}

	@Override
	public String visit(IMC.JUMP jump, Object visArg) {
		matchTiles(jump);
		return null;
	}

	@Override
	public String visit(IMC.LABEL label, Object visArg) {
		return matchTiles(label);
	}

	@Override
	public String visit(IMC.MOVE move, Object visArg) {
		matchTiles(move);
		return null;
	}
}
