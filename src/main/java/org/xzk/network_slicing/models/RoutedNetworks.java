package org.xzk.network_slicing.models;

import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;

import java.util.HashMap;

public class RoutedNetworks {

    public HashMap<IpPrefix, IpAddress> networkGateway;

    public RoutedNetworks() {
        networkGateway = new HashMap<>();
    }

    public IpAddress getGateway(IpPrefix ipPrefix) {
        return this.networkGateway.get(ipPrefix);
    }

}
