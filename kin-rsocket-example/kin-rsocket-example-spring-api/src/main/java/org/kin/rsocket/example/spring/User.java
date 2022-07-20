package org.kin.rsocket.example.spring;

import java.io.Serializable;

/**
 * @author huangjianqin
 * @date 2021/4/9
 */
public class User implements Serializable {
    private static final long serialVersionUID = -7765669138848554424L;

    private String name;
    private int age;

    public static User of(String name, int age) {
        User inst = new User();
        inst.name = name;
        inst.age = age;
        return inst;
    }

    //setter && getter
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
