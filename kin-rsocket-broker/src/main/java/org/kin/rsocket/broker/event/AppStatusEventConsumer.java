package org.kin.rsocket.broker.event;

import io.cloudevents.CloudEvent;
import org.kin.rsocket.broker.RSocketService;
import org.kin.rsocket.broker.RSocketServiceRegistry;
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
    private RSocketServiceRegistry serviceRegistry;

    @Override
    public void consume(CloudEvent cloudEvent, AppStatusEvent event) {
        //安全验证，确保appStatusEvent的ID和cloud source来源的id一致
        String appId = event.getId();
        RSocketService rsocketService = serviceRegistry.getByUUID(appId);
        if (Objects.isNull(rsocketService)) {
            return;
        }

        switch (event.getStatus()) {
            case SERVING:
                //app serving
                rsocketService.publishServices();
                break;
            case DOWN:
                //app out of service
                rsocketService.hideServices();
                break;
            case STOPPED:
                //app stopped
                rsocketService.forceDispose();
                break;
            default:
                //like app connected
                //do nothing
        }
    }
}
