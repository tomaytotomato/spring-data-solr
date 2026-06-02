package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.mapping.SolrPersistentProperty;
import org.springframework.data.repository.core.EntityInformation;

public class SolrEntityInformation<T> implements EntityInformation<T, String> {

  private final Class<T> entityClass;
  private final String collection;
  private final java.lang.reflect.Field idField;

  /**
   * Creates entity information, resolving the collection name via the supplied
   * {@link SolrDocumentResolver} (which has an {@link org.springframework.core.env.Environment}
   * so that {@code ${placeholder}} values are expanded correctly).
   *
   * @param entityClass the domain class
   * @param resolver    the resolver to use for collection name lookup
   */
  public SolrEntityInformation(Class<T> entityClass, SolrDocumentResolver resolver) {
    this.entityClass = entityClass;
    this.collection = resolver.resolve(entityClass);
    this.idField = resolveIdField(entityClass);
  }

  /**
   * Creates entity information without placeholder resolution.
   *
   * <p>Retained for backward compatibility. Prefer the
   * {@link #SolrEntityInformation(Class, SolrDocumentResolver)} constructor when a Spring
   * {@link org.springframework.core.env.Environment} is available.
   *
   * @param entityClass the domain class
   */
  public SolrEntityInformation(Class<T> entityClass) {
    this.entityClass = entityClass;
    this.collection = SolrDocumentResolver.resolveCollection(entityClass);
    this.idField = resolveIdField(entityClass);
  }

  private static java.lang.reflect.Field resolveIdField(Class<?> entityClass) {
    for (var field : entityClass.getDeclaredFields()) {
      var annotation = field.getAnnotation(org.apache.solr.client.solrj.beans.Field.class);
      if (annotation == null) {
        continue;
      }
      var annotationValue = annotation.value();
      boolean isDefaultValue = annotationValue.isEmpty() || SolrPersistentProperty.FIELD_DEFAULT_SENTINEL.equals(annotationValue);
      if (isDefaultValue && "id".equals(field.getName())) {
        field.setAccessible(true);
        return field;
      }
      if (!isDefaultValue && "id".equals(annotationValue)) {
        field.setAccessible(true);
        return field;
      }
    }
    return null;
  }

  @Override
  public boolean isNew(T entity) {
    return getId(entity) == null;
  }

  @Override
  public String getId(T entity) {
    if (idField == null) {
      return null;
    }
    try {
      return (String) idField.get(entity);
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  @Override
  public Class<String> getIdType() {
    return String.class;
  }

  @Override
  public Class<T> getJavaType() {
    return entityClass;
  }

  public String getCollection() {
    return collection;
  }
}
