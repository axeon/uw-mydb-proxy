package uw.mydb.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.server.ProxyServer;

@SpringBootApplication
@EnableDiscoveryClient
public class MydbProxyApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run( MydbProxyApplication.class, args );
        //启动mysql客户端
        MySqlClient.start();
        //代理服务器启动
        ProxyServer.start();
    }

}
