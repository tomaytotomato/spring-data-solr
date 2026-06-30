package com.tomaytotomato.data.solr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates cross-field rules on {@link SolrProperties} that bean validation
 * annotations alone cannot express, and warns on configuration that is technically
 * valid but likely to cause confusion at runtime.
 *
 * <p>Field-level constraints (non-blank host, well-formed Solr URL, non-blank
 * ZooKeeper host) are enforced declaratively by Jakarta Bean Validation on
 * {@link SolrProperties} itself and surface as binding failures. This validator
 * handles the rules that span fields:
 *
 * <ul>
 *   <li>At least one of {@code spring.data.solr.standalone} or {@code spring.data.solr.cloud}
 *       must be configured — there is no implicit localhost fallback.</li>
 *   <li>It is an error to configure both simultaneously; the operator must pick a
 *       mode explicitly.</li>
 *   <li>A missing {@code default-collection} on the active mode is allowed but
 *       logged as a warning, since most repository and health-check flows assume
 *       one is set.</li>
 * </ul>
 *
 * <p>Invoked from {@link SolrAutoConfiguration#solrClient(SolrProperties)} before
 * any {@link org.apache.solr.client.solrj.SolrClient} is built so misconfiguration
 * fails fast at startup.
 */
final class SolrPropertiesValidator {

  private static final Logger log = LoggerFactory.getLogger(SolrPropertiesValidator.class);

  private SolrPropertiesValidator() {
  }

  static void validate(SolrProperties properties) {
    var hasStandalone = properties.getStandalone() != null;
    var hasCloud = properties.getCloud() != null;

    if (hasStandalone && hasCloud) {
      throw new IllegalStateException(
          "Ambiguous Solr configuration: both 'spring.data.solr.standalone' and 'spring.data.solr.cloud' "
              + "are set. Remove one — these modes are mutually exclusive.");
    }

    if (!hasStandalone && !hasCloud) {
      throw new IllegalStateException(
          "No Solr configuration found. Configure either 'spring.data.solr.standalone.host' for "
              + "standalone mode or 'spring.data.solr.cloud.zk-host' for SolrCloud mode.");
    }

    if (hasStandalone && isBlank(properties.getStandalone().defaultCollection())) {
      log.warn("No 'spring.data.solr.standalone.default-collection' set. Repository operations "
          + "and the health indicator will require a collection name to be supplied per-call.");
    }
    if (hasCloud && isBlank(properties.getCloud().defaultCollection())) {
      log.warn("No 'spring.data.solr.cloud.default-collection' set. Repository operations and the "
          + "health indicator will require a collection name to be supplied per-call.");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
