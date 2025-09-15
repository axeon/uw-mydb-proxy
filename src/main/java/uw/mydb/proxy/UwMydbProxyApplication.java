package uw.mydb.proxy;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.server.ProxyServer;

@SpringBootApplication
@EnableDiscoveryClient
public class UwMydbProxyApplication {

    public static void main(String[] args) throws InterruptedException {
        new SpringApplicationBuilder(UwMydbProxyApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        //启动mysql客户端
        MySqlClient.start();
        //代理服务器启动
        ProxyServer.start();
    }

}
