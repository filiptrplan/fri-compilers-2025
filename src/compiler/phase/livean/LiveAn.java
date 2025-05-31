package compiler.phase.livean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import compiler.common.Helper;
import compiler.common.report.Report;
import compiler.phase.Phase;
import compiler.phase.asmgen.ASM;
import compiler.phase.livean.DataFlowGraph.Node;

public class LiveAn extends Phase {
	/*
	 * Total number of registers to use. Including the x2, x3, x10 registers for FP,
	 * SP, RV
	 */
	public static int K;

	public LiveAn(int K) {
		super("livean");
		if (K - 3 < 2) {
			throw new Report.Error("The compiler needs at least 2 registers to function.");
		}
		LiveAn.K = K;
	}

	public static HashMap<ASM.CodeInstrChunk, IntGraph> intGraphs = new HashMap<>();

	public static void calculateInterferenceGraph(ASM.CodeInstrChunk instrChunk) {
		// The SP and FP should be live at the start of the program
		List<String> initialLiveRegs = List.of(instrChunk.frame.FP.toString(), "SP");
		// The SP, FP and RV should be live at the end of the program
		List<String> finalLiveRegs = List.of("SP", instrChunk.frame.FP.toString(), instrChunk.frame.RV.toString());
		DataFlowGraph dfg = calculateLiveVariables(instrChunk, initialLiveRegs, finalLiveRegs);

		IntGraph ig = new IntGraph(instrChunk.frame, LiveAn.K);
		for (Node node : dfg.getNodes()) {
			Set<String> defTemp = node.defTemp;
			Set<String> liveOutTemp = node.outTemp;
			boolean isMove = node.instr instanceof ASM.Move;

			if (!isMove) {
				// For non-moves we the out variables interfere with the defined variables
				for (String def : defTemp) {
					for (String liveOut : liveOutTemp) {
						ig.addEdge(def, liveOut, false);
					}
				}
			} else {
				String def = node.instr.defOps.getFirst();
				String use = node.instr.useOps.getFirst();
				for (String liveOut : liveOutTemp) {
					// For moves we don't add an extra edge between the used and defined variables
					if (!liveOut.equals(use)) {
						ig.addEdge(def, liveOut, true);
					}
				}
			}
		}

		// Add all the rest of the nodes that don't have any incoming edges
		for (Node node : dfg.getNodes()) {
			Set<String> defTemp = node.defTemp;
			Set<String> useTemp = node.useTemp;
			if (defTemp != null) {
				for (String def : defTemp) {
					ig.addNode(def);
				}
			}
			if (useTemp != null) {
				for (String use : useTemp) {
					ig.addNode(use);
				}
			}
		}

		ig.addPhysicalConstraint(instrChunk.frame.RV.toString(), "x10");
		ig.addPhysicalConstraint(instrChunk.frame.FP.toString(), "x3");
		ig.addPhysicalConstraint("SP", "x2");

		// dfg.printToMermaidFile(instrChunk.frame.label.name + ".md");
		// ig.printToMermaidFile(instrChunk.frame.label.name + ".ig.md");
		// dfg.printAsmWithDfg(instrChunk);

		intGraphs.put(instrChunk, ig);
	}

	private static DataFlowGraph constructDfg(Vector<ASM.Instr> instructions) {
		DataFlowGraph dfg = new DataFlowGraph();

		// Construct initial graph
		for (ASM.Instr instr : instructions) {
			// We only care about operation instructions
			if (instr instanceof ASM.Oper oper) {
				dfg.addNode(oper);
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
					if (jumpTo != null) {
						currNode.addEdge(jumpTo);
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

	private static Set<String> diff(Set<String> a, Set<String> b) {
		Set<String> result = new HashSet<>();
		for (String s : a) {
			if (!b.contains(s)) {
				result.add(s);
			}
		}
		return result;
	}

	/**
	 * Calculates which variables are live at each point in the program
	 *
	 * @param instructions    The actual instructions of the program
	 * @param initialLiveRegs The register that should be live even before starting
	 *                        the code (i.e. in the prologue)
	 * @param finalLiveRegs   The registers that are live after we finish the
	 *                        function (i.e. in the epilogue)
	 */
	private static DataFlowGraph calculateLiveVariables(ASM.CodeInstrChunk chunk, List<String> initialLiveRegs,
			List<String> finalLiveRegs) {
		Vector<ASM.Instr> instructions = chunk.instructions;
		DataFlowGraph dfg = constructDfg(instructions);
		dfg.seedFinalRegs(chunk, finalLiveRegs);
		dfg.seedInitialRegs(chunk, initialLiveRegs);
		// Get first non-label instruction
		ASM.Instr first = null;
		for (ASM.Instr instr : instructions) {
			if (instr instanceof ASM.Label) {
				continue;
			}
			first = instr;
			break;
		}
		List<Node> sorted = dfg.quasiTopoSortNodes(first);
		List<Integer> oldHashes = new ArrayList<>();
		List<Integer> newHashes = new ArrayList<>();

		for (Node node : sorted) {
			oldHashes.add(node.inOutHash());
			newHashes.add(123);
		}

		while (true) {
			for (int i = 0; i < sorted.size(); i++) {
				Node node = sorted.get(i);
				node.resetInTemp();

				if (node.useTemp != null) {
					node.inTemp.addAll(node.useTemp);
				}

				if (node.defTemp != null) {
					node.inTemp.addAll(diff(node.outTemp, node.defTemp));
				} else {
					node.inTemp.addAll(node.outTemp);
				}

				node.resetOutTemp();
				for (Node succ : node.outNodes()) {
					node.outTemp.addAll(succ.inTemp);
				}
				newHashes.set(i, node.inOutHash());
			}
			if (oldHashes.equals(newHashes)) {
				break;
			}
			List<Integer> tmp = oldHashes;
			oldHashes = newHashes;
			newHashes = tmp;
		}

		return dfg;
	}
}
