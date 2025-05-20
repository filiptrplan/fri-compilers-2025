package compiler.phase.regall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

import compiler.common.Helper;
import compiler.common.report.Report;
import compiler.phase.asmgen.ASM;
import compiler.phase.livean.IntGraph;
import compiler.phase.livean.LiveAn;

public class RegisterAllocator {
	private Stack<String> stack = new Stack<>();

	private Set<String> previouslySpilled = new HashSet<>();
	private Set<String> potentialSpillSet = new HashSet<>();
	private Set<String> actualSpillSet = new HashSet<>();

	private int K;
	private IntGraph intGraph;
	private ASM.CodeInstrChunk codeChunk;

	private final List<String> physicalRegistersReserved = List.of("x2", "x3", "x10");
	private final List<String> physicalRegistersNotReserved = List.of("x1", "x4", "x5", "x6", "x7", "x8", "x9", "x10",
			"x11", "x12", "x13", "x14", "x15", "x16", "x17", "x18", "x19", "x20", "x21", "x22", "x23", "x24", "x25",
			"x26", "x27", "x28", "x29", "x30", "x31");

	/** The size of the stack frame that is allocated for temporary variables */
	private long tempVariableSize = 0;

	/**
	 * The constructor for the register allocator
	 * 
	 * @param K         the number of registers
	 * @param codeChunk the code chunk to be allocated
	 **/
	public RegisterAllocator(int K, ASM.CodeInstrChunk codeChunk, Set<String> previouslySpilled) {
		this.K = K;
		if (K > physicalRegistersNotReserved.size()) {
			throw new Report.InternalError();
		}
		this.codeChunk = codeChunk;
		this.intGraph = LiveAn.intGraphs.get(codeChunk);
		if (previouslySpilled != null) {
			this.previouslySpilled = previouslySpilled;
		}
	}

	/**
	 * Assigns registers to the code chunk. Saves the result in the code chunk.
	 **/
	public void assignRegisters() {
		// We do the simplify and spil phases until the graph is empty of temp variables
		while (!intGraph.isTempEmpty()) {
			simplify();
			spill();
		}

		// Then we push the physical registers to the stack
		while (!intGraph.isEmpty()) {
			String node = intGraph.getNodes().get(0);
			stack.push(node);
			intGraph.markRemoved(node);
		}

		// Then we do the select phase
		Map<String, Integer> colorMap = select();

		// We check if we have any actual spills
		if (!actualSpillSet.isEmpty()) {
			codeChunk.instructions = handleActualSpills();
			codeChunk.tempSize = tempVariableSize;

			LiveAn.calculateInterferenceGraph(codeChunk);
			RegAll.assignRegisters(codeChunk, actualSpillSet);
		} else {
			String[] regs = new String[K];
			// Then we assign the rest of the registers
			int j = 0;
			for (int i = 0; i < K; i++) {
				if (regs[i] == null) {
					regs[i] = physicalRegistersNotReserved.get(j);
					j++;
				}
			}

			// Finally we assign the registers to the code chunk
			for (ASM.Instr instr : codeChunk.instructions) {
				if (!(instr instanceof ASM.Oper oper)) {
					continue;
				}
				if (oper.defOps != null) {
					for (String def : oper.defOps) {
						if (!Helper.isRegister(def)) {
							continue;
						} else if (def.equals(codeChunk.frame.RV.toString())) {
							oper.defOps.set(oper.defOps.indexOf(def), "x10");
						} else if (colorMap.containsKey(def)) {
							oper.defOps.set(oper.defOps.indexOf(def), regs[colorMap.get(def)]);
						} else {

							Report.warning("Cannot find: " + def);
							throw new Report.InternalError();
						}
					}
				}
				if (oper.useOps != null) {
					for (String use : oper.useOps) {
						if (!Helper.isRegister(use) || use.startsWith("x")) {
							continue;
						}
						if (use.equals(codeChunk.frame.RV.toString())) {
							oper.useOps.set(oper.useOps.indexOf(use), "x10");
						} else if (use.equals(codeChunk.frame.FP.toString())) {
							oper.useOps.set(oper.useOps.indexOf(use), "x3");
						} else if (use.equals("SP")) {
							oper.useOps.set(oper.useOps.indexOf(use), "x2");
						} else if (colorMap.containsKey(use)) {
							oper.useOps.set(oper.useOps.indexOf(use), regs[colorMap.get(use)]);
						} else {
							Report.warning("Cannot find: " + use);
							throw new Report.InternalError();
						}
					}
				}
			}

			// Cleanup unnecessary moves
			Vector<ASM.Instr> newInstrs = new Vector<>(codeChunk.instructions);
			for (ASM.Instr instr : codeChunk.instructions) {
				if (instr instanceof ASM.Move move) {
					if (move.useOps.getFirst().equals(move.defOps.getFirst())) {
						newInstrs.remove(instr);
					}
				}
			}

			codeChunk.instructions = newInstrs;
		}

		// TODO: Transform instructions to use x2 as the SP register
		// Ill probably do that in the prologue

	}

	/**
	 * Returns a new vector of instructions with actual spills transformed to loads
	 * and stores
	 */
	private Vector<ASM.Instr> handleActualSpills() {
		// Spill map maps a spilled temp variable to its offset from the frame pointer
		HashMap<String, Long> spillMap = new HashMap<>();

		// We create a new vector of instructions
		Vector<ASM.Instr> newInstrs = new Vector<>();
		// A temp variable to save the instructions that must come after the current one
		Vector<ASM.Instr> saveInstrs = new Vector<>();
		for (ASM.Instr instr : codeChunk.instructions) {
			// If it's not an oper, just add it to the new vector
			if (!(instr instanceof ASM.Oper oper)) {
				newInstrs.add(instr);
				continue;
			}

			if (oper.defOps != null) {
				for (String def : oper.defOps) {
					if (actualSpillSet.contains(def)) {
						// If we haven't calculated the offset yet, do it now
						if (!spillMap.containsKey(def)) {
							// Caclulate the offset of the temp variable
							long fpOffset = codeChunk.frame.locsSize + tempVariableSize;
							// Increment the offset of the next temp variable
							tempVariableSize += 8;
							spillMap.put(def, fpOffset);
						}
						long fpOffset = spillMap.get(def);
						// Save the temp variable to the location
						String operStr = "sd #1, " + fpOffset + "(#2)";
						Vector<String> newUseOps = new Vector<>();
						// First use op is the temp variable
						newUseOps.add(def);
						// Second use op is the frame pointer
						newUseOps.add(codeChunk.frame.FP.toString());
						// We save to saveInstrs as we are insterting it after the current instruction
						saveInstrs.add(new ASM.Oper(operStr, null, newUseOps));
					}
				}
			}

			if (oper.useOps != null) {
				for (String use : oper.useOps) {
					if (actualSpillSet.contains(use)) {
						// The offset should be already calculated
						if (!spillMap.containsKey(use)) {
							Report.warning("Cannot find in spill map: " + use);
							throw new Report.InternalError();
						}

						long fpOffset = spillMap.get(use);
						// Retrieve the temp variable from the location
						String operStr = "ld $1, " + fpOffset + "(#1)";
						Vector<String> newDefOps = new Vector<>();
						Vector<String> newUseOps = new Vector<>();
						// First def op is the temp variable
						newDefOps.add(use);
						// First use op is the frame pointer
						newUseOps.add(codeChunk.frame.FP.toString());
						// We save to newInstrs as we are insterting before the current instruction
						newInstrs.add(new ASM.Oper(operStr, newDefOps, newUseOps));
					}
				}
			}

			newInstrs.add(oper);
			newInstrs.addAll(saveInstrs);
			saveInstrs.clear();
		}

		return newInstrs;
	}

	/**
	 * The select phase of the coloring by simplifaction algorithm
	 *
	 * Colors the graph of the code chunk by popping nodes from the stack and
	 * assigning them a color.
	 *
	 * Also modifies the potential spill set and actual spill set.
	 **/
	private Map<String, Integer> select() {
		Map<String, Integer> colorMap = new HashMap<>();

		while (!stack.isEmpty()) {
			String node = stack.pop();
			// Unmark the node as removed
			intGraph.unmarkRemoved(node);

			List<String> neighbors = intGraph.getNeighbors(node);
			List<Integer> neighborsColors = new ArrayList<>();
			for (String neighbor : neighbors) {
				if (colorMap.containsKey(neighbor)) {
					neighborsColors.add(colorMap.get(neighbor));
				}
			}
			// We find the lowest not-used color of the neighbors
			Collections.sort(neighborsColors);
			int color = 0;
			for (int neighborColor : neighborsColors) {
				if (neighborColor == color) {
					color++;
				}
				// We don't break as we can have multiple neighbors with the same color
			}

			// We have a potential spill that we now move to actual spill set
			if (color >= K) {
				if (!potentialSpillSet.contains(node)) {
					Report.warning("Cannot find in potential spill set: " + node);
					Report.warning("Neighbors: " + intGraph.getNeighbors(node));
					Report.warning("Colors: " + neighborsColors);
					Report.warning("Degree: " + intGraph.degree(node));
					throw new Report.InternalError();
				}

				actualSpillSet.add(node);
				potentialSpillSet.remove(node);
				// We use -1 as a special value for the color of "no color"
				colorMap.put(node, -1);
			} else {
				colorMap.put(node, color);
			}
		}
		return colorMap;
	}

	/**
	 * The simplify phase of the coloring by simplifaction algorithm
	 * It find any nodes with a degree less than K and removes it from the graph to
	 * the stack.
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
	 * It uses a heuristic to find the node to spill, it picks the node with the
	 * highest degree.
	 */
	void spill() {
		int maxDegree = 0;
		String maxNode = null;
		int maxDegreePrev = 0;
		String maxNodePrev = null;
		for (String node : intGraph.getTempNodes()) {
			if (intGraph.degree(node) >= K) {
				if (previouslySpilled.contains(node)) {
					if (intGraph.degree(node) > maxDegreePrev) {
						maxDegreePrev = intGraph.degree(node);
						maxNodePrev = node;
					}
				} else {
					if (intGraph.degree(node) > maxDegree) {
						maxDegree = intGraph.degree(node);
						maxNode = node;
					}
				}
			}
		}
		if (maxNode != null) {
			potentialSpillSet.add(maxNode);
			stack.push(maxNode);
			intGraph.markRemoved(maxNode);
		} else if (maxNodePrev != null) {
			potentialSpillSet.add(maxNodePrev);
			stack.push(maxNodePrev);
			intGraph.markRemoved(maxNodePrev);
		}
	}
}
