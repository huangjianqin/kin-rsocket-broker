package org.kin.rsocket.gateway.grpc.health;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

/**
 * grpc gateway的health state
 *
 * @author huangjianqin
 * @date 2022/1/16
 */
@GRpcService
public class HealthServiceImpl extends HealthGrpc.HealthImplBase {
    private HealthCheckResponse.ServingStatus servingStatus = HealthCheckResponse.ServingStatus.SERVING;

    public void setServingStatus(HealthCheckResponse.ServingStatus servingStatus) {
        this.servingStatus = servingStatus;
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        HealthCheckResponse response = HealthCheckResponse.newBuilder().setStatus(servingStatus).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
