package org.xzk.network_slicing;

import org.onosproject.net.DeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class VirtualNetworkGraph {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private HashMap<DeviceId, LinkedList<DeviceId>> adj;

    public VirtualNetworkGraph() {
        adj = new HashMap<>();
    }

    public void addEdge(DeviceId sourceDeviceId, DeviceId destinationDeviceId) {
        if (!adj.containsKey(sourceDeviceId)) {
            adj.put(sourceDeviceId, new LinkedList<>());
        }
        adj.get(sourceDeviceId).add(destinationDeviceId);
    }

    public ArrayList<DeviceId> bfsForShortestPath(DeviceId sourceDeviceId, DeviceId destinationDeviceId) {
        HashMap<DeviceId, Boolean> visited = new HashMap<>();
        HashMap<DeviceId, DeviceId> visitedFrom = new HashMap<>();
        ArrayList<DeviceId> shortestPathList = new ArrayList<>();

        if (sourceDeviceId.equals(destinationDeviceId)) {
            return shortestPathList;
        }

        Queue<DeviceId> queue = new LinkedList<>();
        Stack<DeviceId> pathStack = new Stack<>();

        queue.add(sourceDeviceId);
        pathStack.add(sourceDeviceId);
        visited.put(sourceDeviceId, true);

        while (!queue.isEmpty()) {
            DeviceId currentDevice = queue.poll();
            LinkedList<DeviceId> adjList = this.adj.get(currentDevice) == null ? new LinkedList<>() : this.adj.get(currentDevice);

            for (DeviceId deviceId : adjList) {
                if (!visited.containsKey(deviceId)) {
                    if (deviceId.equals(destinationDeviceId)) {
                        visitedFrom.put(deviceId, currentDevice);
                        pathStack.add(deviceId);
                        break;
                    } else {
                        queue.add(deviceId);
                        visited.put(deviceId, true);
                        pathStack.add(deviceId);

                        // testing
                        visitedFrom.put(deviceId, currentDevice);
                    }
                }
            }
        }

        // If impossible to start bfs
        if (!pathStack.contains(sourceDeviceId) || !pathStack.contains(destinationDeviceId)){
            return shortestPathList;
        }

        DeviceId currentSrc = destinationDeviceId;
        shortestPathList.add(currentSrc);
        //quick hack to fix the algorithm to make sure that the shortest path is returned
        DeviceId previousDevice;
        while(true) {
            previousDevice = visitedFrom.get(currentSrc);
            shortestPathList.add(previousDevice);
            if(previousDevice.equals(sourceDeviceId)){
                break;
            }
            currentSrc = previousDevice;
        }

        // below does not guarantee to get the shortest path
//        DeviceId node;
//        while (!pathStack.isEmpty()) {
//            node = pathStack.pop();
//
//            // if they are adjacent nodes
//            if (this.adj.get(currentSrc).contains(node) &&
//                    this.adj.get(node).contains(currentSrc)) {
//
//                shortestPathList.add(node);
//                currentSrc = node;
//
//                if (node.equals(sourceDeviceId)) {
//                    break;
//                }
//
//            }
//        }
        return shortestPathList;
    }
}
