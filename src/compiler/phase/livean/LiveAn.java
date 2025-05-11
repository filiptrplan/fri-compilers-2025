package compiler.phase.livean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import compiler.common.report.Report;
import compiler.phase.asmgen.ASM;
import compiler.phase.livean.DataFlowGraph.Node;

public class LiveAn {
	public static IntGraph calculateInterferenceGraph(ASM.CodeInstrChunk instrChunk) {
		DataFlowGraph dfg = calculateLiveVariables(instrChunk.instructions);
		dfg.printToMermaidFile(instrChunk.frame.label.name + ".md");
		return null;
	}

	private static DataFlowGraph constructDfg(Vector<ASM.Instr> instructions) {
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
		// There can be multiple labels pointing to one instruction
		List<String> labels = new ArrayList<>();
		HashMap<String, Node> labelMap = new HashMap<>();
		for (ASM.Instr instr : instructions) {
			if (instr instanceof ASM.Label labelInstr) {
				labels.add(labelInstr.label);
			} else {
				if (!labels.isEmpty()) {
					for (String label : labels) {
						labelMap.put(label, dfg.getNode(instr));
					}
					labels.clear();
				}
			}
		}

		// Then we add all the edges.
		// If the previous instruction is not a jump then we add an edge unconditionally
		// Else we handle the type of jump
		ASM.Oper prev = null;
		for (ASM.Instr instr : instructions) {
			if (instr instanceof ASM.Oper oper) {
				Node currNode = dfg.getNode(instr);
				if (oper.isJump || oper.isCjump) {
					Node jumpTo = labelMap.get(oper.jumpTarget);
					Report.info(Integer.toString(currNode.hashCode()) + " - " + oper.jumpTarget);
					if (jumpTo != null) {
						currNode.addEdge(jumpTo);
					} else {
						Report.info("Label " + oper.jumpTarget + " not found in function.");
					}
				}
				// Add a back connection if the previous instruction wasn't an unconditional
				// jump
				if (prev != null && !prev.isJump) {
					Node prevNode = dfg.getNode(prev);
					prevNode.addEdge(currNode);
				}
				prev = oper;
			}
		}

		return dfg;
	}

	private static DataFlowGraph calculateLiveVariables(Vector<ASM.Instr> instructions) {
		DataFlowGraph dfg = constructDfg(instructions);
		return dfg;
	}
}
