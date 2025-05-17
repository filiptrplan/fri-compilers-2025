package compiler.phase.asmgen;

import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.Phase;
import compiler.phase.imcgen.IMC;
import compiler.phase.imclin.ImcLin;
import compiler.phase.imclin.LIN;

public class AsmGen extends Phase {
	public static final Vector<ASM.CodeInstrChunk> codeChunks = new Vector<>();
	public static final Vector<ASM.StringConstChunk> constChunks = new Vector<>();
	public static final Vector<ASM.GlobalVarChunk> varChunks = new Vector<>();

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

		Vector<LIN.DataChunk> linDataChunks = ImcLin.dataChunks();
		for (LIN.DataChunk linDataChunk : linDataChunks) {
			ASM.DataInstrChunk dataInstrChunk = DataGenerator.processDataChunk(linDataChunk);
			if (dataInstrChunk instanceof ASM.GlobalVarChunk varChunk) {
				varChunks.add(varChunk);
			} else if (dataInstrChunk instanceof ASM.StringConstChunk constChunk) {
				constChunks.add(constChunk);
			}
		}
	}

	/** Prints the generated assembly */
	public static void printAsm() {
		// String const section
		Report.info(".rodata");
		for (ASM.StringConstChunk chunk : constChunks) {
			Report.info(chunk.toString());
		}

		// Data section
		Report.info(".data");
		for (ASM.GlobalVarChunk chunk : varChunks) {
			Report.info(chunk.toString());
		}

		// Code section
		Report.info(".text");
		for (ASM.CodeInstrChunk chunk : codeChunks) {
			Report.info("#------- " + chunk.frame.label.name + " (FP=" + chunk.frame.FP + ", RV=" + chunk.frame.RV
					+ ", body=" + chunk.entryLabel.name + ", exit=" + chunk.exitLabel.name + ") ------- ");
			for (ASM.Instr instr : chunk.instructions) {
				Report.info(instr.toString());
			}
			Report.info("");
		}
	}

}
