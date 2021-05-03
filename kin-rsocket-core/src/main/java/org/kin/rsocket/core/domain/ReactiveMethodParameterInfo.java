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
    /** 参数描述 */
    private String description;
    /** 参数是否必须赋值, todo 优化:后续通过注解增加 */
    private boolean required;

    //--------------------------------builder--------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final ReactiveMethodParameterInfo reactiveMethodParameterInfo = new ReactiveMethodParameterInfo();

        public Builder name(String name) {
            reactiveMethodParameterInfo.name = name;
            return this;
        }

        public Builder type(String type) {
            reactiveMethodParameterInfo.type = type;
            return this;
        }

        public Builder inferredType(String inferredType) {
            reactiveMethodParameterInfo.inferredType = inferredType;
            return this;
        }

        public Builder description(String description) {
            reactiveMethodParameterInfo.description = description;
            return this;
        }

        public Builder required(boolean required) {
            reactiveMethodParameterInfo.required = required;
            return this;
        }

        public ReactiveMethodParameterInfo build() {
            return reactiveMethodParameterInfo;
        }
    }

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
