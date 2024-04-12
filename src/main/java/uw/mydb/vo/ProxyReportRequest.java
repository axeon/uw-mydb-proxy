package uw.mydb.vo;

/**
 * mydb报告请求。
 */
public class ProxyReportRequest {

    /**
     * id
     */
    private long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用版本
     */
    private String appVersion;

    /**
     * jvm内存总数
     */
    private long jvmMemMax;

    /**
     * jvm内存总数
     */
    private long jvmMemTotal;

    /**
     * jvm空闲内存
     */
    private long jvmMemFree;

    /**
     * 活跃线程
     */
    private int threadActive;

    /**
     * 峰值线程
     */
    private int threadPeak;

    /**
     * 守护线程
     */
    private int threadDaemon;

    /**
     * 累计启动线程
     */
    private long threadStarted;

}
