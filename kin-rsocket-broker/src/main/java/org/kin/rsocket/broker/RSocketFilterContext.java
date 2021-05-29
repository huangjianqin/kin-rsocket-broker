package org.kin.rsocket.broker;

import io.rsocket.Payload;
import io.rsocket.frame.FrameType;
import org.kin.rsocket.core.metadata.AppMetadata;
import org.kin.rsocket.core.metadata.GSVRoutingMetadata;

/**
 * filter逻辑可能需要用到的数据
 *
 * @author huangjianqin
 * @date 2021/3/27
 */
public final class RSocketFilterContext {
    /** request frame type */
    private FrameType frameType;
    /** routing metadata */
    private GSVRoutingMetadata routingMetadata;
    /** source app */
    private AppMetadata source;
    /** request payload */
    private Payload payload;

    public static RSocketFilterContext of(FrameType frameType,
                                          GSVRoutingMetadata routingMetadata,
                                          AppMetadata source,
                                          Payload payload) {
        RSocketFilterContext inst = new RSocketFilterContext();
        inst.frameType = frameType;
        inst.routingMetadata = routingMetadata;
        inst.source = source;
        inst.payload = payload;
        return inst;
    }

    //setter && getter
    public FrameType getFrameType() {
        return frameType;
    }

    public GSVRoutingMetadata getRoutingMetadata() {
        return routingMetadata;
    }

    public Payload getPayload() {
        return payload;
    }

    public AppMetadata getSource() {
        return source;
    }
}
