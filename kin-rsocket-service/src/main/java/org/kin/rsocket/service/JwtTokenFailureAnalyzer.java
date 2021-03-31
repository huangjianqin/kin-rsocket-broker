package org.kin.rsocket.service;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
public class JwtTokenFailureAnalyzer extends AbstractFailureAnalyzer<JwtTokenNotFoundException> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JwtTokenNotFoundException cause) {
        return new FailureAnalysis(getDescription(cause), getAction(cause), cause);
    }

    private String getDescription(JwtTokenNotFoundException ex) {
        return "kin.rsocket.jwt-token not found in the application.yml";
    }

    private String getAction(JwtTokenNotFoundException ex) {
        //todo 生成JWT串
        return "Please contact the Ops or open RSocket Broker console http://localhost:9998/ to generate a JWT token.";
    }
}