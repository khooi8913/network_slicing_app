package org.xzk.network_slicing.models;

import org.onlab.packet.MplsLabel;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.FlowRule;

public class FlowRuleInformation {

    private FlowRule flowRule;
    private MplsLabel mplsLabel;

    public FlowRuleInformation(FlowRule flowRule, MplsLabel mplsLabel) {
        this.flowRule = flowRule;
        this.mplsLabel = mplsLabel;
    }

    public DeviceId getFlowRuleDeviceId() {
        return this.flowRule.deviceId();
    }

    public FlowRule getFlowRule() {
        return this.flowRule;
    }

    public MplsLabel getMplsLabel() {
        return this.mplsLabel;
    }
}
