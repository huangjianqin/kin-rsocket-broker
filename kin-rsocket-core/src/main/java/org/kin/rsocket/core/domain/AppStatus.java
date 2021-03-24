package org.kin.rsocket.core.domain;

/**
 * application service status
 *
 * @author huangjianqin
 * @date 2021/3/24
 */
public enum AppStatus {
    /** app service stopped */
    STOPPED(-1, "Stopped"),
    /** app service Connected */
    CONNECTED(0, "Connected"),
    /** app service Serving */
    SERVING(1, "Serving"),
    /** app service OutOfService */
    OUT_OF_SERVICE(2, "OutOfService"),
    ;

    public static final AppStatus[] values = values();
    /** 标识 */
    private final int id;
    /** 描述 */
    private final String desc;

    AppStatus(int id, String desc) {
        this.id = id;
        this.desc = desc;
    }

    /**
     * 根据唯一id获取{@link AppStatus}
     */
    public static AppStatus get(int id) {
        for (AppStatus appStatus : values) {
            if (appStatus.getId() == id) {
                return appStatus;
            }
        }

        throw new IllegalArgumentException("unknown AppStatus id: " + id);
    }

    /**
     * 根据唯一id获取{@link AppStatus}描述
     */
    public static String getDesc(int id) {
        try {
            return get(id).desc;
        } catch (Exception e) {
            //do nothing
        }

        return "Unknown";
    }

    //getter
    public int getId() {
        return id;
    }

    public String getDesc() {
        return desc;
    }
}
