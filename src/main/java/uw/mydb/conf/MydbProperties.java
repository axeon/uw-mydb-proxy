package uw.mydb.conf;

import org.springframework.beans.factory.annotation.Value;
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
     * 应用名称
     */
    @Value("${spring.application.name}")
    private String appName;

    /**
     * 应用版本
     */
    @Value("${project.version}")
    private String appVersion;

    /**
     * app主机
     */
    @Value("${spring.cloud.nacos.discovery.ip}")
    private String proxyHost;

    /**
     * mydb center主机。
     */
    private String mydbCenterHost="http://uw-mydb-center";

    /**
     * 配置Key。
     */
    private String configKey = "default";

    /**
     * 端口号。
     */
    private int proxyPort = 3300;

    /**
     * 慢查询门限值。
     */
    private long slowQueryMillis = 10_000L;


    public String getMydbCenterHost() {
        return mydbCenterHost;
    }

    public void setMydbCenterHost(String mydbCenterHost) {
        this.mydbCenterHost = mydbCenterHost;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public long getSlowQueryMillis() {
        return slowQueryMillis;
    }

    public void setSlowQueryMillis(long slowQueryMillis) {
        this.slowQueryMillis = slowQueryMillis;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }
}
