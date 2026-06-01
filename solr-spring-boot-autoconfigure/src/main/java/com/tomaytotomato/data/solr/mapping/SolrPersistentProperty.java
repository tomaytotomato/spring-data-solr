package com.tomaytotomato.data.solr.mapping;

import org.apache.solr.client.solrj.beans.Field;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;

/**
 * Spring Data persistent-property representation for a field on a Solr document type.
 *
 * <p>Extends {@link AnnotationBasedPersistentProperty} with Solr-specific behaviour:
 * <ul>
 *   <li>Resolves the Solr field name from SolrJ's {@link Field} annotation, falling back to the
 *       Java property name when the annotation is absent or uses the {@code #default} sentinel.</li>
 *   <li>Recognises properties annotated with {@link Score} as score fields populated by Solr's
 *       relevance scoring.</li>
 *   <li>Treats a property whose {@link Field} value is {@code "id"} as the document's ID
 *       property, in addition to properties already identified as IDs by the standard Spring Data
 *       mechanism.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public class SolrPersistentProperty extends AnnotationBasedPersistentProperty<SolrPersistentProperty> {

  /**
   * Creates a new {@link SolrPersistentProperty}.
   *
   * @param property the property descriptor
   * @param owner the persistent entity that owns this property
   * @param simpleTypeHolder holder of simple types known to the mapping infrastructure
   */
  public SolrPersistentProperty(Property property, PersistentEntity<?, SolrPersistentProperty> owner,
      SimpleTypeHolder simpleTypeHolder) {
    super(property, owner, simpleTypeHolder);
  }

  /**
   * Returns the Solr field name to use when indexing or retrieving this property.
   *
   * <p>If the property carries a SolrJ {@link Field} annotation with a non-empty, non-default
   * value, that value is returned. Otherwise the Java property name is used.
   *
   * @return the Solr field name; never {@code null} or empty
   */
  public String getSolrFieldName() {
    var annotation = findAnnotation(Field.class);
    if (annotation != null) {
      var value = annotation.value();
      if (!value.isEmpty() && !"#default".equals(value)) {
        return value;
      }
    }
    return getName();
  }

  /**
   * Returns {@code true} if this property is annotated with {@link Score}, indicating that Solr
   * should populate it with the document's relevance score.
   *
   * @return {@code true} if the property is a score field
   */
  public boolean isScoreProperty() {
    return isAnnotationPresent(Score.class);
  }

  /**
   * Creates a stub association for this property. Solr documents do not support associations;
   * this implementation satisfies the contract required by the Spring Data mapping infrastructure.
   *
   * @return a stub {@link Association} with no inverse
   */
  @Override
  protected Association<SolrPersistentProperty> createAssociation() {
    return new Association<>(this, null);
  }

  /**
   * Returns {@code true} if this property represents the Solr document ID.
   *
   * <p>A property is considered the ID if it is identified as such by the standard Spring Data
   * mechanism (e.g. annotated with {@code @Id}), or if it carries a SolrJ {@link Field} annotation
   * whose value is {@code "id"}.
   *
   * @return {@code true} if this is the document's ID property
   */
  @Override
  public boolean isIdProperty() {
    if (super.isIdProperty()) {
      return true;
    }
    var annotation = findAnnotation(Field.class);
    return annotation != null && "id".equals(annotation.value());
  }
}
