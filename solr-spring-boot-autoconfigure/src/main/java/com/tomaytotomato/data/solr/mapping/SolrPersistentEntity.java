package com.tomaytotomato.data.solr.mapping;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.model.BasicPersistentEntity;

/**
 * Spring Data persistent-entity representation for an Apache Solr document type.
 *
 * <p>Wraps a domain class annotated with {@link SolrDocument} and exposes the Solr collection
 * name associated with it. If the annotation's {@code collection} attribute is blank, the
 * collection name defaults to the simple class name in lower-case (e.g. {@code Book} → {@code book}).
 *
 * @param <T> the domain type represented by this entity
 * @since 0.1.0
 */
public class SolrPersistentEntity<T> extends BasicPersistentEntity<T, SolrPersistentProperty> {

  private final String collection;

  /**
   * Creates a new {@link SolrPersistentEntity} for the given type information.
   *
   * <p>The Solr collection name is resolved from the {@link SolrDocument#collection()} attribute.
   * When the attribute is absent or empty the simple class name (lower-cased) is used instead.
   *
   * @param typeInformation type metadata for the domain class
   */
  public SolrPersistentEntity(TypeInformation<T> typeInformation) {
    super(typeInformation);
    var annotation = typeInformation.getType().getAnnotation(SolrDocument.class);
    if (annotation != null && !annotation.collection().isEmpty()) {
      this.collection = annotation.collection();
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
