package uw.mydb.proxy.constant;


/**
 * 全局常量配置。
 *
 * @author axeon
 */
public class GlobalConstants {

    /**
     * 协议版本
     **/
    public static final byte PROTOCOL_VERSION = 10;

    /**
     * mydb服务器版本。
     * 第一句一定要是mysql的版本号，否则很多客户端无法连接。
     **/
    public static final String SERVER_VERSION = "8.0.33-uw-mydb-3.0-20240508";

}

