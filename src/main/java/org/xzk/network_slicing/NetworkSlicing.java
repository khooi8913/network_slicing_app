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
import org.onosproject.event.Event;
import org.onosproject.incubator.net.virtual.*;
import org.onosproject.net.*;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xzk.network_slicing.helper.FlowRuleStorage;
import org.xzk.network_slicing.helper.MplsForwardingTable;
import org.xzk.network_slicing.helper.MplsLabelPool;
import org.xzk.network_slicing.helper.VirtualNetworkGraph;
import org.xzk.network_slicing.models.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.StreamSupport;

@Component(immediate = true)
public class NetworkSlicing {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;
    private VirtualNetworkPacketProcessor virtualNetworkPacketProcessor = new VirtualNetworkPacketProcessor();
    private VirtualNetworkTopologyListener virtualNetworkTopologyListener = new VirtualNetworkTopologyListener();

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualNetworkAdminService virtualNetworkAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EdgePortService edgePortService;

    // TenantId/ NetworkId <---> IpNetworks/ Gateway
    private final byte[] gatewayMac = {00, 01, 02, 03, 04, 05};
    private final int DEFAULT_PRIORITY = 100;

    // Tenant's Info
    public static HashMap<NetworkId, RoutedNetworks> tenantRoutedNetworks;
    public static FlowRuleStorage flowRuleStorage;

    // MplsTables
    public static HashMap<DeviceId, MplsLabelPool> mplsLabelPool;
    public static HashMap<DeviceId, MplsForwardingTable> mplsForwardingTable;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.xzk.network_slicing");
        requestIntercepts();
        packetService.addProcessor(virtualNetworkPacketProcessor, PacketProcessor.director(2));
        topologyService.addListener(virtualNetworkTopologyListener);

        flowRuleStorage = new FlowRuleStorage();
        tenantRoutedNetworks = new HashMap<>();

        mplsLabelPool = new HashMap<>();
        mplsForwardingTable = new HashMap<>();
        log.info("Started");
    }

    // Request packet in via packet service
    private void requestIntercepts() {
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        trafficSelector.matchEthType(Ethernet.TYPE_ARP);
        trafficSelector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(trafficSelector.build(), PacketPriority.REACTIVE, appId);
    }

    // To cancel request for packet in via packet service
    private void withdrawIntercepts() {
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        trafficSelector.matchEthType(Ethernet.TYPE_ARP);
        trafficSelector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(trafficSelector.build(), PacketPriority.REACTIVE, appId);
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        virtualNetworkPacketProcessor = null;
        virtualNetworkTopologyListener = null;

        flowRuleStorage = null;
        tenantRoutedNetworks = null;

        mplsLabelPool = null;
        mplsForwardingTable = null;
        log.info("Stopped");
    }

    private class VirtualNetworkPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext packetContext) {
            // Stop processing if the packet has already been handled.
            // Nothing much more can be done.
            if (packetContext.isHandled()) return;

            InboundPacket inboundPacket = packetContext.inPacket();
            Ethernet ethernetPacket = inboundPacket.parsed();

            // Only process packets coming from the network edge
            if (!isEdgePort(packetContext)) return;

            // Do not process null packets
            if (ethernetPacket == null) return;

            // Retrieve TenantId Information
            TenantId currentTenantId = getTenantId(packetContext);

            // Retrieve NetworkId Information
            NetworkId currentNetworkId = getNetworkId(packetContext);

            // Register incoming host information
            VirtualHost sourceHost = getSourceHost(
                    packetContext,
                    currentNetworkId
            );
            if (sourceHost == null) return;

            switch (EthType.EtherType.lookup(ethernetPacket.getEtherType())) {
                case ARP:
                    log.info("ARP packet received");

                    ARP arpPacket = (ARP) ethernetPacket.getPayload();
                    MacAddress destinationMacAddress = getDestinationMac(
                            arpPacket,
                            currentNetworkId
                    );

                    if (destinationMacAddress == null) {
                        log.info("Destination host does not exist!");
                        return;
                    }

                    Ip4Address destinationIpAddress = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
                    Ethernet ethernet = ARP.buildArpReply(destinationIpAddress, destinationMacAddress, ethernetPacket);

                    TrafficTreatment.Builder treatment;
                    treatment = DefaultTrafficTreatment.builder();
                    treatment.setOutput(inboundPacket.receivedFrom().port());
                    packetService.emit(new DefaultOutboundPacket(
                            inboundPacket.receivedFrom().deviceId(),
                            treatment.build(),
                            ByteBuffer.wrap(ethernet.serialize())
                    ));
                    log.info("ARP reply sent!");
                    break;

                case IPV4:
                    log.info("IPv4 packet received!");

                    // If the destination MAC is headed to the gateway, which means to different network
                    MacAddress destinationMac = ethernetPacket.getDestinationMAC();
                    boolean isToBeRouted = isToBeRouted(destinationMac);
                    VirtualHost destinationHost = getDestinationHost(isToBeRouted, ethernetPacket, currentNetworkId);

                    // TODO: What if headed to external network?
                    if (destinationHost == null) {
                        log.info("Destination host does not exist!");
                        return;
                    }

                    if (isHostOnSameDevice(sourceHost, destinationHost)) {

                        forwardToSameDevice(packetContext, sourceHost, destinationHost, isToBeRouted, currentNetworkId);

                    } else {

                        forwardToDiffDevice(packetContext, sourceHost, destinationHost, isToBeRouted, currentNetworkId);

                    }
                    break;
            }
        }

        private boolean isEdgePort(PacketContext packetContext) {
            Iterable<ConnectPoint> edgePorts = edgePortService.getEdgePoints();
            return StreamSupport
                    .stream(edgePorts.spliterator(), false)
                    .anyMatch(packetContext.inPacket().receivedFrom()::equals);
        }

        private TenantId getTenantId(PacketContext packetContext) {
            Set<TenantId> tenantIds = virtualNetworkAdminService.getTenantIds();
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
                                return tenantId;
                            }
                        }
                    }
                }
            }
            return null;
        }

        private NetworkId getNetworkId(PacketContext packetContext) {
            Set<TenantId> tenantIds = virtualNetworkAdminService.getTenantIds();
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
                                return virtualNetwork.id();
                            }
                        }
                    }
                }
            }
            return null;
        }

        private VirtualHost getSourceHost(PacketContext packetContext, NetworkId networkId) {
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

            // TODO: Have to make sure that no duplicate hosts exists
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

        private VirtualHost getDestinationHost(MacAddress destinationMacAddress, NetworkId networkId) {

            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.mac().equals(destinationMacAddress)) {
                    return virtualHost;
                }
            }

            return null;
        }

        private VirtualHost getDestinationHost(IpAddress ipAddress, NetworkId networkId) {
            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.ipAddresses().contains(ipAddress)) {
                    return virtualHost;
                }
            }
            return null;
        }

        private VirtualHost getDestinationHost(boolean isToBeRouted, Ethernet ethernetPacket, NetworkId networkId) {
            if (isToBeRouted) {
                log.info("Packet is to be routed!");

                IPv4 ipPacket = (IPv4) ethernetPacket.getPayload();
                IpAddress ipDstAddress = IpAddress.valueOf(
                        ipPacket.getDestinationAddress()
                );
                // Get destination host information
                return getDestinationHost(
                        ipDstAddress,
                        networkId
                );
            } else {
                // Get destination host information
                return getDestinationHost(
                        ethernetPacket.getDestinationMAC(),
                        networkId
                );
            }
        }

        private MacAddress getDestinationMac(ARP arpPacket, NetworkId networkId) {

            byte[] destinationIpAddress = arpPacket.getTargetProtocolAddress();
            IpAddress destinationIp = IpAddress.valueOf(IpAddress.Version.INET, destinationIpAddress);

            // If ARP is for gateway
            if (NetworkSlicing.tenantRoutedNetworks.containsKey(networkId)) {
                RoutedNetworks routedNetworks = NetworkSlicing.tenantRoutedNetworks.get(networkId);
                if (routedNetworks.networkGateway != null) {
                    for (Map.Entry<IpPrefix, IpAddress> networks : routedNetworks.networkGateway.entrySet()) {
                        if (destinationIp.equals(networks.getValue())) {
                            log.info("ARP reply for gateway!");
                            return new MacAddress(gatewayMac);
                        }
                    }
                }
            }

            // If not gateway found, most probably it belongs to a host
            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);
            for (VirtualHost virtualHost : virtualHosts) {
                if (virtualHost.ipAddresses().contains(
                        IpAddress.valueOf(IpAddress.Version.INET, destinationIpAddress))) {
                    log.info("ARP reply for host!");
                    return virtualHost.mac();
                }
            }

            return null;
        }

        private boolean isHostOnSameDevice(VirtualHost sourceHost, VirtualHost destinationHost) {
            return sourceHost.location().deviceId().equals(destinationHost.location().deviceId());
        }

        private boolean isToBeRouted(MacAddress destinationMAC) {
            return destinationMAC.equals(new MacAddress(gatewayMac));
        }

        private void forwardToSameDevice(PacketContext packetContext, VirtualHost sourceHost, VirtualHost destinationHost, boolean isToBeRouted, NetworkId currentNetworkId) {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();

            Ethernet ethernetPacket = packetContext.inPacket().parsed();
            IPv4 ipPacket = (IPv4) ethernetPacket.getPayload();
            Ip4Prefix ip4DstPrefix = Ip4Prefix.valueOf(
                    ipPacket.getDestinationAddress(),
                    Ip4Prefix.MAX_MASK_LENGTH
            );

            PortNumber inPort = sourceHost.location().port();
            PortNumber outPort = destinationHost.location().port();

            DeviceId currentDeviceId = sourceHost.location().deviceId();

            selector.matchInPort(inPort);
            selector.matchEthType(Ethernet.TYPE_IPV4);
            selector.matchIPDst(ip4DstPrefix);

            if (isToBeRouted) treatment.setEthDst(destinationHost.mac());
            treatment.setOutput(outPort);

            // Build & send forwarding objective
            sendFlowObjective(currentDeviceId, selector, treatment);
            log.info("Flow objective sent to device!");

            // Forward out current packet
            packetOut(packetContext, outPort);
            log.info("Packet out!");

            // Store FlowRule
            IpAddress src = IpAddress.valueOf(ipPacket.getSourceAddress());
            IpAddress dst = IpAddress.valueOf(ipPacket.getDestinationAddress());

            FlowPair flowPair = new FlowPair(src, dst);
//            storeFlowRule(src, dst, selector, treatment, currentDeviceId, currentNetworkId);
            storeFlowRule(flowPair, selector, treatment, null, currentDeviceId, currentNetworkId);
        }

        private void forwardToDiffDevice(PacketContext packetContext, VirtualHost sourceHost, VirtualHost destinationHost, boolean isToBeRouted, NetworkId currentNetworkId) {
            TrafficSelector.Builder selector;
            TrafficTreatment.Builder treatment;

            // Path computation here
            log.info("Path Computation");
            ArrayList<DeviceId> pathNodes = getForwardPathIfPossible(
                    currentNetworkId,
                    sourceHost,
                    destinationHost
            );

            // Display path
            for (DeviceId deviceId : pathNodes) log.info(deviceId.toString());
            if (pathNodes.isEmpty()) {
                log.info("Unable to find valid path!");
                return;
            }

            List<Link> pathLinks = getForwardPathLinks(currentNetworkId, pathNodes);
            for (Link link : pathLinks) log.info(link.src().toString() + " " + link.dst().toString());

            if (pathLinks.isEmpty()) {
                log.info("Unable to find valid path!");
                return;
            }

            List<InOutPort> inOutPorts = extractInOutPorts(pathLinks, sourceHost, destinationHost);

            log.info("Distributing labels!");

            // Initialize MplsLabelPool
            initializeMplsLabelPool(inOutPorts);
            initializeMplsForwardingTables(inOutPorts);

            // Distribute labels and build path
            MplsLabel currentLabel = null;
            MplsLabel previousLabel = null;

            // Extract Destination IP
            Ethernet ethernetPacket = packetContext.inPacket().parsed();
            IPv4 ipPacket = (IPv4) ethernetPacket.getPayload();
            Ip4Prefix ip4DstPrefix = Ip4Prefix.valueOf(
                    ipPacket.getDestinationAddress(),
                    Ip4Prefix.MAX_MASK_LENGTH
            );

            IpAddress src = IpAddress.valueOf(ipPacket.getSourceAddress());
            IpAddress dst = IpAddress.valueOf(ipPacket.getDestinationAddress());

            FlowPair flowPair = new FlowPair(src, dst);

            for (int i = inOutPorts.size() - 1; i >= 0; i--) {
                selector = DefaultTrafficSelector.builder();
                treatment = DefaultTrafficTreatment.builder();

                log.info(inOutPorts.get(i).toString());

                PortNumber inPort = inOutPorts.get(i).getInPort();
                PortNumber outPort = inOutPorts.get(i).getOutPort();
                DeviceId currentDeviceId = inOutPorts.get(i).getDeviceId();

                if (currentDeviceId.equals(destinationHost.location().deviceId())) {   // Terminating Switch
                    log.info("Flow installing for terminating switch");
                    if (mplsForwardingTable.get(currentDeviceId).getMplsLabel(
                            currentNetworkId, destinationHost.id()) == null) {

                        currentLabel = MplsLabel.mplsLabel(mplsLabelPool
                                .get(currentDeviceId)
                                .getNextLabel()
                        );
                    } else {
                        currentLabel = mplsForwardingTable.get(currentDeviceId).getMplsLabel(
                                currentNetworkId, destinationHost.id());

                        mplsForwardingTable.get(currentDeviceId).addLabelToHost(
                                currentNetworkId, destinationHost.id(), currentLabel
                        );
                    }

                    selector.matchInPort(inPort);
                    selector.matchEthType(Ethernet.MPLS_UNICAST);
                    selector.matchMplsBos(true);
                    selector.matchMplsLabel(currentLabel);

                    treatment.popMpls(new EthType(Ethernet.TYPE_IPV4));
                    treatment.setOutput(outPort);

                    previousLabel = currentLabel;

                    storeFlowRule(flowPair, selector, treatment, currentLabel, currentDeviceId, currentNetworkId);
                } else if (currentDeviceId.equals(sourceHost.location().deviceId())) {
                    // Originating Switch
                    log.info("Flow installing for originating switch");
                    mplsForwardingTable.get(currentDeviceId).addLabelToHost(
                            currentNetworkId, destinationHost.id(), previousLabel
                    );

                    selector.matchInPort(inPort);
                    selector.matchIPDst(ip4DstPrefix);
                    selector.matchEthType(Ethernet.TYPE_IPV4);

                    if (isToBeRouted) {
                        treatment.setEthDst(destinationHost.mac());
                    }
                    treatment.pushMpls();
                    treatment.setMpls(previousLabel);
                    treatment.setOutput(outPort);

                    storeFlowRule(flowPair, selector, treatment, null, currentDeviceId, currentNetworkId);
                } else {
                    // LSRs
                    log.info("Flow installing for LSRs");
                    if (mplsForwardingTable.get(currentDeviceId).getMplsLabel(
                            currentNetworkId, destinationHost.id()) == null) {
                        currentLabel = MplsLabel.mplsLabel(mplsLabelPool
                                .get(currentDeviceId)
                                .getNextLabel()
                        );
                    } else {
                        currentLabel = mplsForwardingTable.get(currentDeviceId).getMplsLabel(
                                currentNetworkId, destinationHost.id());
                        mplsForwardingTable.get(currentDeviceId).addLabelToHost(
                                currentNetworkId, destinationHost.id(), currentLabel
                        );
                    }

                    selector.matchInPort(inPort);
                    selector.matchMplsLabel(currentLabel);
                    selector.matchEthType(Ethernet.MPLS_UNICAST);

                    treatment.setMpls(previousLabel);
                    treatment.setOutput(outPort);

                    previousLabel = currentLabel;

                    storeFlowRule(flowPair, selector, treatment, currentLabel, currentDeviceId, currentNetworkId);
                }

                // Build & send forwarding objective
                sendFlowObjective(currentDeviceId, selector, treatment);
                log.info("Flow objective sent to device!" + currentDeviceId.toString());
            }

            // Forward out current packet
            packetOut(packetContext, inOutPorts.get(0).outPort);
            log.info("Packet out!");
        }

        // Custom implementation of path computation
        private ArrayList<DeviceId> getForwardPathIfPossible(NetworkId networkId, VirtualHost sourceHost, VirtualHost destinationHost) {

            // Get all the virtual links available
            Set<VirtualLink> virtualLinks = virtualNetworkAdminService.getVirtualLinks(networkId);

            // Construct Graph
            VirtualNetworkGraph virtualNetworkGraph = new VirtualNetworkGraph();
            for (VirtualLink virtualLink : virtualLinks) {
                if (virtualLink.state().equals(VirtualLink.State.ACTIVE)) {
                    virtualNetworkGraph.addEdge(virtualLink.src().deviceId(), virtualLink.dst().deviceId());
                }
            }

            // If it's A->B->C, it will return C, B, A. Order is reversed
            ArrayList<DeviceId> computedPath = virtualNetworkGraph.bfsForShortestPath(sourceHost.location().deviceId(),
                    destinationHost.location().deviceId());
            Collections.reverse(computedPath);
            return computedPath;
        }
        // Get Links in the path

        private List<Link> getForwardPathLinks(NetworkId networkId, ArrayList<DeviceId> deviceIds) {
            List<Link> links = new LinkedList<>();

            // Get all the virtual links available
            Set<VirtualLink> virtualLinks = virtualNetworkAdminService.getVirtualLinks(networkId);

            for (int i = 0; i < deviceIds.size() - 1; i++) {
                for (VirtualLink virtualLink : virtualLinks) {
                    if (virtualLink.state().equals(VirtualLink.State.ACTIVE)) {
                        if (virtualLink.src().deviceId().equals(deviceIds.get(i)) &&
                                virtualLink.dst().deviceId().equals(deviceIds.get(i + 1))) {
                            links.add(virtualLink);
                        }
                    }
                }
            }
            return links;
        }

        private List<InOutPort> extractInOutPorts(List<Link> links, VirtualHost sourceHost, VirtualHost destinationHost) {
            List<InOutPort> inOutPorts = new LinkedList<>();
            for (int i = 0; i < links.size(); i++) {

                if (i == 0) {
                    inOutPorts.add(new InOutPort(
                            links.get(i).src().deviceId(),
                            sourceHost.location().port(),
                            links.get(i).src().port()
                    ));
                } else {
                    inOutPorts.add(new InOutPort(
                            links.get(i).src().deviceId(),
                            links.get(i - 1).dst().port(),
                            links.get(i).src().port()
                    ));
                }

                if (i == links.size() - 1) {
                    inOutPorts.add(new InOutPort(
                            links.get(i).dst().deviceId(),
                            links.get(i).dst().port(),
                            destinationHost.location().port()
                    ));
                }

            }
            return inOutPorts;
        }

        private void initializeMplsLabelPool(List<InOutPort> inOutPorts) {
            for (InOutPort inOutPort : inOutPorts) {
                if (!mplsLabelPool.containsKey(inOutPort.getDeviceId())) {
                    mplsLabelPool.put(inOutPort.getDeviceId(), new MplsLabelPool());
                }
            }
        }

        private void initializeMplsForwardingTables(List<InOutPort> inOutPorts) {
            for (InOutPort inOutPort : inOutPorts) {
                if (!mplsForwardingTable.containsKey(inOutPort.getDeviceId())) {
                    mplsForwardingTable.put(inOutPort.getDeviceId(), new MplsForwardingTable());
                }
            }
        }

        private void sendFlowObjective(DeviceId deviceId, TrafficSelector.Builder selector, TrafficTreatment.Builder treatment) {
            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .withPriority(DEFAULT_PRIORITY)
                    .fromApp(appId)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .add();
            flowObjectiveService.forward(deviceId, forwardingObjective);
        }

        // Sends a packet out the specified port.
        private void packetOut(PacketContext packetContext, PortNumber portNumber) {
            packetContext.treatmentBuilder().setOutput(portNumber);
            packetContext.send();
        }

        // New FlowRuleStorageMechanism
        private void storeFlowRule(FlowPair flowPair, TrafficSelector.Builder selector, TrafficTreatment.Builder treatment, MplsLabel mplsLabel, DeviceId deviceId, NetworkId networkId) {
            FlowRule flowRule = DefaultFlowRule.builder()
                    .withSelector(selector.build())
                    .withTreatment(treatment.build())
                    .withPriority(DEFAULT_PRIORITY)
                    .withHardTimeout(FlowRule.MAX_TIMEOUT)
                    .fromApp(appId)
                    .forDevice(deviceId)
                    .build();
            flowRuleStorage.addFlowRule(networkId, flowPair, flowRule, mplsLabel);
        }

        class InOutPort {
            private DeviceId deviceId;
            private PortNumber inPort;
            private PortNumber outPort;

            InOutPort(DeviceId deviceId, PortNumber inPort, PortNumber outPort) {
                this.deviceId = deviceId;
                this.inPort = inPort;
                this.outPort = outPort;
            }

            DeviceId getDeviceId() {
                return deviceId;
            }

            PortNumber getInPort() {
                return inPort;
            }

            PortNumber getOutPort() {
                return outPort;
            }

            @Override
            public String toString() {
                return "InOutPort{" +
                        "deviceId=" + deviceId.toString() +
                        ", inPort=" + inPort.toString() +
                        ", outPort=" + outPort.toString() +
                        '}';
            }
        }
    }

    private class VirtualNetworkTopologyListener implements TopologyListener {

        @Override
        public void event(TopologyEvent topologyEvent) {

            Set<DeviceId> affectedDevices = new HashSet<>();

            log.info(topologyEvent.toString());
            for (Event e : topologyEvent.reasons()) {
                DefaultLink affectedLink = (DefaultLink) e.subject();
                affectedDevices.add(affectedLink.src().deviceId());
                affectedDevices.add(affectedLink.dst().deviceId());
            }

            // Display affected devices
            log.info("Topology change detected!");
            log.info("Affected devices: ");
            for (DeviceId affectedDevice : affectedDevices) {
                log.info(affectedDevice.toString());
            }

            Set<DeviceId> devicesInFlow = new HashSet<>();
            Set<FlowPair> toBeDeleted = new HashSet<>();

            HashMap<NetworkId, HashMap<FlowPair, List<FlowRuleInformation>>> allFlows = NetworkSlicing.flowRuleStorage.getAllFlows();
            for (Map.Entry<NetworkId, HashMap<FlowPair, List<FlowRuleInformation>>> a : allFlows.entrySet()) {

                for (Map.Entry<FlowPair, List<FlowRuleInformation>> b : a.getValue().entrySet()) {

                    List<FlowRuleInformation> flowRulesList = b.getValue();
                    // Iterate over all flow rule to extract device ID
                    for (FlowRuleInformation f : flowRulesList) {
                        devicesInFlow.add(f.getFlowRuleDeviceId());
                    }
                    // If affected by topology change, mark as to be deleted
                    if (devicesInFlow.containsAll(affectedDevices)) {
                        toBeDeleted.add(b.getKey());
                    }
                    devicesInFlow.clear();
                }

                for (FlowPair f : toBeDeleted) {
                    // Retract flow rules
                    List<FlowRuleInformation> flowRuleInformations = NetworkSlicing.flowRuleStorage.getFlowRules(a.getKey(), f);
                    for (FlowRuleInformation flowRuleInfo : flowRuleInformations) {
                        flowRuleService.removeFlowRules(flowRuleInfo.getFlowRule());

                        // Return MPLS Label if any
                        DeviceId currentDevice = flowRuleInfo.getFlowRuleDeviceId();
                        if (flowRuleInfo.getMplsLabel() != null) {
                            NetworkSlicing.mplsLabelPool.get(currentDevice).returnLabel(flowRuleInfo.getMplsLabel().toInt());
                        }
                    }
                    NetworkSlicing.flowRuleStorage.deleteFlowRules(a.getKey(), f);
                }
                toBeDeleted = new HashSet<>();
            }
        }
    }
}
