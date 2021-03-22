package org.kin.rsocket.core.event;

/**
 * @author huangjianqin
 * @date 2021/3/23
 */
public class CloudEventReply {
    /** event id */
    private String eventId;
    /** event type */
    private String eventType;
    /** todo */
    private Long timestamp;
    /** todo */
    private boolean result;
    /** tips */
    private String message;
    /** application name */
    private String appName;
    /** application uuid */
    private String uuid;

    //setter && getter
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
