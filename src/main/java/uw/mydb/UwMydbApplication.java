package uw.mydb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uw.mydb.proxy.ProxyServer;

@SpringBootApplication
public class UwMydbApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run( UwMydbApplication.class, args );
        //代理服务器启动
        ProxyServer.start();
    }

}
