package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.TenantId;
import org.onosproject.incubator.net.virtual.VirtualNetwork;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;

@Command(scope = "onos", name = "ns-create-virtual-network",
        description = "Creates a new virtual network for the specified tenant")
public class VirtualNetworkCreateCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "tenantId", description = "Tenant ID",
            required = true, multiValued = false)
    String tenantId = null;

    @Override
    protected void execute() {
        // TODO: Input verification

        VirtualNetworkAdminService virtualNetworkAdminService = getService(VirtualNetworkAdminService.class);
        VirtualNetwork virtualNetwork = virtualNetworkAdminService.createVirtualNetwork(TenantId.tenantId(tenantId));
        print("Virtual network (ID=" + virtualNetwork.id().toString() + ") is successfully created for tenant " + tenantId);
    }
}
