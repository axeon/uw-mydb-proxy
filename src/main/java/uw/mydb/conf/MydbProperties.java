package uw.mydb.conf;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mydb配置类。
 *
 * @author axeon
 */
@Configuration
@ConfigurationProperties(prefix = "uw.mydb")
public class MydbProperties {

    /**
     * mydb center主机。
     */
    private String mydbCenterHost;

    /**
     * 配置Key。
     */
    private String configKey;

    /**
     * 端口号。
     */
    private int port;

}
