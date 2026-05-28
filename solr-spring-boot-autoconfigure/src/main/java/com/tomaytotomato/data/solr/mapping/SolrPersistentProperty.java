package com.tomaytotomato.data.solr.mapping;

import org.apache.solr.client.solrj.beans.Field;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;

public class SolrPersistentProperty extends AnnotationBasedPersistentProperty<SolrPersistentProperty> {

  public SolrPersistentProperty(Property property, PersistentEntity<?, SolrPersistentProperty> owner,
      SimpleTypeHolder simpleTypeHolder) {
    super(property, owner, simpleTypeHolder);
  }

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

  public boolean isScoreProperty() {
    return isAnnotationPresent(Score.class);
  }

  @Override
  protected Association<SolrPersistentProperty> createAssociation() {
    return new Association<>(this, null);
  }

  @Override
  public boolean isIdProperty() {
    if (super.isIdProperty()) {
      return true;
    }
    var annotation = findAnnotation(Field.class);
    return annotation != null && "id".equals(annotation.value());
  }
}
