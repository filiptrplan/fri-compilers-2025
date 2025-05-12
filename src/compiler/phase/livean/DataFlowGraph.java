package compiler.phase.livean;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import compiler.common.report.Report;
import compiler.phase.asmgen.ASM;

/** The representation for the dataflow graph */
public class DataFlowGraph {
	/** Nodes are represented as instructions, edges are directional */
	// The key is the hashCode of the instruction
	private HashMap<Integer, Node> nodes = new HashMap<>();

	/** Returns a list on quasi-topological order (as the graph is not acyclic) */
	public ArrayList<Node> quasiTopoSortNodes(ASM.Instr first) {
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
				stack.push(out);
			}
			sorted.add(top);
		}
		return sorted;
	}

	class Node {
		private Set<Node> outEdges = new HashSet<>();
		private Set<Node> inEdges = new HashSet<>();

		/** Set of temp variables that are active upon entering the node */
		public Set<String> inTemp = new HashSet<>();
		/** Set of temp variables that are active upon exiting the node */
		public Set<String> outTemp = new HashSet<>();

		private final ASM.Instr instr;

		@Override
		public int hashCode() {
			return instr.hashCode();
		}

		public Node(ASM.Instr instr) {
			this.instr = instr;
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
	}

	public void addNode(ASM.Instr instr) {
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
					writer.write("    " + nodeId + " --> " + outNodeId + "\n");
				}
			}

			writer.write("```\n");
		} catch (IOException e) {
			Report.warning("Failed to write mermaid diagram to file: " + e.getMessage());
		}
	}
}
