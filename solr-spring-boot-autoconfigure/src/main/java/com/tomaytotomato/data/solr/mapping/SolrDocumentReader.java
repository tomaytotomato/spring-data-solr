package com.tomaytotomato.data.solr.mapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.solr.common.SolrDocument;

public class SolrDocumentReader<T> implements SolrDocumentConverter<SolrDocument, T> {

  private final Class<T> type;
  private final SolrMappingContext mappingContext;

  /**
   * Creates a reader that resolves field names via the supplied {@link SolrMappingContext}.
   * This is the preferred constructor — field-name resolution is delegated to
   * {@link SolrPersistentProperty#getSolrFieldName()} rather than performed by independent
   * reflection, ensuring a single authoritative source of truth.
   *
   * @param type           the entity class to read into
   * @param mappingContext the mapping context to delegate field-name resolution to
   */
  public SolrDocumentReader(Class<T> type, SolrMappingContext mappingContext) {
    this.type = type;
    this.mappingContext = mappingContext;
  }

  /**
   * Creates a reader that resolves field names via its own reflection over {@code @Field}
   * annotations. Retained for backward compatibility; prefer
   * {@link #SolrDocumentReader(Class, SolrMappingContext)} for new usage.
   *
   * @param type the entity class to read into
   */
  public SolrDocumentReader(Class<T> type) {
    this(type, null);
  }

  @Override
  public T convert(SolrDocument source) {
    try {
      var instance = type.getDeclaredConstructor().newInstance();
      for (var field : annotatedFields(type)) {
        var solrFieldName = resolveSolrFieldName(field);
        var value = source.getFieldValue(solrFieldName);
        if (value == null) {
          continue;
        }
        field.setAccessible(true);
        field.set(instance, coerce(value, field.getType()));
      }
      setScore(instance, source);
      return instance;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to map SolrDocument to " + type.getName(), e);
    }
  }

  /**
   * Resolves the Solr field name for a Java field. When a {@link SolrMappingContext} is
   * available, delegates to {@link SolrPersistentProperty#getSolrFieldName()}. Falls back
   * to reading the {@code @Field} annotation directly when no context is present.
   */
  private String resolveSolrFieldName(Field field) {
    if (mappingContext != null) {
      var entity = mappingContext.getPersistentEntity(type);
      if (entity != null) {
        var property = entity.getPersistentProperty(field.getName());
        if (property != null) {
          return property.getSolrFieldName();
        }
      }
    }
    return solrFieldNameFromAnnotation(field);
  }

  private Object coerce(Object value, Class<?> targetType) {
    if (targetType.isAssignableFrom(value.getClass())) {
      return value;
    }
    if (value instanceof Collection<?> collection && !Collection.class.isAssignableFrom(targetType)) {
      return coerceCollection(collection, targetType);
    }
    return value;
  }

  private Object coerceCollection(Collection<?> collection, Class<?> targetType) {
    if (collection.size() == 1) {
      var unwrapped = collection.iterator().next();
      if (unwrapped != null && targetType.isAssignableFrom(unwrapped.getClass())) {
        return unwrapped;
      }
    }
    if (targetType == String.class) {
      return collection.stream()
          .map(Object::toString)
          .collect(Collectors.joining(","));
    }
    throw new IllegalArgumentException(
        "Cannot convert %s to %s".formatted(collection.getClass().getName(), targetType.getName()));
  }

  private void setScore(T instance, SolrDocument source) throws IllegalAccessException {
    var score = source.getFieldValue("score");
    if (score == null) {
      return;
    }
    for (var field : type.getDeclaredFields()) {
      if (field.isAnnotationPresent(Score.class)) {
        field.setAccessible(true);
        field.set(instance, ((Number) score).floatValue());
        return;
      }
    }
  }

  private static String solrFieldNameFromAnnotation(Field field) {
    var annotation = field.getAnnotation(org.apache.solr.client.solrj.beans.Field.class);
    var value = annotation.value();
    if (value.isEmpty() || "#default".equals(value)) {
      return field.getName();
    }
    return value;
  }

  private static List<Field> annotatedFields(Class<?> clazz) {
    var fields = new ArrayList<Field>();
    var current = clazz;
    while (current != null && current != Object.class) {
      for (var field : current.getDeclaredFields()) {
        if (field.isAnnotationPresent(org.apache.solr.client.solrj.beans.Field.class)) {
          fields.add(field);
        }
      }
      current = current.getSuperclass();
    }
    return fields;
  }
}
