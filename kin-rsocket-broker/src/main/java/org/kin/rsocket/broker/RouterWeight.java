package org.kin.rsocket.broker;

/**
 * 路由权重
 *
 * @author huangjianqin
 * @date 2021/5/7
 */
public class RouterWeight {
    /** app 实例id */
    private final int instanceId;
    /** 权重 */
    private final int weight;
    /** 当前权重 */
    private int currentWeight;

    public RouterWeight(int instanceId, int weight) {
        this.instanceId = instanceId;
        this.weight = weight;
    }

    /**
     * 当前权重增加
     */
    public void incr(int alter) {
        currentWeight += alter;
    }

    //setter && getter
    public int getInstanceId() {
        return instanceId;
    }

    public int getWeight() {
        return weight;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }
}
