package dev.zerokotlin.core.config

import org.hibernate.cfg.SchemaToolingSettings
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean

@AutoConfiguration(beforeName = ["org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"])
@ConditionalOnZeroJpa
internal class JpaAutoConfiguration {
    @Bean
    fun zeroHibernateProperties(): HibernatePropertiesCustomizer = HibernatePropertiesCustomizer { properties ->
        properties.putIfAbsent(SchemaToolingSettings.HBM2DDL_AUTO, "none")
    }
}
