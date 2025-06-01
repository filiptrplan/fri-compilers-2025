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
		if (K > Helper.physicalRegisters.size()) {
			throw new Report.InternalError();
		}
		this.codeChunk = codeChunk;
		this.intGraph = LiveAn.intGraphs.get(codeChunk);
		if (previouslySpilled != null) {
			this.previouslySpilled = previouslySpilled;
		}
		tempVariableSize = codeChunk.tempSize;
	}

	// public static int c = 0;

	/**
	 * Assigns registers to the code chunk. Saves the result in the code chunk.
	 **/
	public void assignRegisters() {
		// We do the simplify and spil phases until the graph is empty of temp variables
		while (!intGraph.isTempEmpty()) {
			simplify();
			spill();
		}

		// Then we remove the FP and SP
		intGraph.markRemoved("SP");
		intGraph.markRemoved(codeChunk.frame.FP.toString());
		// We push them to the stack last to make sure they get colored first
		stack.push("SP");
		stack.push(codeChunk.frame.FP.toString());

		// Then we do the select phase
		Map<String, Integer> colorMap = select();

		// We check if we have any actual spills
		if (!actualSpillSet.isEmpty()) {
			codeChunk.instructions = handleActualSpills();
			codeChunk.tempSize = tempVariableSize;
			// if (c > 10) {
			// throw new Report.InternalError();
			// }
			// c += 1;

			LiveAn.calculateInterferenceGraph(codeChunk);
			// Aggregate the previously spilled registers
			previouslySpilled.addAll(actualSpillSet);
			RegAll.assignRegisters(codeChunk, previouslySpilled);
		} else {
			String[] regs = new String[K];
			// First we assign the registers to the physical ones
			for (int i = 0; i < K; i++) {
				int color = colorMap.get(Helper.physicalRegisters.get(i));
				regs[color] = Helper.physicalRegisters.get(i);
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
						} else if (def.equals("x1")) {
							continue;
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
	}

	/**
	 * Returns a new vector of instructions with actual spills transformed to loads
	 * and stores
	 */
	private Vector<ASM.Instr> handleActualSpills() {
		// Spill map maps a spilled temp variable to its offset from the frame pointer
		HashMap<String, Long> spillMap = new HashMap<>();

		// We first define the offsets
		for (var def : actualSpillSet) {
			if (!spillMap.containsKey(def)) {
				// Caclulate the offset of the temp variable
				// We add a +24 because the stack grows downwards and we saved FP and RA too
				long fpOffset = codeChunk.frame.locsSize + tempVariableSize + 24;
				// Increment the offset of the next temp variable
				tempVariableSize += 8;
				spillMap.put(def, fpOffset);
			}
		}

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
						long fpOffset = spillMap.get(def);
						// Save the temp variable to the location
						String operStr = "sd #1, " + (-fpOffset) + "(#2)";
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
						String operStr = "ld $1, " + (-fpOffset) + "(#1)";
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
	 * First it precolors all the physical registers
	 *
	 * Colors the graph of the code chunk by popping nodes from the stack and
	 * assigning them a color.
	 *
	 * Also modifies the potential spill set and actual spill set.
	 **/
	private Map<String, Integer> select() {
		Map<String, Integer> colorMap = new HashMap<>();

		int i = 0;
		for (String reg : intGraph.getNodes()) {
			if (i >= K) {
				Report.warning("Too many physical registers:");
				Report.warning(intGraph.getNodes().toString());
				throw new Report.InternalError();
			}
			if (!Helper.isPhysicalRegister(reg)) {
				Report.warning(reg + " is not a physical register.");
				Report.warning("At this point all the temp registers should be removed and only physical left");
				throw new Report.InternalError();
			}
			colorMap.put(reg, i);
			i += 1;
		}

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
		// Maximum degree node of not spilled
		String maxNode = null;
		int maxDegreePrev = 0;
		// Maximum degree node of already spilled
		String maxNodePrev = null;

		for (String node : intGraph.getTempNodes()) {
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
