package com.custos.config;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@Profile("!test")
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer schedulerFactoryBeanCustomizer(DataSource dataSource) {
        return schedulerFactoryBean -> {
            schedulerFactoryBean.setDataSource(dataSource);
            Properties props = new Properties();
            props.put("org.quartz.scheduler.instanceName", "CustosClusteredScheduler");
            props.put("org.quartz.scheduler.instanceId", "AUTO");
            props.put("org.quartz.jobStore.class", "org.springframework.scheduling.quartz.LocalDataSourceJobStore");
            props.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
            props.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
            props.put("org.quartz.jobStore.isClustered", "true");
            props.put("org.quartz.jobStore.clusterCheckinInterval", "20000");
            props.put("org.quartz.jobStore.misfireThreshold", "60000");
            props.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
            props.put("org.quartz.threadPool.threadCount", "10");
            props.put("org.quartz.threadPool.threadPriority", "5");
            schedulerFactoryBean.setQuartzProperties(props);
        };
    }
}
