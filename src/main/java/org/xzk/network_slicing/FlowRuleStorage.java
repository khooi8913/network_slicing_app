package org.xzk.network_slicing;

import org.onlab.packet.MplsLabel;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.net.flow.FlowRule;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class FlowRuleStorage {

    private HashMap<NetworkId, HashMap<FlowPair, List<FlowRuleInformation>>> flowRuleStorage = new HashMap<>();

    public FlowRuleStorage() {
        this.flowRuleStorage = new HashMap<>();
    }

    public void addFlowRule(NetworkId networkId, FlowPair flowPair, FlowRule flowRule, MplsLabel mplsLabel) {
        if (!flowRuleStorage.containsKey(networkId)) flowRuleStorage.put(networkId, new HashMap<>());
        if (!flowRuleStorage.get(networkId).containsKey(flowPair))
            flowRuleStorage.get(networkId).put(flowPair, new LinkedList<>());

        FlowRuleInformation flowRuleInformation = new FlowRuleInformation(flowRule, mplsLabel);
        flowRuleStorage.get(networkId).get(flowPair).add(flowRuleInformation);
    }

    public void deleteFlowRules(NetworkId networkId, FlowPair flowPair) {
        flowRuleStorage.get(networkId).remove(flowPair);
    }

    public List<FlowRuleInformation> getFlowRules(NetworkId networkId, FlowPair flowPair) {
        return this.flowRuleStorage.get(networkId).get(flowPair);
    }

    public HashMap<FlowPair, List<FlowRuleInformation>> getAllFlowsPerNetwork(NetworkId networkId) {
        return this.flowRuleStorage.get(networkId);
    }

    public HashMap<NetworkId, HashMap<FlowPair, List<FlowRuleInformation>>> getAllFlows() {
        return this.flowRuleStorage;
    }

}
