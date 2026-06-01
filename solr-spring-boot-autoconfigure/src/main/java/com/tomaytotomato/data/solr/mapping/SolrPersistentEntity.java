package com.tomaytotomato.data.solr.mapping;

import org.springframework.core.env.Environment;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.model.BasicPersistentEntity;

/**
 * Spring Data persistent-entity representation for an Apache Solr document type.
 *
 * <p>Wraps a domain class annotated with {@link SolrDocument} and exposes the Solr collection
 * name associated with it. If the annotation's {@code collection} attribute is blank, the
 * collection name defaults to the simple class name in lower-case (e.g. {@code Book} → {@code book}).
 *
 * <p>When an {@link Environment} is supplied, any {@code ${placeholder}} expression in the
 * collection name is resolved eagerly at construction time via
 * {@link Environment#resolvePlaceholders(String)}. If no matching property is defined the
 * placeholder literal is retained unchanged.
 *
 * @param <T> the domain type represented by this entity
 * @since 0.1.0
 */
public class SolrPersistentEntity<T> extends BasicPersistentEntity<T, SolrPersistentProperty> {

  private final String collection;

  /**
   * Creates a new {@link SolrPersistentEntity} for the given type information without placeholder
   * resolution.
   *
   * @param typeInformation type metadata for the domain class
   */
  public SolrPersistentEntity(TypeInformation<T> typeInformation) {
    this(typeInformation, null);
  }

  /**
   * Creates a new {@link SolrPersistentEntity} for the given type information, resolving any
   * {@code ${placeholder}} expressions in the collection name against the supplied
   * {@link Environment}.
   *
   * <p>The Solr collection name is resolved from the {@link SolrDocument#collection()} attribute.
   * When the attribute is absent or empty the simple class name (lower-cased) is used instead.
   * When the attribute contains a placeholder and {@code environment} is non-null, the placeholder
   * is resolved eagerly; if the property is not defined the literal placeholder string is retained.
   *
   * @param typeInformation type metadata for the domain class
   * @param environment     Spring {@link Environment} used for placeholder resolution; may be
   *                        {@code null} to skip resolution
   */
  public SolrPersistentEntity(TypeInformation<T> typeInformation, Environment environment) {
    super(typeInformation);
    var annotation = typeInformation.getType().getAnnotation(SolrDocument.class);
    if (annotation != null && !annotation.collection().isEmpty()) {
      var raw = annotation.collection();
      this.collection = environment != null ? environment.resolvePlaceholders(raw) : raw;
    } else {
      this.collection = typeInformation.getType().getSimpleName().toLowerCase();
    }
  }

  /**
   * Returns the Solr collection name for this entity.
   *
   * @return the collection name; never {@code null} or empty
   */
  public String getCollection() {
    return collection;
  }
}
