package org.xzk.network_slicing;

import org.onlab.packet.IpAddress;
import org.onosproject.net.flow.FlowRule;

import java.util.ArrayList;
import java.util.HashMap;

public class InstalledFlowRules {

    private HashMap<IpAddress, HashMap<IpAddress, ArrayList<FlowRule>>> installedFlowRules;

    public InstalledFlowRules() {
        installedFlowRules = new HashMap<>();
    }

    public void addFlowRule(IpAddress src, IpAddress dst, FlowRule flowRule) {
        if(!installedFlowRules.containsKey(src))    installedFlowRules.put(src, new HashMap<>());
        if(!installedFlowRules.get(src).containsKey(dst))   installedFlowRules.get(src).put(dst, new ArrayList<>());
        installedFlowRules.get(src).get(dst).add(flowRule);
    }

    public ArrayList<FlowRule> getFlowRules(IpAddress src, IpAddress dst) {
        if(!installedFlowRules.containsKey(src))    return null;
        if(!installedFlowRules.get(src).containsKey(dst))   return null;
        return installedFlowRules.get(src).get(dst);
    }

    public void deleteFlowRules(IpAddress src, IpAddress dst){
        installedFlowRules.get(src).remove(dst);
        if(installedFlowRules.get(src) == null) {
            installedFlowRules.remove(src);
        }
    }

    public HashMap<IpAddress, HashMap<IpAddress, ArrayList<FlowRule>>> getAllFlowRules(){
        return installedFlowRules;
    }

}
