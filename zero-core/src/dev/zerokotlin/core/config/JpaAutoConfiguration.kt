package dev.zerokotlin.core.config

import org.hibernate.cfg.SchemaToolingSettings
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean

/** 启用 JPA 时保留 Flyway 对数据库结构的默认管理职责。 */
@AutoConfiguration(beforeName = ["org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"])
@ConditionalOnZeroJpa
internal class JpaAutoConfiguration {
    /**
     * 为未显式设置的 Hibernate DDL 策略填入 `none`。
     *
     * @return 在创建 JPA 工厂前填充 Hibernate 默认属性的定制器。
     */
    @Bean
    fun zeroHibernateProperties(): HibernatePropertiesCustomizer = HibernatePropertiesCustomizer { properties ->
        properties.putIfAbsent(SchemaToolingSettings.HBM2DDL_AUTO, "none")
    }
}
