package compiler.phase.livean;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import compiler.common.Helper;
import compiler.common.report.Report;
import compiler.phase.asmgen.ASM;
import compiler.phase.asmgen.Tiles;
import compiler.phase.memory.MEM;

/** The representation for the dataflow graph */
public class DataFlowGraph {
	/** Nodes are represented as instructions, edges are directional */
	// The key is the hashCode of the instruction
	private HashMap<Integer, Node> nodes = new HashMap<>();

	/**
	 * Returns a list on quasi-topological order (as the graph is not acyclic)
	 * The list is reversed
	 */
	public List<Node> quasiTopoSortNodes(ASM.Instr first) {
		ArrayList<Node> sorted = new ArrayList<>();
		Stack<Node> stack = new Stack<>();
		if (!nodes.containsKey(first.hashCode())) {
			throw new Report.InternalError();
		}
		stack.push(this.getNode(first));
		HashSet<Integer> visited = new HashSet<>();

		while (!stack.isEmpty()) {
			Node top = stack.pop();
			if (visited.contains(top.hashCode())) {
				continue;
			}
			for (Node out : top.outNodes()) {
				if (!visited.contains(out.hashCode())) {
					stack.push(out);
				}
			}
			sorted.add(top);
			visited.add(top.hashCode());
		}
		return sorted.reversed();
	}

	/** Sets the initial active register before function start */
	public void seedInitialRegs(ASM.CodeInstrChunk chunk, List<String> regs) {
		ASM.Instr first = null;
		for (ASM.Instr instr : chunk.instructions) {
			if (instr instanceof ASM.Label) {
				continue;
			}
			first = instr;
			break;
		}
		Node firstNode = getNode(first);
		firstNode.initInTemp.addAll(regs);
	}

	/** Sets the final active registers after function end */
	public void seedFinalRegs(ASM.CodeInstrChunk chunk, List<String> regs) {
		String funcEndLabel = chunk.exitLabel.name;
		for (ASM.Instr instr : chunk.instructions) {
			if (instr instanceof ASM.Oper oper) {
				// We set the out temp for all the jumps that jump to the epilogue
				if ((oper.isJump || oper.isCjump) && oper.jumpTarget.equals(funcEndLabel)) {
					Node node = getNode(oper);
					node.initOutTemp.addAll(regs);
					System.out.println(oper.toString());
				}
			}
		}
	}

	class Node {
		private Set<Node> outEdges = new HashSet<>();
		private Set<Node> inEdges = new HashSet<>();

		/** Set of temp variables that are active upon entering the node */
		public Set<String> inTemp = new HashSet<>();
		/** Set of temp variables that are active upon exiting the node */
		public Set<String> outTemp = new HashSet<>();

		/**
		 * These two variables store the constant portion of the inTemp and outTemp
		 * sets.
		 * Used for specifying fixed constraints on the liveness of some variables.
		 * They are always parts of the sets
		 */
		public Set<String> initInTemp = new HashSet<>();
		public Set<String> initOutTemp = new HashSet<>();

		public void resetInTemp() {
			inTemp = new HashSet<>(initInTemp);
		}

		public void resetOutTemp() {
			outTemp = new HashSet<>(initOutTemp);
		}

		public Set<String> useTemp = new HashSet<>();
		public Set<String> defTemp = new HashSet<>();

		public final ASM.Oper instr;

		@Override
		public int hashCode() {
			return instr.hashCode();
		}

		public Node(ASM.Oper instr) {
			this.instr = instr;
			if (instr.useOps != null) {
				for (String useOp : instr.useOps) {
					// Add only the temp variables, not labels
					if (Helper.isRegister(useOp)) {
						this.useTemp.add(useOp);
					}
				}
			}
			if (instr.defOps != null) {
				for (String defOp : instr.defOps) {
					if (Helper.isRegister(defOp)) {
						this.defTemp.add(defOp);
					}
				}
			}
		}

		/** Add edge from this node to `to` */
		public void addEdge(Node to) {
			this.outEdges.add(to);
			to.inEdges.add(this);
		}

		public void removeEdge(Node to) {
			this.outEdges.remove(to);
			to.inEdges.remove(this);
		}

		public List<Node> outNodes() {
			return new ArrayList<>(outEdges);
		}

		public List<Node> inNodes() {
			return new ArrayList<>(inEdges);
		}

		/** Returns a unique hash representing the inTemp and outTemp sets */
		public int inOutHash() {
			final int prime = 31;
			int hash = 17;

			List<String> sortedInTemp = new ArrayList<>(inTemp);
			sortedInTemp.sort(String::compareTo);
			for (String element : sortedInTemp) {
				hash = hash * prime + (element == null ? 0 : element.hashCode());
			}

			hash = hash * prime + 42;

			List<String> sortedOutTemp = new ArrayList<>(outTemp);
			sortedOutTemp.sort(String::compareTo);
			for (String element : sortedOutTemp) {
				hash = hash * prime + (element == null ? 0 : element.hashCode());
			}

			return hash;
		}
	}

	public void addNode(ASM.Oper instr) {
		nodes.put(instr.hashCode(), new Node(instr));
	}

	public Node getNode(ASM.Instr instr) {
		return nodes.get(instr.hashCode());
	}

	public void removeNode(Node node) {
		for (Node inNode : node.inNodes()) {
			inNode.removeEdge(node);
		}

		for (Node outNode : node.outNodes()) {
			node.removeEdge(outNode);
		}

		nodes.remove(node.hashCode());
	}

	public List<Node> getNodes() {
		return new ArrayList<>(nodes.values());
	}

	/**
	 * Prints the graph as a mermaid diagram to a file.
	 * 
	 * @param filename The name of the file to write the diagram to.
	 */
	public void printToMermaidFile(String filename) {
		try (FileWriter writer = new FileWriter(filename)) {
			writer.write("```mermaid\n");
			writer.write("flowchart TD\n");

			// Define all nodes
			for (Node node : nodes.values()) {
				String nodeId = "node" + node.hashCode();
				String shape = (node.instr instanceof ASM.Oper &&
						(((ASM.Oper) node.instr).isJump || ((ASM.Oper) node.instr).isCjump))
								? "diamond"
								: "box";

				// Clean the instruction string for mermaid compatibility
				String instrText = node.instr.toString()
						.replace("\"", "'")
						.replace("\n", "<br>")
						.replace("\t", "  ");
				String leftDelim = shape.equals("diamond") ? "{" : "[";
				String rightDelim = shape.equals("diamond") ? "}" : "]";
				writer.write("    " + nodeId + leftDelim + "\"" + instrText + "\"" + rightDelim + "\n");
			}

			// Define all edges
			for (Node node : nodes.values()) {
				String nodeId = "node" + node.hashCode();

				for (Node outNode : node.outNodes()) {
					String outNodeId = "node" + outNode.hashCode();

					// Format the temp variables for display
					String tempList = String.join(",", node.outTemp);
					String tempListIn = String.join(",", outNode.inTemp);
					String outString = tempList.isEmpty() ? "" : "out: " + tempList;
					String inString = tempListIn.isEmpty() ? "" : "in: " + tempListIn;
					writer.write(
							"    " + nodeId + " -->|\"" + outString + " " + inString + "\"|" + outNodeId
									+ "\n");
				}
			}

			writer.write("```\n");
		} catch (IOException e) {
			Report.warning("Failed to write mermaid diagram to file: " + e.getMessage());
		}
	}

	/**
	 * Prints assembly instructions with their corresponding outTemp sets
	 * for instructions that exist in the dataflow graph.
	 */
	public void printAsmWithDfg(ASM.CodeInstrChunk chunk) {
		Report.info("#------- " + chunk.frame.label.name + " (FP=" + chunk.frame.FP + ", RV=" + chunk.frame.RV
				+ ", body=" + chunk.entryLabel.name + ", exit=" + chunk.exitLabel.name + ") ------- ");

		for (ASM.Instr instr : chunk.instructions) {
			System.out.println(instr.toString());

			if (instr instanceof ASM.Oper) {
				Node node = getNode(instr);
				if (node != null && !node.outTemp.isEmpty()) {
					System.out.println("\t\t// out: " + String.join(", ", node.outTemp));
				}
			}
		}
		System.out.println();
	}
}
