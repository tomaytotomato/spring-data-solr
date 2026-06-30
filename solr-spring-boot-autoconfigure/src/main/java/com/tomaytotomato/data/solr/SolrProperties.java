package com.tomaytotomato.data.solr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.data.solr")
public class SolrProperties {

  @Valid
  private final StandaloneProperties standalone;
  @Valid
  private final CloudProperties cloud;
  private final Duration connectionTimeout;
  private final Duration requestTimeout;
  private final CommitMode commitMode;

  /**
   * Default page size used when a repository method returns a paged result
   * ({@code Page}, {@code HighlightPage}, or {@code FacetPage}) but the caller
   * does not supply a {@link org.springframework.data.domain.Pageable} argument.
   * <p>
   * A {@code WARN} is logged each time the default is applied so that unexpected
   * result truncation is visible in logs. To suppress the warning, always pass an
   * explicit {@code Pageable} to page-returning repository methods.
   * <p>
   * Configure via {@code spring.data.solr.default-page-size} in {@code application.yml}.
   * Defaults to {@code 10} for backwards compatibility.
   */
  private final int defaultPageSize;

  public SolrProperties(
      StandaloneProperties standalone,
      CloudProperties cloud,
      @DefaultValue("10s") Duration connectionTimeout,
      @DefaultValue("60s") Duration requestTimeout,
      @DefaultValue("NONE") CommitMode commitMode,
      @DefaultValue("10") int defaultPageSize) {
    this.standalone = standalone;
    this.cloud = cloud;
    this.connectionTimeout = connectionTimeout;
    this.requestTimeout = requestTimeout;
    this.commitMode = commitMode;
    this.defaultPageSize = defaultPageSize;
  }

  public StandaloneProperties getStandalone() {
    return standalone;
  }

  public CloudProperties getCloud() {
    return cloud;
  }

  public Duration getConnectionTimeout() {
    return connectionTimeout;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public CommitMode getCommitMode() {
    return commitMode;
  }

  public int getDefaultPageSize() {
    return defaultPageSize;
  }

  public String getDefaultCollection() {
    if (cloud != null) {
      return cloud.defaultCollection();
    }
    if (standalone != null) {
      return standalone.defaultCollection();
    }
    return null;
  }

  public record StandaloneProperties(
      @DefaultValue("http://localhost:8983/solr")
      @NotBlank(message = "spring.data.solr.standalone.host must not be blank")
      @Pattern(
          regexp = "^https?://[^\\s]+/solr/?$",
          message = "spring.data.solr.standalone.host must start with http(s):// and end with /solr (was: ${validatedValue})")
      String host,
      String defaultCollection) {}

  public record CloudProperties(
      @NotBlank(message = "spring.data.solr.cloud.zk-host must not be blank when spring.data.solr.cloud is configured")
      String zkHost,
      String defaultCollection) {}
}
