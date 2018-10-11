package org.xzk.network_slicing;

import org.onlab.packet.MplsLabel;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.net.HostId;

import java.util.HashMap;

public class MplsForwardingTable {

    private HashMap<NetworkId, HashMap<HostId, MplsLabel>> mplsForwardingTable;

    public MplsForwardingTable() {
        mplsForwardingTable = new HashMap<>();
    }

    public void addLabelToHost (NetworkId networkId, HostId hostId, MplsLabel mplsLabel) {
        if(!this.mplsForwardingTable.containsKey(networkId)) {
            this.mplsForwardingTable.put(networkId, new HashMap<>());
        }

        this.mplsForwardingTable.get(networkId).put(hostId, mplsLabel);
    }

    public MplsLabel getMplsLabel (NetworkId networkId, HostId hostId) {
        return this.mplsForwardingTable.containsKey(networkId) ?
               this.mplsForwardingTable.get(networkId).containsKey(hostId)?
                       this.mplsForwardingTable.get(networkId).get(hostId) :
                       null :
                null;
    }
}
