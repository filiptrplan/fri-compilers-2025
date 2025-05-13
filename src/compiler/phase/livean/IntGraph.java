package compiler.phase.livean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The representation for the interference graph */
public class IntGraph {
	/** Nodes are represented as strings, edges are bidirectional */
	private HashMap<String, Set<String>> edges = new HashMap<>();

	public IntGraph() {
		// Add all the physical registers to the graph
		for (int i = 0; i < 32; i++) {
			for (int j = i + 1; j < 32; j++) {
				addEdge("x" + i, "x" + j);
			}
		}
	}

	/** Adds an edge between nodes t1 and t2 */
	public void addEdge(String t1, String t2) {
		Set<String> e1 = edges.get(t1);
		Set<String> e2 = edges.get(t2);
		if (e1 == null) {
			e1 = new HashSet<>();
			edges.put(t1, e1);
		}
		if (e2 == null) {
			e2 = new HashSet<>();
			edges.put(t2, e2);
		}
		e1.add(t2);
		e2.add(t1);
	}

	/** Removes edges between t1 and t2. Returns false if edge not found */
	public boolean removeEdge(String t1, String t2) {
		Set<String> e1 = edges.get(t1);
		Set<String> e2 = edges.get(t2);
		if (e1 == null) {
			return false;
		}
		if (e2 == null) {
			return false;
		}

		return e1.remove(t2) && e2.remove(t1);
	}
	
	/**
	 * Generates a Mermaid graph representation of the interference graph.
	 * Nodes are represented as circles, with physical registers (x0-x31) having double borders.
	 * 
	 * @return A string containing the Mermaid graph definition
	 */
	public String printToMermaid() {
		StringBuilder sb = new StringBuilder();
		sb.append("graph TD\n");
		
		// Define all nodes as circles, with physical registers having a different style
		for (String node : edges.keySet()) {
			if (isPhysicalRegister(node)) {
				// Physical registers with double border
				sb.append("    ").append(node).append>("((").append(node).append("))\"\"]\n");
			} else {
				// Regular nodes as simple circles
				sb.append("    ").append(node).append("((").append(node).append("))\n");
			}
		}
		
		// Define all edges with no duplicates
		Set<String> processedEdges = new HashSet<>();
		for (String node : edges.keySet()) {
			for (String neighbor : edges.get(node)) {
				// Create a unique edge identifier to avoid duplicates
				String edgeId = node.compareTo(neighbor) < 0 ? 
						node + "-" + neighbor : neighbor + "-" + node;
				
				if (!processedEdges.contains(edgeId)) {
					sb.append("    ").append(node).append(" --- ").append(neighbor).append("\n");
					processedEdges.add(edgeId);
				}
			}
		}
		
		return sb.toString();
	}
	
	/**
	 * Checks if a node name represents a physical register (x0-x31)
	 * 
	 * @param nodeName The name of the node to check
	 * @return true if the node is a physical register, false otherwise
	 */
	private boolean isPhysicalRegister(String nodeName) {
		if (nodeName.length() < 2 || nodeName.charAt(0) != 'x') {
			return false;
		}
		
		try {
			int regNum = Integer.parseInt(nodeName.substring(1));
			return regNum >= 0 && regNum <= 31;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
