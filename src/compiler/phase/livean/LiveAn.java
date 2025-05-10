package compiler.phase.livean;

import java.util.HashMap;
import java.util.Vector;

import compiler.phase.asmgen.ASM;
import compiler.phase.livean.DataFlowGraph.Node;

public class LiveAn {
	public IntGraph calculateInterferenceGraph(ASM.CodeInstrChunk instrChunk) {

	}

	public DataFlowGraph calculateLiveVariables(Vector<ASM.Instr> instructions) {
		DataFlowGraph dfg = new DataFlowGraph();

		// Construct initial graph
		for (ASM.Instr instr : instructions) {
			// We only care about operation instructions
			if (instr instanceof ASM.Oper) {
				dfg.addNode(instr);
			}
		}

		// Now we add edges

		// First we map all the labels to the next instruction
		String label = null;
		HashMap<String, Node> labelMap = new HashMap<>();
		for (ASM.Instr instr : instructions) {
			if (instr instanceof ASM.Label labelInstr) {
				label = labelInstr.label;
			} else {
				if (label != null) {
					labelMap.put(label, dfg.getNode(instr));
					label = null;
				}
			}
		}

		// Then we add all the edges.
		// If the previous instruction is not a jump then we add an edge unconditionally
		// Else we handle the type of jump
		for (ASM.Instr instr : instructions) {

		}
	}
}
