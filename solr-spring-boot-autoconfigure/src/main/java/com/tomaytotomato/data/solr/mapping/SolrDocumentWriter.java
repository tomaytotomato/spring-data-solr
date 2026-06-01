package com.tomaytotomato.data.solr.mapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.solr.common.SolrInputDocument;

public class SolrDocumentWriter<T> implements SolrDocumentConverter<T, SolrInputDocument> {

  private final SolrMappingContext mappingContext;
  private final SolrMappingConverter mappingConverter;

  /**
   * Creates a writer with no mapping context and no custom converters.
   */
  public SolrDocumentWriter() {
    this(null, new SolrMappingConverter());
  }

  /**
   * Creates a writer that resolves field names via the supplied {@link SolrMappingContext}.
   *
   * @param mappingContext the mapping context for field-name resolution
   */
  public SolrDocumentWriter(SolrMappingContext mappingContext) {
    this(mappingContext, new SolrMappingConverter());
  }

  /**
   * Creates a writer that consults the supplied {@link SolrMappingConverter} during field mapping.
   *
   * @param mappingConverter the converter to use for custom type conversions
   */
  public SolrDocumentWriter(SolrMappingConverter mappingConverter) {
    this(null, mappingConverter);
  }

  /**
   * Creates a writer with both field-name resolution and custom type conversion support.
   *
   * @param mappingContext   the mapping context for field-name resolution; may be {@code null}
   * @param mappingConverter the converter for custom type conversions; must not be {@code null}
   */
  public SolrDocumentWriter(SolrMappingContext mappingContext, SolrMappingConverter mappingConverter) {
    this.mappingContext = mappingContext;
    this.mappingConverter = mappingConverter;
  }

  @Override
  public SolrInputDocument convert(T source) {
    try {
      var doc = new SolrInputDocument();
      for (var field : annotatedFields(source.getClass())) {
        var solrFieldName = resolveSolrFieldName(field, source.getClass());
        field.setAccessible(true);
        var value = field.get(source);
        if (value == null) {
          continue;
        }
        var solrValue = toSolrValue(value);
        if (value instanceof Collection<?> collection) {
          doc.setField(solrFieldName, new ArrayList<>(collection));
        } else {
          doc.setField(solrFieldName, solrValue);
        }
      }
      return doc;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          "Failed to convert entity to SolrInputDocument: " + source.getClass().getName(), e);
    }
  }

  /**
   * Resolves the Solr field name for a Java field. When a {@link SolrMappingContext} is
   * available, delegates to {@link SolrPersistentProperty#getSolrFieldName()}. Falls back
   * to reading the {@code @Field} annotation directly when no context is present.
   */
  private String resolveSolrFieldName(Field field, Class<?> entityType) {
    if (mappingContext != null) {
      var entity = mappingContext.getPersistentEntity(entityType);
      if (entity != null) {
        var property = entity.getPersistentProperty(field.getName());
        if (property != null) {
          return property.getSolrFieldName();
        }
      }
    }
    return solrFieldNameFromAnnotation(field);
  }

  /**
   * Converts a Java field value to its Solr-storable representation.
   * If a converter is registered for the value's type to {@link String}, it is applied.
   * Otherwise the value is returned as-is.
   */
  private Object toSolrValue(Object value) {
    if (mappingConverter.canConvert(value.getClass(), String.class)) {
      return mappingConverter.convert(value, String.class);
    }
    return value;
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
