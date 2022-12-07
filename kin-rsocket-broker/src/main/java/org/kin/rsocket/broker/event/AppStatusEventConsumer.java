package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketEndpoint;
import org.kin.rsocket.broker.RSocketServiceManager;
import org.kin.rsocket.core.event.AbstractCloudEventConsumer;
import org.kin.rsocket.core.event.AppStatusEvent;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public final class AppStatusEventConsumer extends AbstractCloudEventConsumer<AppStatusEvent> {
    @Autowired
    private RSocketServiceManager serviceManager;

    @Override
    public void consume(CloudEvent cloudEvent, AppStatusEvent event) {
        //安全验证，确保appStatusEvent的ID和cloud source来源的id一致
        String appId = event.getId();
        RSocketEndpoint rsocketEndpoint = serviceManager.getByUUID(appId);
        if (Objects.isNull(rsocketEndpoint)) {
            return;
        }

        switch (event.getStatus()) {
            case SERVING:
                //app serving
                rsocketEndpoint.publishServices();
                break;
            case DOWN:
                //app out of service
                rsocketEndpoint.hideServices();
                break;
            case STOPPED:
                //app stopped
                rsocketEndpoint.forceDispose();
                break;
            default:
                //like app connected
                //do nothing
        }
    }
}
