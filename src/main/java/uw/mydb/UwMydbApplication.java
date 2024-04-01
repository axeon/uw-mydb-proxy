package uw.mydb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uw.mydb.mysql.MySqlClient;
import uw.mydb.proxy.ProxyServer;

@SpringBootApplication
public class UwMydbApplication {

    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run( UwMydbApplication.class, args );
        //启动mysql客户端
        MySqlClient.start();
        //代理服务器启动
        ProxyServer.start();
    }

}
