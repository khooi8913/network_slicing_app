package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowRuleService;
import org.xzk.network_slicing.NetworkSlicing;
import org.xzk.network_slicing.models.FlowPair;
import org.xzk.network_slicing.models.FlowRuleInformation;

import java.util.List;

@Command(scope = "onos", name = "ns-delete-flow",
        description = "Deletes flow given source and destination")
public class DeleteFlowCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Argument(index = 1, name = "srcIp", description = "Device ID",
            required = true, multiValued = false)
    String srcIp = null;

    @Argument(index = 2, name = "dstIp", description = "Device ID",
            required = true, multiValued = false)
    String dstIp = null;

    @Override
    protected void execute() {
        FlowRuleService flowRuleService = getService(FlowRuleService.class);
        NetworkId netId = NetworkId.networkId(networkId);
        IpAddress src = IpAddress.valueOf(srcIp);
        IpAddress dst = IpAddress.valueOf(dstIp);

        FlowPair flowPair = new FlowPair(src, dst);
        List<FlowRuleInformation> flowRules = NetworkSlicing.flowRuleStorage.getFlowRules(netId, flowPair);
        for(FlowRuleInformation f : flowRules) {
            flowRuleService.removeFlowRules(f.getFlowRule());

            DeviceId currentDeviceId = f.getFlowRuleDeviceId();
            // Return MPLS label if any
            if(f.getMplsLabel() != null) {
                NetworkSlicing.mplsLabelPool.get(currentDeviceId).returnLabel(f.getMplsLabel().toInt());
            }
        }

        NetworkSlicing.flowRuleStorage.deleteFlowRules(netId, flowPair);
        print("Flow successfully removed!");
    }
}
