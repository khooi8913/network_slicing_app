package org.xzk.network_slicing.cli;

import com.google.common.graph.Network;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.virtual.NetworkId;
import org.onosproject.incubator.net.virtual.VirtualNetworkAdminService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.edge.EdgePortService;

import java.util.Set;
import java.util.stream.StreamSupport;

@Command(scope = "onos", name = "ns-add-edge-port",
        description = "Creates a new virtual network for the specified tenant")
public class EdgePortAddCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "Network ID",
            required = true, multiValued = false)
    Long networkId = null;

    @Argument(index = 1, name = "deviceId", description = "Source device ID",
            required = true, multiValued = false)
    String deviceId = null;

    @Argument(index = 2, name = "portNum", description = "Source port number",
            required = true, multiValued = false)
    Integer portNum = null;

    @Override
    protected void execute() {
        VirtualNetworkAdminService virtualNetworkAdminService = getService(VirtualNetworkAdminService.class);
        EdgePortService edgePortService = getService(EdgePortService.class);

        ConnectPoint requestedPort = new ConnectPoint(DeviceId.deviceId(deviceId), PortNumber.portNumber(portNum));
        Iterable<ConnectPoint> edgePorts = edgePortService.getEdgePoints();

        boolean containsRequestPort = StreamSupport
                .stream(edgePorts.spliterator(), false)
                .anyMatch(requestedPort::equals);

        if (containsRequestPort) {
            virtualNetworkAdminService.createVirtualPort(
                    NetworkId.networkId(networkId),
                    DeviceId.deviceId(deviceId),
                    PortNumber.portNumber(portNum),
                    requestedPort
                    );

            virtualNetworkAdminService.bindVirtualPort(
                    NetworkId.networkId(networkId),
                    DeviceId.deviceId(deviceId),
                    PortNumber.portNumber(portNum),
                    requestedPort
            );

            print("Port added successfully!");

        } else {

            // TODO: Detailed error messages
            error("Invalid port specified!");

        }
    }
}
