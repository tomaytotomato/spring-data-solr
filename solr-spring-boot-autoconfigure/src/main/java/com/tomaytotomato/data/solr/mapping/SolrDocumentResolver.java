package com.tomaytotomato.data.solr.mapping;

import org.springframework.core.env.Environment;

/**
 * Resolves the Solr collection name for a domain class from its
 * {@link SolrDocument @SolrDocument} annotation.
 *
 * <p>When instantiated with an {@link Environment}, property placeholders of the form
 * {@code ${some.property}} in the {@link SolrDocument#collection()} value are expanded using
 * {@link Environment#resolvePlaceholders(String)}. If the property is not set the placeholder
 * literal is returned unchanged.
 *
 * <p>This class is registered as a Spring bean by {@code SolrAutoConfiguration} so that the
 * environment is available to all repository-layer callers. The static utility methods
 * {@link #resolveCollection(Class)} and {@link #resolveCollection(Class, Environment)} are
 * retained for code that constructs a resolver outside of the Spring context (e.g. unit tests).
 *
 * @since 0.1.0
 */
public class SolrDocumentResolver {

  private final Environment environment;

  /**
   * Creates a resolver backed by the given {@link Environment}.
   *
   * @param environment used to expand {@code ${...}} placeholders; must not be {@code null}
   */
  public SolrDocumentResolver(Environment environment) {
    this.environment = environment;
  }

  /**
   * Resolves the Solr collection name for {@code type} using the injected environment.
   *
   * <p>Delegates to {@link #resolveCollection(Class, Environment)} using the environment that
   * was supplied at construction time.
   *
   * @param type the domain class annotated with {@link SolrDocument}
   * @return the resolved collection name, never {@code null}
   * @throws IllegalArgumentException if {@code type} is not annotated with {@link SolrDocument}
   */
  public String resolve(Class<?> type) {
    return resolveCollection(type, environment);
  }

  // -------------------------------------------------------------------------
  // Static utility methods
  // -------------------------------------------------------------------------

  /**
   * Resolves the collection name for {@code type} without placeholder expansion.
   *
   * @param type the domain class annotated with {@link SolrDocument}
   * @return the raw collection name (placeholders not expanded)
   * @throws IllegalArgumentException if {@code type} is not annotated with {@link SolrDocument}
   */
  public static String resolveCollection(Class<?> type) {
    return resolveCollection(type, null);
  }

  /**
   * Resolves the collection name for {@code type}, expanding {@code ${...}} placeholders via
   * {@code environment} when it is non-{@code null}.
   *
   * @param type        the domain class annotated with {@link SolrDocument}
   * @param environment used to resolve placeholders; may be {@code null}
   * @return the resolved collection name
   * @throws IllegalArgumentException if {@code type} is not annotated with {@link SolrDocument}
   */
  public static String resolveCollection(Class<?> type, Environment environment) {
    var annotation = type.getAnnotation(SolrDocument.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Class '%s' is not annotated with @SolrDocument".formatted(type.getSimpleName()));
    }
    var collection = annotation.collection();
    if (collection.isEmpty()) {
      return type.getSimpleName().toLowerCase();
    }
    return environment != null ? environment.resolvePlaceholders(collection) : collection;
  }

  /**
   * Returns {@code true} if {@code type} is annotated with {@link SolrDocument}.
   */
  public static boolean isSolrDocument(Class<?> type) {
    return type.isAnnotationPresent(SolrDocument.class);
  }
}
