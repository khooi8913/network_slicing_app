package org.xzk.network_slicing;

import org.onosproject.net.DeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class VirtualNetworkGraph {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private int V;
    private HashMap<DeviceId, LinkedList<DeviceId>> adj;

    public VirtualNetworkGraph(int V) {
        this.V = V;
        adj = new HashMap<>();
    }

    public void addEdge(DeviceId sourceDeviceId, DeviceId destinationDeviceId) {
        if (!adj.containsKey(sourceDeviceId)) {
            adj.put(sourceDeviceId, new LinkedList<>());
        }
        adj.get(sourceDeviceId).add(destinationDeviceId);
    }

    public ArrayList<DeviceId> bfsForShortestPath (DeviceId sourceDeviceId, DeviceId destinationDeviceId) {
        HashMap<DeviceId, Boolean> visited = new HashMap<>();
        ArrayList<DeviceId> shortestPathList = new ArrayList<>();

        if(sourceDeviceId.equals(destinationDeviceId)) {
            return shortestPathList;
        }

        Queue<DeviceId> queue = new LinkedList<>();
        Stack<DeviceId> pathStack = new Stack<>();

        queue.add(sourceDeviceId);
        pathStack.add(sourceDeviceId);
        visited.put(sourceDeviceId, true);

        while(!queue.isEmpty()) {
            DeviceId currentDevice = queue.poll();
            LinkedList<DeviceId> adjList = this.adj.get(currentDevice);

            for(DeviceId deviceId : adjList) {
                if(!visited.containsKey(deviceId)) {
                    if(deviceId.equals(destinationDeviceId)){
                        pathStack.add(deviceId);
                        break;
                    }else{
                        queue.add(deviceId);
                        visited.put(deviceId, true);
                        pathStack.add(deviceId);
                    }
                }
            }
        }

        log.info(queue.toString());
        log.info(pathStack.toString());

        // TODO: Something wrong there.......
        // What is the problem with this implementation?

        DeviceId node;
        DeviceId currentSrc = destinationDeviceId;
        shortestPathList.add(destinationDeviceId);
        while(!pathStack.isEmpty()) {
            node = pathStack.pop();
            if(this.adj.get(currentSrc).contains(node) &&
                    this.adj.get(node).contains(currentSrc)) {

                shortestPathList.add(node);
                currentSrc = node;

                if(node.equals(sourceDeviceId)) {
                    break;
                }

            }
        }
        return shortestPathList;
    }
}
