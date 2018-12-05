package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;

import java.util.List;

@Command(scope = "onos", name = "ns-list-forbidden-Traffic",
        description = "List forbidden flows within a specific virtual network")
public class ForbiddenTrafficListCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Override
    protected void execute() {

        NetworkId netId = NetworkId.networkId(networkId);

        List<FlowPair> flowPairs = NetworkSlicing.forbiddenTraffic.get(netId);
        if (flowPairs == null) {
            return;
        }

        print("========== Forbidden Flows (NetworkID = " + netId + ") ==========");
        int i = 0;
        for (FlowPair f : flowPairs) {
            print((++i) + " " + f.getSrc() + " " + f.getDst());
        }
    }
}
