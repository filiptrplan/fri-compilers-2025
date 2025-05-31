package compiler.phase.livean;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import compiler.common.Helper;
import compiler.common.report.Report;
import compiler.phase.memory.MEM;

/** The representation for the interference graph */
public class IntGraph {
	/**
	 * Nodes are represented as strings, edges are bidirectional.
	 * This hashmap represents any non-move edges.
	 */
	private HashMap<String, Set<String>> edges = new HashMap<>();

	/**
	 * Nodes are represented as strings, edges are bidirectional.
	 * This hashmap represents move edges.
	 */
	private HashMap<String, Set<String>> moveEdges = new HashMap<>();

	private HashMap<String, Integer> nodeDegrees = new HashMap<>();

	private Set<String> removedNodes = new HashSet<>();

	private MEM.Frame frame;

	public IntGraph(MEM.Frame frame, int K) {
		this.frame = frame;
		for (int i = 0; i < K; i++) {
			addNode(Helper.physicalRegisters.get(i));
		}
		for (int i = 0; i < K; i++) {
			for (int j = i + 1; j < K; j++) {
				addEdge(Helper.physicalRegisters.get(i), Helper.physicalRegisters.get(j), false);
			}
		}
	}

	/** Adds a node to the graph. If it already exists, does nothing */
	public void addNode(String node) {
		if (!Helper.isRegister(node) || nodeDegrees.containsKey(node)) {
			return;
		}
		nodeDegrees.put(node, 0);
		edges.put(node, new HashSet<>());
		moveEdges.put(node, new HashSet<>());
	}

	/** Gets degree of a node */
	public int degree(String node) {
		if (!nodeDegrees.containsKey(node)) {
			Report.info("Cannot retrieve: " + node);
			throw new Report.InternalError();
		}
		return nodeDegrees.get(node);
	}

	public List<String> getNodes() {
		Set<String> keySet = new HashSet<>(edges.keySet());
		keySet.addAll(moveEdges.keySet());
		keySet.removeAll(removedNodes);
		return new ArrayList<>(keySet);
	}

	/** Gets all neighbors of a node */
	public List<String> getNeighbors(String node) {
		if (!nodeDegrees.containsKey(node)) {
			Report.info("Cannot retrieve: " + node);
			throw new Report.InternalError();
		}
		Set<String> neighbors = edges.get(node);
		Set<String> moveNeighbors = moveEdges.get(node);
		Set<String> allNeighbors = new HashSet<>();
		if (neighbors == null && moveNeighbors == null) {
			return new ArrayList<>();
		}

		if (neighbors != null) {
			allNeighbors.addAll(neighbors);
		}
		if (moveNeighbors != null) {
			allNeighbors.addAll(moveNeighbors);
		}

		return new ArrayList<>(allNeighbors);
	}

	/** Whether the graph contains any temp variables */
	public boolean isTempEmpty() {
		return getTempNodes().isEmpty();
	}

	/** Whether the graph contains any physical registers or temp variables */
	public boolean isEmpty() {
		return getNodes().isEmpty();
	}

	/** Gets all nodes that are not physical registers or FP/RV */
	public List<String> getTempNodes() {
		Set<String> keySet = new HashSet<>(edges.keySet());
		keySet.addAll(moveEdges.keySet());
		keySet.removeAll(removedNodes);
		List<String> tempNodes = new ArrayList<>(keySet);
		tempNodes.removeIf(n -> !Helper.isTemp(n) || n.equals(frame.FP.toString()) || n.equals(frame.RV.toString()));
		return tempNodes;
	}

	public void markRemoved(String node) {
		if (!nodeDegrees.containsKey(node)) {
			// Report.info("Cannot retrieve (maybe not used in graph?): " + node);
			// throw new Report.InternalError();
			return;
		}
		removedNodes.add(node);
		// Update degrees for neighbors
		Set<String> neighbors = edges.get(node);
		if (neighbors != null) {
			for (String n : neighbors) {
				nodeDegrees.put(n, nodeDegrees.get(n) - 1);
			}
		}
		Set<String> moveNeighbors = moveEdges.get(node);
		if (moveNeighbors != null) {
			for (String n : moveNeighbors) {
				nodeDegrees.put(n, nodeDegrees.get(n) - 1);
			}
		}
	}

	public void unmarkRemoved(String node) {
		removedNodes.remove(node);
		// Update degrees for neighbors
		Set<String> neighbors = edges.get(node);
		if (neighbors != null) {
			for (String n : neighbors) {
				nodeDegrees.put(n, nodeDegrees.get(n) + 1);
			}
		}
		Set<String> moveNeighbors = moveEdges.get(node);
		if (moveNeighbors != null) {
			for (String n : moveNeighbors) {
				nodeDegrees.put(n, nodeDegrees.get(n) + 1);
			}
		}
	}

	/** Adds an edge between nodes t1 and t2 */
	public void addEdge(String t1, String t2, boolean isMove) {
		if (!Helper.isRegister(t1) || !Helper.isRegister(t2)) {
			return;
		}
		HashMap<String, Set<String>> edgesToUse = isMove ? moveEdges : edges;
		Set<String> e1 = edgesToUse.get(t1);
		Set<String> e2 = edgesToUse.get(t2);
		if (!nodeDegrees.containsKey(t1)) {
			nodeDegrees.put(t1, 0);
		}
		if (e1 == null) {
			e1 = new HashSet<>();
			edgesToUse.put(t1, e1);
		}
		if (!nodeDegrees.containsKey(t2)) {
			nodeDegrees.put(t2, 0);
		}
		if (e2 == null) {
			e2 = new HashSet<>();
			edgesToUse.put(t2, e2);
		}
		e1.add(t2);
		e2.add(t1);
		nodeDegrees.put(t1, nodeDegrees.get(t1) + 1);
		nodeDegrees.put(t2, nodeDegrees.get(t2) + 1);
	}

	/** Removes edges between t1 and t2. Returns false if edge not found */
	public boolean removeEdge(String t1, String t2, boolean isMove) {
		HashMap<String, Set<String>> edgesToUse = isMove ? moveEdges : edges;
		Set<String> e1 = edgesToUse.get(t1);
		Set<String> e2 = edgesToUse.get(t2);
		if (e1 == null) {
			return false;
		}
		if (e2 == null) {
			return false;
		}

		nodeDegrees.put(t1, nodeDegrees.get(t1) - 1);
		nodeDegrees.put(t2, nodeDegrees.get(t2) - 1);

		return e1.remove(t2) && e2.remove(t1);
	}

	/**
	 * Generates a Mermaid graph representation of the interference graph.
	 * Nodes are represented as circles, with physical registers (x0-x31) having
	 * double borders.
	 */
	public void printToMermaidFile(String filename) {
		StringBuilder sb = new StringBuilder();
		sb.append("graph TD\n");

		for (String node : edges.keySet()) {
			if (Helper.isPhysicalRegister(node)) {
				sb.append("    ").append(node).append("(((").append(node).append(")))\n");
			} else {
				sb.append("    ").append(node).append("((").append(node).append("))\n");
			}
		}

		Set<String> processedEdges = new HashSet<>();
		for (String node : edges.keySet()) {
			for (String neighbor : edges.get(node)) {
				String edgeId = node.compareTo(neighbor) < 0 ? node + "-" + neighbor : neighbor + "-" + node;

				if (!processedEdges.contains(edgeId)) {
					sb.append("    ").append(node).append(" --- ").append(neighbor).append("\n");
					processedEdges.add(edgeId);
				}
			}
		}

		try (FileWriter writer = new FileWriter(filename)) {
			writer.write("```mermaid\n");
			writer.write(sb.toString());
			writer.write("```\n");
		} catch (IOException e) {
			Report.warning("Failed to write Mermaid graph to file: " + e.getMessage());
		}
	}
}
