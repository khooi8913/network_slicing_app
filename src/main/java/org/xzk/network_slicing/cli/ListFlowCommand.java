package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;
import org.xzk.network_slicing.models.FlowRuleInformation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(scope = "onos", name = "ns-list-flow",
        description = "Deletes flow given source and destination")
public class ListFlowCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Override
    protected void execute() {

        NetworkId netId = NetworkId.networkId(networkId);
        HashMap<FlowPair, List<FlowRuleInformation>> flows = NetworkSlicing.flowRuleStorage.getAllFlowsPerNetwork(netId);

        print("========== Installed Flows (NetworkID = " + networkId + ") ==========");
        int i=1;
        for(Map.Entry<FlowPair, List<FlowRuleInformation>> f : flows.entrySet()) {
            FlowPair flowPair = f.getKey();
            print((i++) + " " + flowPair.getSrc().toString() + " --> " + flowPair.getDst().toString());
        }
    }

}
