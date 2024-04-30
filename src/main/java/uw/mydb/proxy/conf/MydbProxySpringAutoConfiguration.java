package uw.mydb.proxy.conf;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uw.mydb.proxy.mysql.MySqlClient;
import uw.mydb.proxy.server.ProxyServer;


/**
 * 启动配置文件。
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
     * 关闭连接管理器,销毁全部连接池.
     */
    @PreDestroy
    public void destroy() {
        ProxyServer.stop();
        MySqlClient.stop();
        log.info( "uw-mydb-proxy destroy configuration..." );
    }

    @Bean
    public MydbProxyConfigService mydbConfigService(final MydbProxyProperties mydbProxyProperties, @Qualifier("tokenRestTemplate") final RestTemplate restTemplate) {
        return new MydbProxyConfigService( mydbProxyProperties, restTemplate );
    }
}
