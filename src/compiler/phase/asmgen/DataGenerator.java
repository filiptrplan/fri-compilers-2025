package compiler.phase.asmgen;

import compiler.common.report.Report;
import compiler.phase.imclin.LIN;

public class DataGenerator {
	public static ASM.DataInstrChunk processDataChunk(LIN.DataChunk linDataChunk) {
		ASM.DataInstrChunk chunk = null;

		if (linDataChunk.type == LIN.DataChunkType.GlobalVar) {
			chunk = new ASM.GlobalVarChunk(linDataChunk.label.name, linDataChunk.size);
		} else if (linDataChunk.type == LIN.DataChunkType.StringConst) {
			chunk = new ASM.StringConstChunk(linDataChunk.label.name, linDataChunk.init);
		} else {
			throw new Report.InternalError();
		}
		return chunk;
	}
}
