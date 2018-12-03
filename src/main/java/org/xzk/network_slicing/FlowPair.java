package org.xzk.network_slicing;

import org.onlab.packet.IpAddress;

import java.util.Objects;

public class FlowPair {

    private IpAddress src;
    private IpAddress dst;

    public FlowPair(IpAddress src, IpAddress dst) {
        this.src = src;
        this.dst = dst;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowPair flowPair = (FlowPair) o;
        return Objects.equals(src, flowPair.src) &&
                Objects.equals(dst, flowPair.dst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, dst);
    }
}
