package com.tomaytotomato.data.solr;

import java.time.Duration;
import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SolrPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SolrAutoConfiguration.class));

  @Nested
  class Defaults {

    @Test
    void standaloneIsNullWhenNoStandalonePropertiesSet() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getStandalone()).isNull());
    }

    @Test
    void cloudIsNullByDefault() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getCloud()).isNull());
    }

    @Test
    void connectionTimeoutDefaultsToTenSeconds() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getConnectionTimeout())
              .isEqualTo(Duration.ofSeconds(10)));
    }

    @Test
    void requestTimeoutDefaultsToSixtySeconds() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getRequestTimeout())
              .isEqualTo(Duration.ofSeconds(60)));
    }

    @Test
    void defaultCollectionIsNullWhenNoModeConfigured() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getDefaultCollection()).isNull());
    }
  }

  @Nested
  class StandaloneBinding {

    @Test
    void customHostBindsFromProperty() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.host=http://solr.example.com:8983/solr")
          .run(ctx -> {
            var standalone = ctx.getBean(SolrProperties.class).getStandalone();
            assertThat(standalone).isNotNull();
            assertThat(standalone.host()).isEqualTo("http://solr.example.com:8983/solr");
          });
    }

    @Test
    void defaultCollectionBindsFromProperty() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.default-collection=my-collection")
          .run(ctx -> {
            var standalone = ctx.getBean(SolrProperties.class).getStandalone();
            assertThat(standalone).isNotNull();
            assertThat(standalone.defaultCollection()).isEqualTo("my-collection");
          });
    }

    @Test
    void hostDefaultsWhenOnlyCollectionIsSet() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.default-collection=books")
          .run(ctx -> {
            var standalone = ctx.getBean(SolrProperties.class).getStandalone();
            assertThat(standalone).isNotNull();
            assertThat(standalone.host()).isEqualTo("http://localhost:8983/solr");
          });
    }

    @Test
    void defaultCollectionIsExposedByTopLevelGetter() {
      contextRunner
          .withPropertyValues("spring.solr.standalone.default-collection=products")
          .run(ctx ->
              assertThat(ctx.getBean(SolrProperties.class).getDefaultCollection())
                  .isEqualTo("products"));
    }
  }

  @Nested
  class CloudBinding {

    private final ApplicationContextRunner cloudContextRunner = contextRunner
        .withBean("solrClient", SolrClient.class, () -> mock(SolrClient.class));

    @Test
    void zkHostBindsFromProperty() {
      cloudContextRunner
          .withPropertyValues("spring.solr.cloud.zk-host=localhost:2181")
          .run(ctx -> {
            var cloud = ctx.getBean(SolrProperties.class).getCloud();
            assertThat(cloud).isNotNull();
            assertThat(cloud.zkHost()).isEqualTo("localhost:2181");
          });
    }

    @Test
    void cloudDefaultCollectionBindsFromProperty() {
      cloudContextRunner
          .withPropertyValues(
              "spring.solr.cloud.zk-host=localhost:2181",
              "spring.solr.cloud.default-collection=catalog")
          .run(ctx -> {
            var cloud = ctx.getBean(SolrProperties.class).getCloud();
            assertThat(cloud).isNotNull();
            assertThat(cloud.defaultCollection()).isEqualTo("catalog");
          });
    }

    @Test
    void cloudDefaultCollectionIsExposedByTopLevelGetter() {
      cloudContextRunner
          .withPropertyValues(
              "spring.solr.cloud.zk-host=localhost:2181",
              "spring.solr.cloud.default-collection=catalog")
          .run(ctx ->
              assertThat(ctx.getBean(SolrProperties.class).getDefaultCollection())
                  .isEqualTo("catalog"));
    }

    @Test
    void cloudDefaultCollectionTakesPrecedenceOverStandaloneInTopLevelGetter() {
      var cloud = new SolrProperties.CloudProperties("localhost:2181", "cloud-col");
      var standalone = new SolrProperties.StandaloneProperties("http://localhost:8983/solr", "standalone-col");
      var properties = new SolrProperties(standalone, cloud, Duration.ofSeconds(10), Duration.ofSeconds(60), CommitMode.NONE, 10);
      assertThat(properties.getDefaultCollection()).isEqualTo("cloud-col");
    }
  }

  @Nested
  class SharedProperties {

    @Test
    void connectionTimeoutBindsFromProperty() {
      contextRunner
          .withPropertyValues("spring.solr.connection-timeout=5s")
          .run(ctx ->
              assertThat(ctx.getBean(SolrProperties.class).getConnectionTimeout())
                  .isEqualTo(Duration.ofSeconds(5)));
    }

    @Test
    void requestTimeoutBindsFromProperty() {
      contextRunner
          .withPropertyValues("spring.solr.request-timeout=30s")
          .run(ctx ->
              assertThat(ctx.getBean(SolrProperties.class).getRequestTimeout())
                  .isEqualTo(Duration.ofSeconds(30)));
    }
  }

  @Nested
  class DefaultPageSize {

    @Test
    void defaultPageSizeIsTenWhenNotConfigured() {
      contextRunner.run(ctx ->
          assertThat(ctx.getBean(SolrProperties.class).getDefaultPageSize()).isEqualTo(10));
    }

    @Test
    void customDefaultPageSizeBindsFromProperty() {
      contextRunner
          .withPropertyValues("spring.solr.default-page-size=50")
          .run(ctx ->
              assertThat(ctx.getBean(SolrProperties.class).getDefaultPageSize()).isEqualTo(50));
    }
  }
}
