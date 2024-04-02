package uw.mydb.conf;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import uw.mydb.mysql.MySqlClient;
import uw.mydb.proxy.ProxyServer;


/**
 * 启动配置文件。
 *
 * @author axeon
 */
@Configuration
@EnableConfigurationProperties({MydbProperties.class})
public class MydbSpringAutoConfiguration {

    /**
     * 日志.
     */
    private static final Logger log = LoggerFactory.getLogger( MydbSpringAutoConfiguration.class );

    /**
     * 关闭连接管理器,销毁全部连接池.
     */
    @PreDestroy
    public void destroy() {
        ProxyServer.stop();
        MySqlClient.stop();
        log.info( "uw-mydb destroy configuration..." );
    }

    @Bean
    public MydbConfigService mydbConfigService(final MydbProperties mydbProperties, @Qualifier("tokenRestTemplate") final RestTemplate restTemplate) {
        return new MydbConfigService( mydbProperties, restTemplate );
    }
}
