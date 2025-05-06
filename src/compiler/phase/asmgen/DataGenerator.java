package compiler.phase.asmgen;

import compiler.common.report.Report;
import compiler.phase.imclin.LIN;

public class DataGenerator {
	private static String getEscapedString(String str) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c >= 32 && c <= 126 && c != '\\' && c != '"') {
				sb.append(c);
			} else {
				sb.append("\\x");
				sb.append(String.format("%02x", (int) c));
			}
		}

		return sb.toString();
	}

	public static ASM.DataInstrChunk processDataChunk(LIN.DataChunk linDataChunk) {
		ASM.DataInstrChunk chunk = null;

		if (linDataChunk.type == LIN.DataChunkType.GlobalVar) {
			chunk = new ASM.GlobalVarChunk(linDataChunk.label.name, linDataChunk.size);
		} else if (linDataChunk.type == LIN.DataChunkType.StringConst) {
			chunk = new ASM.StringConstChunk(linDataChunk.label.name, getEscapedString(linDataChunk.init));
		} else {
			throw new Report.InternalError();
		}
		return chunk;
	}
}
