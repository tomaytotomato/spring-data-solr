package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.mapping.SolrCustomConversions;
import com.tomaytotomato.data.solr.mapping.SolrMappingConverter;
import com.tomaytotomato.data.solr.repository.SolrRepositoryAutoConfiguration;
import com.tomaytotomato.data.solr.testfixtures.TestSolrDocument;
import com.tomaytotomato.data.solr.testfixtures.TestSolrDocumentRepository;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SolrAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SolrAutoConfiguration.class));

  @Configuration
  static class TestRepositoryConfiguration {
    @Bean
    SolrClient solrClient() {
      return mock(SolrClient.class);
    }
  }

  @Nested
  class BeanCreation {

    @Test
    void createsSolrClientWhenSolrClientClassIsOnClasspath() {
      contextRunner.run(ctx ->
          assertThat(ctx).hasSingleBean(SolrClient.class));
    }

    @Test
    void createsSolrTemplateWhenSolrClientClassIsOnClasspath() {
      contextRunner.run(ctx ->
          assertThat(ctx).hasSingleBean(SolrTemplate.class));
    }

    @Test
    void solrTemplateAndSolrClientAreRegisteredInTheSameContext() {
      contextRunner.run(ctx -> {
        assertThat(ctx).hasSingleBean(SolrTemplate.class);
        assertThat(ctx).hasSingleBean(SolrClient.class);
      });
    }
  }

  @Nested
  class PropertyApplication {

    @Test
    void customHostPropertyIsReflectedInProperties() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.host=http://custom-solr:8983/solr")
          .run(ctx -> {
            var standalone = ctx.getBean(SolrProperties.class).getStandalone();
            assertThat(standalone).isNotNull();
            assertThat(standalone.host()).isEqualTo("http://custom-solr:8983/solr");
          });
    }

    @Test
    void customCollectionPropertyIsReflectedInProperties() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.default-collection=products")
          .run(ctx -> {
            var standalone = ctx.getBean(SolrProperties.class).getStandalone();
            assertThat(standalone).isNotNull();
            assertThat(standalone.defaultCollection()).isEqualTo("products");
          });
    }
  }

  @Nested
  class UserProvidedBeans {

    @Test
    void userProvidedSolrClientTakesPrecedence() {
      var userClient = mock(SolrClient.class);
      contextRunner
          .withBean("customSolrClient", SolrClient.class, () -> userClient)
          .run(ctx -> {
            assertThat(ctx).hasSingleBean(SolrClient.class);
            assertThat(ctx.getBean(SolrClient.class)).isSameAs(userClient);
          });
    }

    @Test
    void userProvidedSolrTemplateTakesPrecedence() {
      var userClient = mock(SolrClient.class);
      var userTemplate = new SolrTemplate(userClient);
      contextRunner
          .withBean("customSolrClient", SolrClient.class, () -> userClient)
          .withBean("customSolrTemplate", SolrTemplate.class, () -> userTemplate)
          .run(ctx -> {
            assertThat(ctx).hasSingleBean(SolrTemplate.class);
            assertThat(ctx.getBean(SolrTemplate.class)).isSameAs(userTemplate);
          });
    }
  }

  @Nested
  class MicrometerConditionalConfiguration {

    @Configuration
    static class ObservationRegistryConfiguration {
      @Bean
      ObservationRegistry observationRegistry() {
        return ObservationRegistry.create();
      }
    }

    @Test
    void createsMicrometerSolrTemplateWhenObservationRegistryIsPresent() {
      contextRunner
          .withUserConfiguration(ObservationRegistryConfiguration.class)
          .run(ctx -> {
            assertThat(ctx).hasSingleBean(SolrTemplate.class);
            assertThat(ctx.getBean(SolrTemplate.class)).isInstanceOf(MicrometerSolrTemplate.class);
          });
    }

    @Test
    void createsPlainSolrTemplateWhenObservationRegistryIsAbsent() {
      contextRunner.run(ctx -> {
        assertThat(ctx).hasSingleBean(SolrTemplate.class);
        assertThat(ctx.getBean(SolrTemplate.class)).isNotInstanceOf(MicrometerSolrTemplate.class);
      });
    }
  }

  @Nested
  class ConverterAutoConfiguration {

    @Test
    void createsSolrCustomConversionsBeanByDefault() {
      contextRunner.run(ctx ->
          assertThat(ctx).hasSingleBean(SolrCustomConversions.class));
    }

    @Test
    void createsSolrMappingConverterBeanByDefault() {
      contextRunner.run(ctx ->
          assertThat(ctx).hasSingleBean(SolrMappingConverter.class));
    }

    @Test
    void defaultSolrCustomConversionsIsEmpty() {
      contextRunner.run(ctx -> {
        var conversions = ctx.getBean(SolrCustomConversions.class);
        assertThat(conversions.getConverters()).isEmpty();
      });
    }

    @Test
    void userDefinedSolrCustomConversionsOverridesDefault() {
      var userConversions = new SolrCustomConversions(List.of(new Object()));
      contextRunner
          .withBean("customConversions", SolrCustomConversions.class, () -> userConversions)
          .run(ctx -> {
            assertThat(ctx).hasSingleBean(SolrCustomConversions.class);
            assertThat(ctx.getBean(SolrCustomConversions.class)).isSameAs(userConversions);
          });
    }

    @Test
    void solrMappingConverterIsWiredWithSolrCustomConversions() {
      contextRunner.run(ctx -> {
        var converter = ctx.getBean(SolrMappingConverter.class);
        var conversions = ctx.getBean(SolrCustomConversions.class);
        assertThat(converter.getConversions()).isSameAs(conversions);
      });
    }
  }

  @Nested
  class ClientModeSelection {

    @Test
    void usesStandaloneClientByDefault() {
      contextRunner.run(ctx -> {
        assertThat(ctx).hasSingleBean(SolrClient.class);
        assertThat(ctx.getBean(SolrClient.class)).isNotInstanceOf(CloudSolrClient.class);
      });
    }

    @Test
    void createsCloudClientWhenCloudZkHostIsSet() {
      contextRunner
          .withPropertyValues("spring.solr.cloud.zk-host=localhost:2181")
          .run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(SolrClient.class)).isInstanceOf(CloudSolrClient.class);
          });
    }

    @Test
    void userProvidedClientTakesPrecedenceEvenWhenCloudIsConfigured() {
      var userClient = mock(SolrClient.class);
      contextRunner
          .withPropertyValues("spring.solr.cloud.zk-host=localhost:2181")
          .withBean("customSolrClient", SolrClient.class, () -> userClient)
          .run(ctx -> {
            assertThat(ctx).hasSingleBean(SolrClient.class);
            assertThat(ctx.getBean(SolrClient.class)).isSameAs(userClient);
          });
    }

    @Test
    void failsWithIllegalStateWhenBothStandaloneAndCloudAreConfigured() {
      contextRunner
          .withPropertyValues(
              "spring.solr.standalone.host=http://localhost:8983/solr",
              "spring.solr.cloud.zk-host=localhost:2181")
          .run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                .hasMessageContaining("spring.solr.standalone")
                .hasMessageContaining("spring.solr.cloud");
          });
    }
  }

  @Nested
  class RepositoryAutoConfiguration {

    private final ApplicationContextRunner repositoryContextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            SolrAutoConfiguration.class,
            SolrRepositoryAutoConfiguration.class))
        .withInitializer(ctx -> AutoConfigurationPackages.register(
            (BeanDefinitionRegistry) ctx.getBeanFactory(),
            TestSolrDocument.class.getPackageName()))
        .withUserConfiguration(TestRepositoryConfiguration.class);

    @Test
    void registersRepositoryBeanInApplicationContext() {
      repositoryContextRunner.run(ctx ->
          assertThat(ctx).hasSingleBean(TestSolrDocumentRepository.class));
    }

    @Test
    void repositoryBeanIsWiredWithAutoConfiguredTemplate() {
      repositoryContextRunner.run(ctx -> {
        assertThat(ctx).hasSingleBean(TestSolrDocumentRepository.class);
        assertThat(ctx).hasSingleBean(SolrTemplate.class);
      });
    }
  }
}
