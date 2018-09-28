/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xzk.network_slicing;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.virtual.*;
import org.onosproject.net.*;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;
    private VirtualNetworkPacketProcessor virtualNetworkPacketProcessor = new VirtualNetworkPacketProcessor();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualNetworkAdminService virtualNetworkAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EdgePortService edgePortService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.xzk.network_slicing");
        requestIntercepts();
        packetService.addProcessor(virtualNetworkPacketProcessor, PacketProcessor.director(2));
        log.info("Started");
    }

    // Request packet in via packet service
    private void requestIntercepts() {
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        trafficSelector.matchEthType(Ethernet.TYPE_IPV4);
        trafficSelector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(trafficSelector.build(), PacketPriority.REACTIVE, appId);
    }

    // To cancel request for packet in via packet service
    private void withdrawIntercepts() {
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        trafficSelector.matchEthType(Ethernet.TYPE_IPV4);
        trafficSelector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(trafficSelector.build(), PacketPriority.REACTIVE, appId);
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        virtualNetworkPacketProcessor = null;
        log.info("Stopped");
    }

    private class VirtualNetworkPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext packetContext) {

            // Stop processing if the packet has already been handled.
            // Nothing much more can be done.
            if (packetContext.isHandled()) {
                return;
            }

            InboundPacket inboundPacket = packetContext.inPacket();
            Ethernet ethernetPacket = inboundPacket.parsed();

            // Only process packets coming from the network edge
            if (!isEdgePort(packetContext)) {
                return;
            }

            // Do not process null packets
            if (ethernetPacket == null) {
                return;
            }

            // Retrieve Port Information
            TenantIdNetworkIdPair tenantIdAndNetworkId = getTenantIdAndNetworkId(packetContext);
            if (tenantIdAndNetworkId == null) {
                return;
            }

            // Register/ get incoming host information
            VirtualHost sourceHost = getSourceHostInformation(
                    packetContext,
                    tenantIdAndNetworkId.getNetworkId()
            );

            if (sourceHost == null) {
                return;
            }

            TrafficSelector.Builder selector;
            TrafficTreatment.Builder builder;

            switch (EthType.EtherType.lookup(ethernetPacket.getEtherType())) {
                case ARP:
                    ARP arpPacket = (ARP) ethernetPacket.getPayload();
                    MacAddress destinationMacAddress = getDestinationMacAddress(arpPacket, tenantIdAndNetworkId.getNetworkId());

                    if (destinationMacAddress == null) {
                        return;
                    }

                    // Construct ARP Reply
                    Ip4Address destinationIpAddress = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
                    Ethernet ethernet = ARP.buildArpReply(destinationIpAddress, destinationMacAddress, ethernetPacket);

                    builder = DefaultTrafficTreatment.builder();
                    builder.setOutput(inboundPacket.receivedFrom().port());
                    packetService.emit(new DefaultOutboundPacket(
                            inboundPacket.receivedFrom().deviceId(),
                            builder.build(),
                            ByteBuffer.wrap(ethernet.serialize())
                    ));
                    break;

                case IPV4:
                    // Get destination host information
                    VirtualHost destinationHost = getDestinationHostInformation(
                            ethernetPacket.getDestinationMAC(),
                            tenantIdAndNetworkId.getNetworkId()
                    );

                    if (destinationHost == null) {
                        return;
                    }


                    // Both hosts located on the same device
                    if (sourceHost.location().deviceId().equals(destinationHost.location().deviceId())) {

                        // TODO: Forward to the designated port

                    } else {
                        // Path computation here
                        // Get set of paths
                        Set<Path> paths = topologyService.getPaths(
                                topologyService.currentTopology(),
                                sourceHost.location().deviceId(),
                                destinationHost.location().deviceId()
                        );

                        if (paths.isEmpty()) {
                            return;
                        }

                        Path path = pickForwardPathIfPossible(
                                paths,
                                inboundPacket.receivedFrom().port(),
                                tenantIdAndNetworkId.getNetworkId());

                        if (path == null) {
                            return;
                        }

                        List<Link> pathLinks = path.links();
                        List<InOutPort> inOutPorts = extractInOutPorts(pathLinks, sourceHost, destinationHost);

                        // Build path

                    }

                    break;
            }
        }

        private TenantIdNetworkIdPair getTenantIdAndNetworkId(PacketContext packetContext) {

            // TODO: More efficient implementation
            // Get all tenants
            Set<TenantId> tenantIds =
                    virtualNetworkAdminService.getTenantIds();
            for (TenantId tenantId : tenantIds) {
                // Get all virtual networks per tenant
                Set<VirtualNetwork> virtualNetworks =
                        virtualNetworkAdminService.getVirtualNetworks(tenantId);
                for (VirtualNetwork virtualNetwork : virtualNetworks) {
                    // Get all the connect points registered
                    Set<VirtualDevice> virtualDevices =
                            virtualNetworkAdminService.getVirtualDevices(virtualNetwork.id());
                    for (VirtualDevice virtualDevice : virtualDevices) {
                        Set<VirtualPort> virtualPorts =
                                virtualNetworkAdminService.getVirtualPorts(virtualNetwork.id(), virtualDevice.id());
                        for (VirtualPort virtualPort : virtualPorts) {
                            if (packetContext.inPacket().receivedFrom().equals(virtualPort.realizedBy())) {
                                // Return something here
                                return new TenantIdNetworkIdPair(tenantId, virtualNetwork.id());
                            }
                        }
                    }
                }
            }
            return null;
        }

        private boolean isEdgePort(PacketContext packetContext) {

            Iterable<ConnectPoint> edgePorts = edgePortService.getEdgePoints();

            return StreamSupport
                    .stream(edgePorts.spliterator(), false)
                    .anyMatch(packetContext.inPacket().receivedFrom()::equals);
        }

        private VirtualHost getSourceHostInformation(PacketContext packetContext, NetworkId networkId) {

            InboundPacket inboundPacket = packetContext.inPacket();
            Ethernet ethernetPacket = inboundPacket.parsed();

            MacAddress macAddress;
            HostId hostId;
            HostLocation hostLocation;
            Set<IpAddress> ipAddresses = new HashSet<>();

            if (ethernetPacket.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipPacket = (IPv4) ethernetPacket.getPayload();

                macAddress = ethernetPacket.getSourceMAC();
                hostId = HostId.hostId(ethernetPacket.getSourceMAC());
                hostLocation = new HostLocation(inboundPacket.receivedFrom(), System.currentTimeMillis());
                ipAddresses.add(IpAddress.valueOf(ipPacket.getSourceAddress()));

            } else {
                ARP arpPacket = (ARP) ethernetPacket.getPayload();
                macAddress = ethernetPacket.getSourceMAC();
                hostId = HostId.hostId(ethernetPacket.getSourceMAC());
                hostLocation = new HostLocation(inboundPacket.receivedFrom(), System.currentTimeMillis());
                ipAddresses.add(IpAddress.valueOf(IpAddress.Version.INET, arpPacket.getSenderProtocolAddress()));
            }

            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);

            // Check if host already exist
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.id().equals(hostId)) {
                    // TODO: Check HostLocation
                    return virtualHost;
                }
            }

            // If not exist
            VirtualHost virtualHost = virtualNetworkAdminService.createVirtualHost(
                    networkId,
                    hostId,
                    macAddress,
                    VlanId.NONE,
                    hostLocation,
                    ipAddresses);

            return virtualHost;
        }

        private VirtualHost getDestinationHostInformation(MacAddress destinationMacAddress, NetworkId networkId) {

            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.mac().equals(destinationMacAddress)) {
                    return virtualHost;
                }
            }

            return null;
        }

        private MacAddress getDestinationMacAddress(ARP arpPacket, NetworkId networkId) {

            byte[] destinationIpAddress = arpPacket.getTargetProtocolAddress();

            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.ipAddresses().contains(
                        IpAddress.valueOf(IpAddress.Version.INET, destinationIpAddress))) {
                    return virtualHost.mac();
                }
            }

            return null;
        }

        // Pick the possible paths with the links that are registered by the tenant
        private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort, NetworkId networkId) {

            for (Path path : paths) {
                if (!path.src().port().equals(notToPort)) {
                    // Not going back to itself

                    // Get all the virtual links available
                    Set<VirtualLink> virtualLinks = virtualNetworkAdminService.getVirtualLinks(networkId);
                    Set<SimpleLink> availableLinks = new HashSet<>();
                    for (VirtualLink virtualLink : virtualLinks) {
                        availableLinks.add(new SimpleLink(virtualLink.src(), virtualLink.dst()));
                    }

                    // Get all the path links
                    Set<SimpleLink> pathLinks = new HashSet<>();
                    List<Link> linkList = path.links();
                    for (Link link : linkList) {
                        pathLinks.add(new SimpleLink(link.src(), link.dst()));
                    }

                    // We need to make sure that the the paths is a subset of the virtual links
                    if (virtualLinks.containsAll(pathLinks)) {
                        return path;
                    }
                }
            }
            return null;
        }

        private List<InOutPort> extractInOutPorts(List<Link> links, VirtualHost sourceHost, VirtualHost destinationHost) {
            List<InOutPort> inOutPorts = new ArrayList<>();
            for (int i = 0; i < links.size(); i++) {
                if (i == 0) {
                    inOutPorts.add(new InOutPort(
                        links.get(i).src().deviceId(),
                        sourceHost.location().port(),
                        links.get(i).src().port()
                    ));
                }  else {
                    inOutPorts.add(new InOutPort(
                            links.get(i).src().deviceId(),
                            links.get(i-1).dst().port(),
                            links.get(i).src().port()
                    ));
                }

                if(i == links.size() -1) {
                    inOutPorts.add(new InOutPort(
                            links.get(i).src().deviceId(),
                            links.get(i).dst().port(),
                            destinationHost.location().port()
                    ));
                }
            }
            return null;
        }

        class TenantIdNetworkIdPair {

            private TenantId tenantId;
            private NetworkId networkId;

            public TenantIdNetworkIdPair(TenantId tenantId, NetworkId networkId) {
                this.tenantId = tenantId;
                this.networkId = networkId;
            }

            public TenantId getTenantId() {
                return tenantId;
            }

            public NetworkId getNetworkId() {
                return networkId;
            }
        }

        class SimpleLink {

            private ConnectPoint src;
            private ConnectPoint dst;

            public SimpleLink(ConnectPoint src, ConnectPoint dst) {
                this.src = src;
                this.dst = dst;
            }
        }

        class InOutPort {
            DeviceId deviceId;
            PortNumber inPort;
            PortNumber outPort;

            public InOutPort(DeviceId deviceId, PortNumber inPort, PortNumber outPort) {
                this.deviceId = deviceId;
                this.inPort = inPort;
                this.outPort = outPort;
            }
        }

    }

}
