package compiler.phase.regall;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import compiler.phase.asmgen.ASM;
import compiler.phase.livean.IntGraph;
import compiler.phase.livean.LiveAn;

public class RegisterAllocator {
	private Stack<String> stack = new Stack<>();
	private Set<String> potentialSpillSet = new HashSet<>();
	private Set<String> actualSpillSet = new HashSet<>();
	private int K;
	private IntGraph intGraph;
	private ASM.CodeInstrChunk codeChunk;

	/**
	 * The constructor for the register allocator
	 * 
	 * @param K         the number of registers
	 * @param codeChunk the code chunk to be allocated
	 **/
	public RegisterAllocator(int K, ASM.CodeInstrChunk codeChunk) {
		this.K = K;
		this.codeChunk = codeChunk;
		this.intGraph = LiveAn.intGraphs.get(codeChunk);
	}

	/**
	 * Assigns registers to the code chunk. Saves the result in the code chunk.
	 **/
	public void assignRegisters() {
		Map<String, Integer> colorMap = colorGraph();

		// TODO: Implement register allocation
	}

	/**
	 * Colors the graph of the code chunk by popping nodes from the stack and
	 * assigning them a color.
	 **/
	private Map<String, Integer> colorGraph() {
		Map<String, Integer> colorMap = new HashMap<>();
		for (String node : intGraph.getNodes()) {
			colorMap.put(node, 0);
		}
		return colorMap;
	}

	/**
	 * The simplify phase of the coloring by simplifaction algorithm
	 * It find any nodes with a degree < K and removes it from the graph to the
	 * stack.
	 **/
	private void simplify() {
		boolean nodesSimplified = true;
		while (nodesSimplified) {
			nodesSimplified = false;
			for (String node : intGraph.getTempNodes()) {
				if (intGraph.degree(node) < K) {
					nodesSimplified = true;
					stack.push(node);
					intGraph.markRemoved(node);
				}
			}
		}
	}

	/**
	 * The spill phase of the coloring by simplifaction algorithm
	 * It finds a node of degree >= K and spills it to the spill set.
	 */
	void spill() {
		for (String node : intGraph.getTempNodes()) {
			if (intGraph.degree(node) >= K) {
				potentialSpillSet.add(node);
				intGraph.markRemoved(node);
				break;
			}
		}
	}
}
