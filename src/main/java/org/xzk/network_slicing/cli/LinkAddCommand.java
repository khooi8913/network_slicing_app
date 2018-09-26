package org.xzk.network_slicing.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.DefaultVirtualDevice;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualDevice;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.PortNumber;
import org.onosproject.net.link.LinkService;

import java.util.Set;

@Command(scope = "onos", name = "ns-add-link",
        description = "Adds a link between devices to a virtual network")
public class LinkAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Argument(index = 1, name = "srcDeviceId", description = "Source device ID",
            required = true, multiValued = false)
    String srcDeviceId = null;

    @Argument(index = 2, name = "srcPortNum", description = "Source port number",
            required = true, multiValued = false)
    Integer srcPortNum = null;

    @Argument(index = 3, name = "dstDeviceId", description = "Destination device ID",
            required = true, multiValued = false)
    String dstDeviceId = null;

    @Argument(index = 4, name = "dstPortNum", description = "Destination port number",
            required = true, multiValued = false)
    Integer dstPortNum = null;

    @Override
    protected void execute() {

        VirtualNetworkAdminService virtualNetworkAdminService = getService(VirtualNetworkAdminService.class);
        LinkService linkService = getService(LinkService.class);

        if (isDevicesValid(virtualNetworkAdminService)) {
            ConnectPoint src = new ConnectPoint(DeviceId.deviceId(srcDeviceId), PortNumber.portNumber(srcPortNum));
            ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(dstDeviceId), PortNumber.portNumber(dstPortNum));

            if(isLinksValid(linkService, src, dst)) {
                // Add port
                
                virtualNetworkAdminService.createVirtualLink(NetworkId.networkId(networkId), src, dst);
                virtualNetworkAdminService.createVirtualLink(NetworkId.networkId(networkId), dst, src);
                print("Link added!");
            } else{
                print("Link does not exists!");
            }

        } else {
            print("Invalid/ non-registered devices specified!");
        }
    }

    private boolean isDevicesValid(VirtualNetworkAdminService virtualNetworkAdminService) {
        Set<VirtualDevice> registeredDevices = virtualNetworkAdminService.getVirtualDevices(NetworkId.networkId(networkId));

        DefaultVirtualDevice srcDevice = new DefaultVirtualDevice(NetworkId.networkId(networkId), DeviceId.deviceId(srcDeviceId));
        DefaultVirtualDevice dstDevice = new DefaultVirtualDevice(NetworkId.networkId(networkId), DeviceId.deviceId(dstDeviceId));

        if (registeredDevices.contains(srcDevice) && registeredDevices.contains(dstDevice)) return true;

        return false;
    }

    private boolean isLinksValid(LinkService linkService, ConnectPoint connectPoint1, ConnectPoint connectPoint2) {

        Link to = linkService.getLink(connectPoint1, connectPoint2);
        Link from = linkService.getLink(connectPoint2, connectPoint1);

        if(to != null && from != null) return true;

        return false;
    }
}
