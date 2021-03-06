package org.kin.rsocket.core.domain;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author huangjianqin
 * @date 2021/3/30
 */
public class ReactiveMethodInfo implements Serializable {
    private static final long serialVersionUID = 4422311675833851700L;
    /** method name */
    private String name;
    /** 接口描述 */
    private String description;
    /** 方法是否弃用 */
    private boolean deprecated;
    /** 返回类型 */
    private String returnType;
    /** 返回类型中第一个泛型参数实际类型 */
    private String returnInferredType;
    /** 方法参数信息 */
    private List<ReactiveMethodParameterInfo> parameters = Collections.emptyList();

    //--------------------------------builder--------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /** builder **/
    public static class Builder {
        private final ReactiveMethodInfo reactiveMethodInfo = new ReactiveMethodInfo();

        public Builder name(String name) {
            reactiveMethodInfo.name = name;
            return this;
        }

        public Builder description(String description) {
            reactiveMethodInfo.description = description;
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            reactiveMethodInfo.deprecated = deprecated;
            return this;
        }

        public Builder returnType(String returnType) {
            reactiveMethodInfo.returnType = returnType;
            return this;
        }

        public Builder returnInferredType(String returnInferredType) {
            reactiveMethodInfo.returnInferredType = returnInferredType;
            return this;
        }

        public Builder parameters(List<ReactiveMethodParameterInfo> parameters) {
            reactiveMethodInfo.parameters = parameters;
            return this;
        }

        public ReactiveMethodInfo build() {
            return reactiveMethodInfo;
        }
    }

    //setter && getter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getReturnInferredType() {
        return returnInferredType;
    }

    public void setReturnInferredType(String returnInferredType) {
        this.returnInferredType = returnInferredType;
    }

    public List<ReactiveMethodParameterInfo> getParameters() {
        return parameters;
    }

    public void setParameters(List<ReactiveMethodParameterInfo> parameters) {
        this.parameters = parameters;
    }
}

