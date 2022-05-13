package org.kin.rsocket.example.grpc.consumer;

import com.google.protobuf.StringValue;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.kin.rsocket.example.ReactorUserServiceGrpc;

/**
 * @author huangjianqin
 * @date 2022/1/16
 */
public class GrpcRequesterApplication {
    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("0.0.0.0", 9302).usePlaintext().build();

        ReactorUserServiceGrpc.ReactorUserServiceStub stub = ReactorUserServiceGrpc.newReactorStub(channel);
        stub.findByPb(StringValue.of("A")).subscribe(System.out::println);
        Thread.sleep(1_000);
    }
}
