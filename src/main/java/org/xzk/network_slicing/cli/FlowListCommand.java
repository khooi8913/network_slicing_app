package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.net.DeviceId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;
import org.xzk.network_slicing.models.FlowRuleInformation;

import java.util.*;

@Command(scope = "onos", name = "ns-list-flow",
        description = "Lists flows installed for a specified virtual network")
public class FlowListCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Override
    protected void execute() {

        NetworkId netId = NetworkId.networkId(networkId);
        HashMap<FlowPair, List<FlowRuleInformation>> flows = NetworkSlicing.flowRuleStorage.getAllFlowsPerNetwork(netId);

        print("========== Installed Flows (NetworkID = " + networkId + ") ==========");

        StringBuilder sb;

        if (flows == null) {
            return;
        }

        int i = 0;
        for (Map.Entry<FlowPair, List<FlowRuleInformation>> f : flows.entrySet()) {

            int numOfFlows = flows.size();
            List<DeviceId> pathTaken = new LinkedList<>();

            sb = new StringBuilder();

            FlowPair flowPair = f.getKey();

            sb.append((++i) + " " + flowPair.getSrc().toString() + " --> " + flowPair.getDst().toString() + "\n");
            sb.append("Path Taken: ");

            for (FlowRuleInformation g : f.getValue()) {
                pathTaken.add(g.getFlowRuleDeviceId());
            }
            Collections.reverse(pathTaken);

            int j = 0;
            for (DeviceId d : pathTaken) {
                j++;
                sb.append(d.toString());
                if (j != pathTaken.size()) sb.append(" ");
            }

            if (i != numOfFlows) {
                sb.append("\n");
            }
            print(sb.toString());
        }
    }

}
