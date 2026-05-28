package com.tomaytotomato.data.solr.mapping;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.model.BasicPersistentEntity;

public class SolrPersistentEntity<T> extends BasicPersistentEntity<T, SolrPersistentProperty> {

  private final String collection;

  public SolrPersistentEntity(TypeInformation<T> typeInformation) {
    super(typeInformation);
    var annotation = typeInformation.getType().getAnnotation(SolrDocument.class);
    if (annotation != null && !annotation.collection().isEmpty()) {
      this.collection = annotation.collection();
    } else {
      this.collection = typeInformation.getType().getSimpleName().toLowerCase();
    }
  }

  public String getCollection() {
    return collection;
  }
}
