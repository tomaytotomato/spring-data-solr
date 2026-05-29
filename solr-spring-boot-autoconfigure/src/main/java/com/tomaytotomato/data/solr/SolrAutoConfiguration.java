package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrCustomConversions;
import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import com.tomaytotomato.data.solr.mapping.SolrMappingConverter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(SolrClient.class)
@EnableConfigurationProperties(SolrProperties.class)
public class SolrAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  @ConditionalOnBean(MeterRegistry.class)
  static class MicrometerSolrConfiguration {

    @Bean
    @ConditionalOnMissingBean(SolrTemplate.class)
    SolrTemplate micrometerSolrTemplate(SolrClient solrClient, SolrProperties properties,
        Environment environment, MeterRegistry meterRegistry) {
      return new MicrometerSolrTemplate(solrClient, properties.getCommitMode(), environment, meterRegistry);
    }
  }

  @Bean
  @ConditionalOnMissingBean(SolrClient.class)
  public SolrClient solrClient(SolrProperties properties) {
    if (properties.getCloud() != null && properties.getStandalone() != null) {
      throw new IllegalStateException(
          "Ambiguous Solr configuration: both 'spring.solr.standalone' and 'spring.solr.cloud' are set. Remove one.");
    }
    if (properties.getCloud() != null) {
      return buildCloudClient(properties);
    }
    return buildStandaloneClient(properties);
  }

  private SolrClient buildCloudClient(SolrProperties properties) {
    var cloud = properties.getCloud();
    var httpClientBuilder = new HttpJdkSolrClient.Builder()
        .withConnectionTimeout(properties.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .withRequestTimeout(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
    return new CloudSolrClient.Builder(List.of(cloud.zkHost()))
        .withDefaultCollection(cloud.defaultCollection())
        .withHttpClientBuilder(httpClientBuilder)
        .build();
  }

  private SolrClient buildStandaloneClient(SolrProperties properties) {
    var standalone = properties.getStandalone();
    var host = standalone != null ? standalone.host() : "http://localhost:8983/solr";
    var collection = standalone != null ? standalone.defaultCollection() : null;
    return new HttpJdkSolrClient.Builder(host)
        .withConnectionTimeout(properties.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .withRequestTimeout(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .withDefaultCollection(collection)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  public SolrTemplate solrTemplate(SolrClient solrClient, SolrProperties properties, Environment environment) {
    return new SolrTemplate(solrClient, properties.getCommitMode(), environment);
  }

  @Bean
  @ConditionalOnMissingBean
  public SolrMappingContext solrMappingContext() {
    return new SolrMappingContext();
  }

  @Bean
  @ConditionalOnMissingBean
  public SolrCustomConversions solrCustomConversions() {
    return SolrCustomConversions.empty();
  }

  @Bean
  @ConditionalOnMissingBean
  public SolrMappingConverter solrMappingConverter(SolrCustomConversions conversions) {
    return new SolrMappingConverter(conversions);
  }
}
