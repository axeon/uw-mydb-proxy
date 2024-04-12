package uw.mydb.vo;

/**
 * 报告response。
 */
public class ProxyReportResponse {

    /**
     * proxyId。
     */
    private long proxyId;

    /**
     * 状态。
     */
    private int state;

    /**
     * 消息。
     */
    private String message;

    public long getProxyId() {
        return proxyId;
    }

    public void setProxyId(long proxyId) {
        this.proxyId = proxyId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
