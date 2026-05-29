package com.tomaytotomato.data.solr.repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.UnaryOperator;
import org.apache.solr.client.solrj.beans.Field;

public class SolrFieldNameResolver {

  // WeakHashMap allows Class<?> keys to be GC'd when the classloader that loaded them is replaced
  // (e.g. Spring Boot DevTools hot-reload), preventing classloader leaks. The synchronizedMap
  // wrapper provides thread safety for this low-contention cache.
  private static final Map<Class<?>, SolrFieldNameResolver> CACHE =
      Collections.synchronizedMap(new WeakHashMap<>());

  private final Map<String, String> propertyToSolrField;

  private SolrFieldNameResolver(Map<String, String> propertyToSolrField) {
    this.propertyToSolrField = propertyToSolrField;
  }

  public static SolrFieldNameResolver forClass(Class<?> entityClass) {
    return CACHE.computeIfAbsent(entityClass, SolrFieldNameResolver::buildMapping);
  }

  public static SolrFieldNameResolver identity() {
    return new SolrFieldNameResolver(Map.of());
  }

  static void clearCache() {
    CACHE.clear();
  }

  public String resolve(String propertyName) {
    return propertyToSolrField.getOrDefault(propertyName, propertyName);
  }

  public UnaryOperator<String> asFunction() {
    return this::resolve;
  }

  private static SolrFieldNameResolver buildMapping(Class<?> clazz) {
    var mapping = new HashMap<String, String>();

    var currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      for (var field : currentClass.getDeclaredFields()) {
        var annotation = field.getAnnotation(Field.class);
        if (annotation != null && !annotation.value().isEmpty() && !"#default".equals(annotation.value())) {
          mapping.putIfAbsent(field.getName(), annotation.value());
        }
      }

      for (var method : currentClass.getDeclaredMethods()) {
        var annotation = method.getAnnotation(Field.class);
        if (annotation != null && !annotation.value().isEmpty() && !"#default".equals(annotation.value())) {
          var name = method.getName();
          if (name.startsWith("get") && name.length() > 3) {
            var propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            mapping.putIfAbsent(propertyName, annotation.value());
          } else if (name.startsWith("is") && name.length() > 2) {
            var propertyName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            mapping.putIfAbsent(propertyName, annotation.value());
          }
        }
      }

      currentClass = currentClass.getSuperclass();
    }

    return new SolrFieldNameResolver(Map.copyOf(mapping));
  }
}
