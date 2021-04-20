package org.kin.rsocket.core.event;

import org.kin.rsocket.core.domain.AppStatus;

/**
 * todo 时间处理需要优化
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public class AppStatusEvent implements CloudEventSupport {
    private static final long serialVersionUID = -1486554322602641902L;
    /** app UUID */
    private String id;
    /** app status */
    private AppStatus status;

    public static AppStatusEvent of(String id, AppStatus status) {
        AppStatusEvent inst = new AppStatusEvent();
        inst.id = id;
        inst.status = status;
        return inst;
    }

    public static AppStatusEvent stopped(String id) {
        return of(id, AppStatus.STOPPED);
    }

    public static AppStatusEvent connected(String id) {
        return of(id, AppStatus.CONNECTED);
    }

    public static AppStatusEvent serving(String id) {
        return of(id, AppStatus.SERVING);
    }

    public static AppStatusEvent down(String id) {
        return of(id, AppStatus.DOWN);
    }


    //setter && getter
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AppStatus getStatus() {
        return status;
    }

    public void setStatus(AppStatus status) {
        this.status = status;
    }
}
