package org.kin.rsocket.service.boot;

import org.kin.rsocket.service.JwtTokenNotFoundException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * @author huangjianqin
 * @date 2021/3/31
 */
public final class JwtTokenFailureAnalyzer extends AbstractFailureAnalyzer<JwtTokenNotFoundException> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, JwtTokenNotFoundException cause) {
        return new FailureAnalysis(getDescription(cause), getAction(cause), cause);
    }

    private String getDescription(JwtTokenNotFoundException ex) {
        return "kin.rsocket.jwt-token not found in the application.yml";
    }

    private String getAction(JwtTokenNotFoundException ex) {
        return "please generate a JWT token.";
    }
}