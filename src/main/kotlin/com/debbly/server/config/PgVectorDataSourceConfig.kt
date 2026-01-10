package com.debbly.server.config

import liquibase.integration.spring.SpringLiquibase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
class PgVectorDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.pgvector")
    fun pgvectorDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    @Bean
    fun pgvectorDataSource(
        @Qualifier("pgvectorDataSourceProperties") properties: DataSourceProperties
    ): DataSource {
        return properties.initializeDataSourceBuilder()
            .build()
    }

    @Bean
    fun pgvectorJdbcTemplate(
        @Qualifier("pgvectorDataSource") dataSource: DataSource
    ): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }

    @Bean
    @ConfigurationProperties("spring.liquibase.pgvector")
    fun pgvectorLiquibaseProperties(): LiquibaseProperties {
        return LiquibaseProperties()
    }

    @Bean
    fun pgvectorLiquibase(
        @Qualifier("pgvectorDataSource") dataSource: DataSource,
        @Qualifier("pgvectorLiquibaseProperties") properties: LiquibaseProperties
    ): SpringLiquibase {
        val liquibase = SpringLiquibase()
        liquibase.dataSource = dataSource
        liquibase.changeLog = properties.changeLog
        liquibase.defaultSchema = properties.defaultSchema
        liquibase.isDropFirst = properties.isDropFirst
        return liquibase
    }
}
