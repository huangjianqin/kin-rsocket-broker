FROM java:8
MAINTAINER hjq

ENV JAR_ROOT_PATH kin-rsocket-example-broker-0.1.0.0

VOLUME /tmp
ADD kin-rsocket-example-broker-0.1.0.0.tar.gz ./

ENTRYPOINT java -Dspring.config.location=$JAR_ROOT_PATH/config/ -cp .:$JAR_ROOT_PATH/lib/*:$JAR_ROOT_PATH/config/*:${CLASSPATH} org.kin.rsocket.example.broker.RSocketBrokerApplication
EXPOSE 9998 9999