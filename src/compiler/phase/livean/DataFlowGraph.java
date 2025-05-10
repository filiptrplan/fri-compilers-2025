package compiler.phase.livean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import compiler.phase.asmgen.ASM;

/** The representation for the dataflow graph */
public class DataFlowGraph {
	/** Nodes are represented as instructions, edges are directional */
	// The key is the hashCode of the instruction
	private HashMap<Integer, Node> nodes = new HashMap<>();

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

}
