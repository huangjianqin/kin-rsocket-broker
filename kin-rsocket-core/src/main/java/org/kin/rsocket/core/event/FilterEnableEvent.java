package org.kin.rsocket.core.event;

/**
 * @author huangjianqin
 * @date 2021/3/29
 */
public class FilterEnableEvent implements CloudEventSupport {
    private static final long serialVersionUID = -6884554299203591695L;
    /** {@link org.kin.rsocket.broker.AbstractRSocketFilter}实现类 */
    private String filterClassName;
    /** 是否开启 */
    private boolean enabled;

    public static FilterEnableEvent of(String filterClassName, boolean enabled) {
        FilterEnableEvent inst = new FilterEnableEvent();
        inst.filterClassName = filterClassName;
        inst.enabled = enabled;
        return inst;
    }

    //setter && getter
    public String getFilterClassName() {
        return filterClassName;
    }

    public void setFilterClassName(String filterClassName) {
        this.filterClassName = filterClassName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
