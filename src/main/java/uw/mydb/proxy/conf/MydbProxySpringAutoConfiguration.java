package uw.mydb.proxy.conf;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.server.ProxyServer;


/**
 * mydb proxy Spring 自动配置入口。
 * <p>
 * 启用 {@link MydbProxyProperties} 配置绑定，注册 {@link MydbProxyConfigService} Bean，
 * 并在容器销毁时通过 {@link #destroy()} 优雅关闭 proxy server 与后端连接池。
 *
 * @author axeon
 */
@Configuration
@EnableConfigurationProperties({MydbProxyProperties.class})
public class MydbProxySpringAutoConfiguration {

    /**
     * 日志.
     */
    private static final Logger log = LoggerFactory.getLogger( MydbProxySpringAutoConfiguration.class );

    /**
     * 容器销毁回调：先停 proxy server（关闭前端 listener 与统计调度），再停 MySqlClient（关闭全部后端连接池）。
     */
    @PreDestroy
    public void destroy() {
        ProxyServer.stop();
        MySqlClient.stop();
        log.info( "uw-mydb-proxy destroy configuration..." );
    }

    /**
     * 注册 {@link MydbProxyConfigService} 单例，注入 proxy 属性与带鉴权的 RestClient。
     *
     * @param mydbProxyProperties proxy 配置属性
     * @param authRestClient      带鉴权拦截器的 RestClient（来自 uw-auth-client）
     * @return 配置管理器实例
     */
    @Bean
    public MydbProxyConfigService mydbConfigService(final MydbProxyProperties mydbProxyProperties, @Qualifier("authRestClient") final RestClient authRestClient) {
        return new MydbProxyConfigService( mydbProxyProperties, authRestClient );
    }
}
