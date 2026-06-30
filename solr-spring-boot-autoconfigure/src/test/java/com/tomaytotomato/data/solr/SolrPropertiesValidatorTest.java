package com.tomaytotomato.data.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tomaytotomato.data.solr.SolrProperties.CloudProperties;
import com.tomaytotomato.data.solr.SolrProperties.StandaloneProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SolrPropertiesValidatorTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(SolrPropertiesValidator.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  private static SolrProperties props(StandaloneProperties standalone, CloudProperties cloud) {
    return new SolrProperties(standalone, cloud, Duration.ofSeconds(10), Duration.ofSeconds(60),
        CommitMode.NONE, 10);
  }

  @Nested
  class Failures {

    @Test
    void throwsWhenNeitherStandaloneNorCloudIsConfigured() {
      var properties = props(null, null);

      assertThatThrownBy(() -> SolrPropertiesValidator.validate(properties))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("No Solr configuration found")
          .hasMessageContaining("spring.data.solr.standalone.host")
          .hasMessageContaining("spring.data.solr.cloud.zk-host");
    }

    @Test
    void throwsWhenBothStandaloneAndCloudAreConfigured() {
      var properties = props(
          new StandaloneProperties("http://localhost:8983/solr", "books"),
          new CloudProperties("localhost:2181", "books"));

      assertThatThrownBy(() -> SolrPropertiesValidator.validate(properties))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Ambiguous Solr configuration")
          .hasMessageContaining("spring.data.solr.standalone")
          .hasMessageContaining("spring.data.solr.cloud");
    }
  }

  @Nested
  class Successes {

    @Test
    void passesWhenOnlyStandaloneIsConfiguredWithDefaultCollection() {
      var properties = props(
          new StandaloneProperties("http://localhost:8983/solr", "books"),
          null);

      assertThatCode(() -> SolrPropertiesValidator.validate(properties)).doesNotThrowAnyException();
      assertThat(warningMessages()).isEmpty();
    }

    @Test
    void passesWhenOnlyCloudIsConfiguredWithDefaultCollection() {
      var properties = props(
          null,
          new CloudProperties("localhost:2181", "books"));

      assertThatCode(() -> SolrPropertiesValidator.validate(properties)).doesNotThrowAnyException();
      assertThat(warningMessages()).isEmpty();
    }
  }

  @Nested
  class Warnings {

    @Test
    void warnsWhenStandaloneDefaultCollectionIsMissing() {
      var properties = props(
          new StandaloneProperties("http://localhost:8983/solr", null),
          null);

      SolrPropertiesValidator.validate(properties);

      assertThat(warningMessages())
          .anySatisfy(msg -> assertThat(msg)
              .contains("spring.data.solr.standalone.default-collection"));
    }

    @Test
    void warnsWhenStandaloneDefaultCollectionIsBlank() {
      var properties = props(
          new StandaloneProperties("http://localhost:8983/solr", "  "),
          null);

      SolrPropertiesValidator.validate(properties);

      assertThat(warningMessages())
          .anySatisfy(msg -> assertThat(msg)
              .contains("spring.data.solr.standalone.default-collection"));
    }

    @Test
    void warnsWhenCloudDefaultCollectionIsMissing() {
      var properties = props(
          null,
          new CloudProperties("localhost:2181", null));

      SolrPropertiesValidator.validate(properties);

      assertThat(warningMessages())
          .anySatisfy(msg -> assertThat(msg)
              .contains("spring.data.solr.cloud.default-collection"));
    }
  }

  private java.util.List<String> warningMessages() {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
