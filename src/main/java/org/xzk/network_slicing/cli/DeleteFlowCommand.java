package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.IpAddress;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.xzk.network_slicing.AppComponent;

import java.util.ArrayList;

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

        ArrayList<FlowRule> installedFlowRules = AppComponent.tenantFlowRules.get(netId).getFlowRules(src, dst);
        if (installedFlowRules != null) {
            for(FlowRule f : installedFlowRules) {
                flowRuleService.removeFlowRules(f);
            }
        }
        AppComponent.tenantFlowRules.get(netId).deleteFlowRules(src, dst);
        print("Flow successfully removed!");
    }
}
