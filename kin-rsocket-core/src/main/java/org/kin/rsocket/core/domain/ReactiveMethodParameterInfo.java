package org.kin.rsocket.core.domain;

import java.io.Serializable;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ReactiveMethodParameterInfo implements Serializable {
    private static final long serialVersionUID = -8369300953700310654L;
    /** 参数名 */
    private String name;
    /** 参数类型 */
    private String type;
    /** 参数类型中第一个泛型参数实际类型 */
    private String inferredType;
    /** 描述, todo 后续通过注解增加 */
    private String description;
    /** 参数是否必须赋值, todo 后续通过注解增加 */
    private boolean required;

    //setter && getter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInferredType() {
        return inferredType;
    }

    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
