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
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.virtual.*;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

            // Perform Host Registration
            VirtualHost virtualHost = getVirtualHostInformation(packetContext, tenantIdAndNetworkId.getNetworkId());

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
            return StreamSupport
                    .stream(edgePortService.getEdgePoints().spliterator(), false)
                    .anyMatch(packetContext.inPacket().receivedFrom()::equals);
        }

        private VirtualHost getVirtualHostInformation(PacketContext packetContext, NetworkId networkId) {

            InboundPacket inboundPacket = packetContext.inPacket();
            Ethernet ethernetPacket = inboundPacket.parsed();


//            VirtualHost incomingHost = new DefaultVirtualHost(
//                    networkId,
//                    HostId.hostId(ethernetPacket.getSourceMAC()),
//                    ethernetPacket.getSourceMAC(),
//                    null,
//                    new HostLocation(),
//
//                    );
            /**
             * Creates a virtual host attributed to the specified provider.
             *
             * @param networkId network identifier
             * @param id        host identifier
             * @param mac       host MAC address
             * @param vlan      host VLAN identifier
             * @param location  host location
             * @param ips       host IP addresses
             */

            Set<VirtualHost> virtualHosts = virtualNetworkAdminService.getVirtualHosts(networkId);


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

    }

}
