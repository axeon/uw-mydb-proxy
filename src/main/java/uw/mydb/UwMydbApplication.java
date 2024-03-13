package uw.mydb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uw.mydb.mysql.MySqlClusterManager;
import uw.mydb.proxy.ProxyServer;
import uw.mydb.route.RouteManager;

@SpringBootApplication
public class UwMydbApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(UwMydbApplication.class, args);
        //启动mysql后端服务器集群。
        MySqlClusterManager.init();
        //启动mysql group心跳
        MySqlClusterManager.start();
        //初始化路由管理器。
        RouteManager.init();
        //代理服务器启动
        ProxyServer.start();
    }

}
