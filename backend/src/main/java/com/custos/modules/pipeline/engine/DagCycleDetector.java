package com.custos.modules.pipeline.engine;

import com.custos.modules.pipeline.entity.PipelineStepNode;
import com.custos.modules.pipeline.exception.CyclicDependencyException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DagCycleDetector {

    /**
     * Validates a list of pipeline step nodes for circular dependencies.
     * Throws CyclicDependencyException if a cycle is found.
     */
    public void validateNodes(List<PipelineStepNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        Map<UUID, List<UUID>> adjacencyList = new HashMap<>();
        Map<UUID, String> nodeLabels = new HashMap<>();

        for (PipelineStepNode node : nodes) {
            UUID id = node.getId();
            nodeLabels.put(id, node.getNodeLabel() != null ? node.getNodeLabel() : node.getNodeKey());
            adjacencyList.putIfAbsent(id, new ArrayList<>());

            if (node.getOnSuccessNodeId() != null) {
                adjacencyList.get(id).add(node.getOnSuccessNodeId());
            }
            if (node.getOnFailureNodeId() != null) {
                adjacencyList.get(id).add(node.getOnFailureNodeId());
            }
        }

        validateGraph(adjacencyList, nodeLabels);
    }

    /**
     * Validates an adjacency list graph representation for cycles.
     */
    public void validateGraph(Map<UUID, List<UUID>> adjacencyList, Map<UUID, String> nodeLabels) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> recursionStack = new HashSet<>();
        List<UUID> path = new ArrayList<>();

        for (UUID node : adjacencyList.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycleDfs(node, adjacencyList, visited, recursionStack, path, nodeLabels)) {
                    // CyclicDependencyException thrown inside hasCycleDfs
                }
            }
        }
    }

    private boolean hasCycleDfs(UUID current,
                                Map<UUID, List<UUID>> adjacencyList,
                                Set<UUID> visited,
                                Set<UUID> recursionStack,
                                List<UUID> path,
                                Map<UUID, String> nodeLabels) {
        visited.add(current);
        recursionStack.add(current);
        path.add(current);

        List<UUID> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());
        for (UUID neighbor : neighbors) {
            // Ignore self-references or dangling references if neighbor is not in the graph
            if (!adjacencyList.containsKey(neighbor)) {
                continue;
            }

            if (recursionStack.contains(neighbor)) {
                // Cycle detected!
                path.add(neighbor);
                int cycleStartIdx = path.indexOf(neighbor);
                List<String> cycleLabels = new ArrayList<>();
                for (int i = cycleStartIdx; i < path.size(); i++) {
                    UUID id = path.get(i);
                    cycleLabels.add(nodeLabels.getOrDefault(id, id.toString()));
                }
                String cyclePathStr = String.join(" -> ", cycleLabels);
                throw new CyclicDependencyException("Cycle detected in pipeline graph: " + cyclePathStr);
            }

            if (!visited.contains(neighbor)) {
                if (hasCycleDfs(neighbor, adjacencyList, visited, recursionStack, path, nodeLabels)) {
                    return true;
                }
            }
        }

        recursionStack.remove(current);
        path.remove(path.size() - 1);
        return false;
    }
}
