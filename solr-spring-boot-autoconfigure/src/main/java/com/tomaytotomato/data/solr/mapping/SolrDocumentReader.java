package com.tomaytotomato.data.solr.mapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.solr.common.SolrDocument;

public class SolrDocumentReader<T> implements SolrDocumentConverter<SolrDocument, T> {

  private final Class<T> type;
  private final SolrMappingContext mappingContext;
  private final SolrMappingConverter mappingConverter;

  /**
   * Creates a reader with no mapping context and no custom converters.
   *
   * @param type the entity class to read into
   */
  public SolrDocumentReader(Class<T> type) {
    this(type, null, new SolrMappingConverter());
  }

  /**
   * Creates a reader that resolves field names via the supplied {@link SolrMappingContext}.
   *
   * @param type           the entity class to read into
   * @param mappingContext the mapping context to delegate field-name resolution to
   */
  public SolrDocumentReader(Class<T> type, SolrMappingContext mappingContext) {
    this(type, mappingContext, new SolrMappingConverter());
  }

  /**
   * Creates a reader that consults the supplied {@link SolrMappingConverter} during field mapping.
   *
   * @param type             the target entity class
   * @param mappingConverter the converter to use for custom type conversions
   */
  public SolrDocumentReader(Class<T> type, SolrMappingConverter mappingConverter) {
    this(type, null, mappingConverter);
  }

  /**
   * Creates a reader with both field-name resolution and custom type conversion support.
   *
   * @param type             the entity class to read into
   * @param mappingContext   the mapping context for field-name resolution; may be {@code null}
   * @param mappingConverter the converter for custom type conversions; must not be {@code null}
   */
  public SolrDocumentReader(Class<T> type, SolrMappingContext mappingContext,
      SolrMappingConverter mappingConverter) {
    this.type = type;
    this.mappingContext = mappingContext;
    this.mappingConverter = mappingConverter;
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
        var coerced = coerce(value, field.getType());
        if (coerced != null) {
          field.set(instance, coerced);
        }
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

  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = Map.of(
      boolean.class, Boolean.class,
      byte.class, Byte.class,
      char.class, Character.class,
      double.class, Double.class,
      float.class, Float.class,
      int.class, Integer.class,
      long.class, Long.class,
      short.class, Short.class
  );

  private Object coerce(Object value, Class<?> targetType) {
    var effectiveTarget = PRIMITIVE_TO_WRAPPER.getOrDefault(targetType, targetType);
    if (effectiveTarget.isAssignableFrom(value.getClass())) {
      return value;
    }
    if (mappingConverter.canConvert(value.getClass(), effectiveTarget)) {
      return mappingConverter.convert(value, effectiveTarget);
    }
    if (value instanceof Collection<?> collection && !Collection.class.isAssignableFrom(targetType)) {
      return coerceCollection(collection, targetType);
    }
    // No converter available and types are incompatible — skip the field (leave null/default)
    return null;
  }

  private Object coerceCollection(Collection<?> collection, Class<?> targetType) {
    if (collection.size() == 1) {
      var unwrapped = collection.iterator().next();
      if (unwrapped != null && targetType.isAssignableFrom(unwrapped.getClass())) {
        return unwrapped;
      }
      if (unwrapped != null && mappingConverter.canConvert(unwrapped.getClass(), targetType)) {
        return mappingConverter.convert(unwrapped, targetType);
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
