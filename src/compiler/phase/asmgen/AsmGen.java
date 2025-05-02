package compiler.phase.asmgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.Phase;
import compiler.phase.imcgen.IMC;
import compiler.phase.imclin.ImcLin;
import compiler.phase.imclin.LIN;

public class AsmGen extends Phase {
	public static final Vector<ASM.CodeInstrChunk> codeChunks = new Vector<>();

	public AsmGen() {
		super("asmgen");
	}

	private Vector<ASM.Instr> generateAsmFromChunk(LIN.CodeChunk chunk) {
		Vector<ASM.Instr> instrs = new Vector<>();
		AsmGenerator asmGenerator = new AsmGenerator(instrs, chunk.frame);
		for (IMC.Stmt stmt : chunk.stmts()) {
			stmt.accept(asmGenerator, null);
		}
		return instrs;
	}

	/** Generates assembly from codechunks from the imclin phase */
	public void generateAsm() {
		Vector<LIN.CodeChunk> linCodeChunks = ImcLin.codeChunks();
		for (LIN.CodeChunk chunk : linCodeChunks) {
			Vector<ASM.Instr> instrs = generateAsmFromChunk(chunk);
			ASM.CodeInstrChunk instrChunk = new ASM.CodeInstrChunk(chunk, instrs);
			codeChunks.add(instrChunk);
		}
	}

	/** Prints the generated assembly */
	public void printAsm() {
		for (ASM.CodeInstrChunk chunk : codeChunks) {
			Report.info(" ------- " + chunk.frame.label.name + " (FP=" + chunk.frame.FP + ", RV=" + chunk.frame.RV
					+ ") ------- ");
			for (ASM.Instr instr : chunk.instructions) {
				Report.info(instr.toString());
			}
			Report.info("");
		}
	}

}
