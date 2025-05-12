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
}
