package org.kin.spring.rsocket.example.requester;

import org.kin.spring.rsocket.support.SpringRSocketServiceReference;

/**
 * @author huangjianqin
 * @date 2021/8/23
 */
@SpringRSocketServiceReference(service = "org.kin.spring.rsocket.example.UserService")
public interface UserService extends org.kin.spring.rsocket.example.UserService {
}
