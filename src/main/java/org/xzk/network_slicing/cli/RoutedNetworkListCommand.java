package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.xzk.network_slicing.AppComponent;
import org.xzk.network_slicing.RoutedNetworks;

import java.util.Map;

@Command(scope = "onos", name = "ns-list-routed-network",
        description = "Adds a routed network to a virtual network")
public class RoutedNetworkListCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Override
    protected void execute() {
        NetworkId _networkId = NetworkId.networkId(networkId);
        if (AppComponent.tenantRoutedNetworks.containsKey(_networkId)) {
            RoutedNetworks routedNetworks = AppComponent.tenantRoutedNetworks.get(_networkId);
            if (routedNetworks.networkGateway != null) {
                for (Map.Entry<IpPrefix, IpAddress> networks : routedNetworks.networkGateway.entrySet()) {
                    print("Network Address: " + networks.getKey().toString() + " Gateway: " + networks.getValue().toString());
                }
            }
        }
    }
}
