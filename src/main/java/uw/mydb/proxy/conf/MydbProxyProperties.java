package uw.mydb.proxy.conf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mydb proxy 配置属性，绑定 {@code uw.mydb.proxy.*} 前缀。
 * <p>
 * 启动时由 {@link MydbProxySpringAutoConfiguration} 通过 {@link EnableConfigurationProperties} 注入，
 * 运行期只读。
 *
 * @author axeon
 */
@Configuration
@ConfigurationProperties(prefix = "uw.mydb.proxy")
public class MydbProxyProperties {

    /**
     * 应用名称（来自 {@code spring.application.name}），用于 proxy 运行统计上报。
     */
    @Value("${spring.application.name}")
    private String appName;

    /**
     * 应用版本（来自 {@code project.version}），用于 proxy 运行统计上报。
     */
    @Value("${project.version}")
    private String appVersion;

    /**
     * proxy 对外暴露的主机 IP（来自 {@code spring.cloud.nacos.discovery.ip}），用于注册到 center。
     */
    @Value("${spring.cloud.nacos.discovery.ip}")
    private String proxyHost;

    /**
     * mydb-center 服务地址（含 scheme），proxy 通过它拉取配置与上报统计。默认 {@code http://uw-mydb-center}。
     */
    private String mydbCenterHost="http://uw-mydb-center";

    /**
     * 配置 Key，用于在 center 区分不同 proxy 集群（多租户/多环境），默认 "default"。
     */
    private String configKey = "default";

    /**
     * proxy 监听端口（对外模拟 MySQL Server），默认 3306 体系下的 3300。
     */
    private int proxyPort = 3300;

    /**
     * 慢查询阈值（毫秒），SQL 执行耗时超过此值则上报慢 SQL。默认 10 秒。
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
