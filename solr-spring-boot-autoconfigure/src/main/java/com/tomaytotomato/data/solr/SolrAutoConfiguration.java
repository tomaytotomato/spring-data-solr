package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrCustomConversions;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import com.tomaytotomato.data.solr.mapping.SolrMappingConverter;
import io.micrometer.observation.ObservationRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
  @ConditionalOnClass(ObservationRegistry.class)
  @ConditionalOnBean(ObservationRegistry.class)
  static class MicrometerSolrConfiguration {

    @Bean
    @ConditionalOnMissingBean(SolrTemplate.class)
    SolrTemplate micrometerSolrTemplate(SolrClient solrClient, SolrProperties properties,
        Environment environment, ObservationRegistry observationRegistry,
        SolrMappingContext mappingContext, SolrMappingConverter mappingConverter) {
      return new MicrometerSolrTemplate(solrClient, properties.getCommitMode(), environment,
          observationRegistry, mappingContext, mappingConverter);
    }
  }

  @Bean
  @ConditionalOnMissingBean(SolrClient.class)
  SolrClient solrClient(SolrProperties properties) {
    SolrPropertiesValidator.validate(properties);
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
    var zk = parseZkHost(cloud.zkHost());
    return new CloudSolrClient.Builder(zk.hosts(), zk.chroot())
        .withDefaultCollection(cloud.defaultCollection())
        .withHttpClientBuilder(httpClientBuilder)
        .build();
  }

  // Splits a SolrCloud zk-host string of the form "host1:2181,host2:2181[/chroot]"
  // into the host list and optional chroot expected by CloudSolrClient.Builder.
  // The single-arg Builder(List<String>) overload treats its argument as Solr base URLs,
  // not ZK hosts — so we must use the two-arg form to get the ZooKeeper provider.
  private static ZkHost parseZkHost(String zkHost) {
    var slash = zkHost.indexOf('/');
    if (slash >= 0) {
      var hosts = zkHost.substring(0, slash);
      var chroot = zkHost.substring(slash);
      return new ZkHost(Arrays.asList(hosts.split(",")), Optional.of(chroot));
    }
    return new ZkHost(Arrays.asList(zkHost.split(",")), Optional.empty());
  }

  private record ZkHost(List<String> hosts, Optional<String> chroot) {
  }

  private SolrClient buildStandaloneClient(SolrProperties properties) {
    var standalone = properties.getStandalone();
    return new HttpJdkSolrClient.Builder(standalone.host())
        .withConnectionTimeout(properties.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .withRequestTimeout(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .withDefaultCollection(standalone.defaultCollection())
        .build();
  }

  @Bean
  @ConditionalOnMissingBean
  SolrDocumentResolver solrDocumentResolver(Environment environment) {
    return new SolrDocumentResolver(environment);
  }

  @Bean
  @ConditionalOnMissingBean
  SolrTemplate solrTemplate(SolrClient solrClient, SolrProperties properties,
      Environment environment, SolrMappingContext mappingContext,
      SolrMappingConverter mappingConverter) {
    return new SolrTemplate(solrClient, properties.getCommitMode(), environment, mappingContext,
        mappingConverter);
  }

  @Bean
  @ConditionalOnMissingBean
  SolrMappingContext solrMappingContext(Environment environment) {
    return new SolrMappingContext(environment);
  }

  @Bean
  @ConditionalOnMissingBean
  SolrCustomConversions solrCustomConversions() {
    return SolrCustomConversions.empty();
  }

  @Bean
  @ConditionalOnMissingBean
  SolrMappingConverter solrMappingConverter(SolrCustomConversions conversions) {
    return new SolrMappingConverter(conversions);
  }
}
