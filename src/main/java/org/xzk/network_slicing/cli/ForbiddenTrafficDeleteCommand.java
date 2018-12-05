package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;

import java.util.NoSuchElementException;

@Command(scope = "onos", name = "ns-delete-forbidden-Traffic",
        description = "Deletes a forbidden flow within a specific virtual network")
public class ForbiddenTrafficDeleteCommand extends AbstractShellCommand {

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

        try {
            if (NetworkSlicing.forbiddenTraffic.containsKey(netId)) {
                NetworkSlicing.forbiddenTraffic.get(netId).remove(flowPair1);
                NetworkSlicing.forbiddenTraffic.get(netId).remove(flowPair2);
                print("Forbidden traffic removed!");
            } else {
                print("Forbidden traffic does not exists!");
            }
        } catch (NoSuchElementException e) {
            print("Forbidden traffic does not exists!");
        }

    }
}
