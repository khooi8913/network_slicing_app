package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.RoutedNetworks;

@Command(scope = "onos", name = "ns-add-routed-network",
        description = "Adds a routed network to a virtual network")
public class RoutedNetworkAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Argument(index = 1, name = "networkCidr", description = "Routed Network in CIDR Notation",
            required = true, multiValued = false)
    String networkCidr = null;

    @Argument(index = 2, name = "networkGateway", description = "Routed Network's Gateway Address",
            required = true, multiValued = false)
    String networkGateway = null;

    @Override
    protected void execute() {
        NetworkId _networkId = NetworkId.networkId(networkId);
        IpPrefix routedNetworkAddress = null;
        IpAddress gatewayAddress = null;

        try {
            routedNetworkAddress = IpPrefix.valueOf(networkCidr);
            gatewayAddress = IpAddress.valueOf(networkGateway);
        } catch (IllegalArgumentException e) {
            error(e.toString());
        }

        if (routedNetworkAddress != null && gatewayAddress != null) {
            if (routedNetworkAddress.contains(gatewayAddress)) {
                if (!NetworkSlicing.tenantRoutedNetworks.containsKey(_networkId)) {
                    NetworkSlicing.tenantRoutedNetworks.put(_networkId, new RoutedNetworks());
                }
                NetworkSlicing.tenantRoutedNetworks.get(_networkId).networkGateway.put(routedNetworkAddress, gatewayAddress);
                print("Routed network entry added successfully!");
            } else {
                print("Gateway does not belong to the specified network!");
            }
        }
    }
}
