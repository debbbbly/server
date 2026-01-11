package com.debbly.server.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import liquibase.integration.spring.SpringLiquibase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = [
        "com.debbly.server.user.repository",
        "com.debbly.server.claim.repository",
        "com.debbly.server.claim.user.repository",
        "com.debbly.server.claim.top",
        "com.debbly.server.claim.topic.repository",
        "com.debbly.server.category.repository",
        "com.debbly.server.stage.repository",
        "com.debbly.server.backstage.repository",
        "com.debbly.server.settings.repository",
        "com.debbly.server.followers.repository"
    ],
    entityManagerFactoryRef = "primaryEntityManagerFactory",
    transactionManagerRef = "primaryTransactionManager"
)
class PrimaryDataSourceConfig {

    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource")
    fun primaryDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Primary
    @Bean
    fun primaryDataSource(
        @Qualifier("primaryDataSourceProperties") properties: DataSourceProperties
    ): DataSource {
        return properties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Primary
    @Bean
    fun primaryEntityManagerFactory(
        builder: EntityManagerFactoryBuilder,
        @Qualifier("primaryDataSource") dataSource: DataSource
    ): LocalContainerEntityManagerFactoryBean {
        val properties = mapOf(
            "hibernate.hbm2ddl.auto" to "none"
        )
        return builder
            .dataSource(dataSource)
            .packages(
                "com.debbly.server.user",
                "com.debbly.server.claim",
                "com.debbly.server.category",
                "com.debbly.server.stage",
                "com.debbly.server.backstage",
                "com.debbly.server.settings",
                "com.debbly.server.followers"
            )
            .persistenceUnit("primary")
            .properties(properties)
            .build()
    }

    @Primary
    @Bean
    fun primaryTransactionManager(
        @Qualifier("primaryEntityManagerFactory") entityManagerFactory: EntityManagerFactory
    ): PlatformTransactionManager {
        return JpaTransactionManager(entityManagerFactory)
    }

    @Primary
    @Bean
    @ConfigurationProperties("spring.liquibase")
    fun primaryLiquibaseProperties(): LiquibaseProperties {
        return LiquibaseProperties()
    }

    @Primary
    @Bean
    fun liquibase(
        @Qualifier("primaryDataSource") dataSource: DataSource,
        @Qualifier("primaryLiquibaseProperties") properties: LiquibaseProperties
    ): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.dataSource = dataSource
        liquibase.changeLog = properties.changeLog
        liquibase.defaultSchema = properties.defaultSchema
        liquibase.isDropFirst = properties.isDropFirst
        return liquibase
    }
}
