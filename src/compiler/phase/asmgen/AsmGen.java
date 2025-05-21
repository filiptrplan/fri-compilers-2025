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

	private static String asmString() {
		StringBuilder sb = new StringBuilder();
		// Data section
		// We create it only if we have something to put into it
		if (constChunks.size() + varChunks.size() > 0) {
			sb.append(".data\n");
			for (ASM.StringConstChunk chunk : constChunks) {
				sb.append(chunk.toString()).append("\n");
			}
			for (ASM.GlobalVarChunk chunk : varChunks) {
				sb.append(chunk.toString()).append("\n");
			}
		}

		// Code section
		sb.append(".text\n");
		for (ASM.CodeInstrChunk chunk : codeChunks) {
			sb.append("#------- ").append(chunk.frame.label.name)
					.append(" (FP=").append(chunk.frame.FP)
					.append(", RV=").append(chunk.frame.RV)
					.append(", body=").append(chunk.entryLabel.name)
					.append(", exit=").append(chunk.exitLabel.name)
					.append(") ------- \n");
			for (ASM.Instr instr : chunk.instructions) {
				sb.append(instr.toString()).append("\n");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	/** Prints the generated assembly */
	public static void printAsm() {
		Report.info(AsmGen.asmString());
	}

	public static void writeAsmToFile(String filename) {
		try {
			java.nio.file.Files.writeString(java.nio.file.Path.of(filename), asmString());
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to write assembly to file: " + filename, e);
		}
	}

}
