package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;

import java.util.LinkedList;

@Command(scope = "onos", name = "ns-add-forbidden-Traffic",
        description = "Adds a forbidden flow to be blocked")
public class ForbiddenTrafficAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Argument(index = 1, name = "host1", description = "Host1",
            required = true, multiValued = false)
    String host1Ip = null;

    @Argument(index = 2, name = "host2", description = "Host2",
            required = true, multiValued = false)
    String host2Ip = null;

    @Override
    protected void execute() {

        NetworkId netId = NetworkId.networkId(networkId);
        IpAddress host1 = IpAddress.valueOf(host1Ip);
        IpAddress host2 = IpAddress.valueOf(host2Ip);

        FlowPair flowPair1 = new FlowPair(host1, host2);
        FlowPair flowPair2 = new FlowPair(host2, host1);

        if(!NetworkSlicing.forbiddenTraffic.containsKey(netId)){
            NetworkSlicing.forbiddenTraffic.put(netId, new LinkedList<>());
        }
        NetworkSlicing.forbiddenTraffic.get(netId).add(flowPair1);
        NetworkSlicing.forbiddenTraffic.get(netId).add(flowPair2);

        print("Forbidden traffic entry added successfully!");

        try{
            NetworkSlicing.flowRuleStorage.deleteFlowRules(netId, flowPair1);
            NetworkSlicing.flowRuleStorage.deleteFlowRules(netId, flowPair2);
        } catch (NullPointerException e) {
            // Do nothing
        }
    }
}
