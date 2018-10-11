package org.xzk.network_slicing;

import java.util.LinkedList;
import java.util.Queue;

public class MplsLabelPool {

    private final int MIN_LABEL = 1;
    private final int MAX_LABEL = 1048576;

    private int currentLabel = 0;

    private Queue<Integer> withdrawedLabels = new LinkedList<>();

    public MplsLabelPool() {
        currentLabel = 1;
    }

    public int getNextLabel () {
        if(!withdrawedLabels.isEmpty()){
            return withdrawedLabels.poll();
        } else {
            // TODO: MAX_LABEL?
            return currentLabel++;
        }
    }

    public void returnLabel(int MplsLabel) {
        withdrawedLabels.add(MplsLabel);
    }

}
