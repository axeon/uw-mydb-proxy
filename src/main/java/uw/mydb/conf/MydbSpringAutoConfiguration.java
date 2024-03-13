package uw.mydb.conf;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import uw.mydb.vo.MydbFullConfig;


/**
 * 启动配置文件。
 *
 * @author axeon
 */
@Configuration
@EnableConfigurationProperties({MydbFullConfig.class})
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
        log.info( "uw.mydb destroy configuration..." );
    }
}
