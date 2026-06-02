package com.tomaytotomato.data.solr.mapping;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.solr.common.SolrInputDocument;

public class SolrDocumentWriter<T> implements SolrDocumentConverter<T, SolrInputDocument> {

  private final SolrMappingContext mappingContext;

  /**
   * Creates a writer that resolves field names via the supplied {@link SolrMappingContext}.
   * This is the preferred constructor — field-name resolution is delegated to
   * {@link SolrPersistentProperty#getSolrFieldName()} rather than performed by independent
   * reflection, ensuring a single authoritative source of truth.
   *
   * @param mappingContext the mapping context to delegate field-name resolution to
   */
  public SolrDocumentWriter(SolrMappingContext mappingContext) {
    this.mappingContext = mappingContext;
  }

  /**
   * Creates a writer that resolves field names via its own reflection over {@code @Field}
   * annotations. Retained for backward compatibility; prefer
   * {@link #SolrDocumentWriter(SolrMappingContext)} for new usage.
   */
  public SolrDocumentWriter() {
    this(null);
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
        if (value instanceof Collection<?> collection) {
          doc.setField(solrFieldName, new ArrayList<>(collection));
        } else {
          doc.setField(solrFieldName, value);
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
