package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.query.GeoDistance;
import com.tomaytotomato.data.solr.query.GeoPoint;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThat;

class SolrQueryCreatorTest {

  static class Product {
    String title;
    String author;
    double price;
    boolean inStock;
    String category;
  }

  static class Place {
    String name;
    GeoPoint location;
  }

  static class BookWithFieldMapping {
    String id;
    String title;
    @Field("publication_year")
    int year;
    @Field("author_name")
    String author;
    double price;
  }

  private String createQuery(String methodName, Object... params) {
    var tree = new PartTree(methodName, Product.class);
    var accessor = new StubParameterAccessor(params);
    var creator = new SolrQueryCreator(tree, accessor);
    return creator.createQuery().toSolrQuery().getQuery();
  }

  private String createQueryWithMapping(String methodName, Class<?> entityClass, Object... params) {
    var tree = new PartTree(methodName, entityClass);
    var accessor = new StubParameterAccessor(params);
    var fieldNameResolver = SolrFieldNameResolver.forClass(entityClass);
    var creator = new SolrQueryCreator(tree, accessor, fieldNameResolver);
    return creator.createQuery().toSolrQuery().getQuery();
  }

  @Nested
  class SimpleProperty {

    @Test
    void findByTitle() {
      assertThat(createQuery("findByTitle", "Spring Boot"))
          .isEqualTo("title:Spring\\ Boot");
    }

    @Test
    void findByTitleAndAuthor() {
      assertThat(createQuery("findByTitleAndAuthor", "Spring", "Picard"))
          .isEqualTo("title:Spring AND author:Picard");
    }
  }

  @Nested
  class NegatingSimpleProperty {

    @Test
    void findByTitleNot() {
      assertThat(createQuery("findByTitleNot", "Obsolete"))
          .isEqualTo("-title:Obsolete");
    }
  }

  @Nested
  class Containing {

    @Test
    void findByTitleContaining() {
      assertThat(createQuery("findByTitleContaining", "spring"))
          .isEqualTo("title:*spring*");
    }
  }

  @Nested
  class NotContaining {

    @Test
    void findByTitleNotContaining() {
      assertThat(createQuery("findByTitleNotContaining", "draft"))
          .isEqualTo("-title:*draft*");
    }
  }

  @Nested
  class StartingWith {

    @Test
    void findByTitleStartingWith() {
      assertThat(createQuery("findByTitleStartingWith", "Spring"))
          .isEqualTo("title:Spring*");
    }
  }

  @Nested
  class EndingWith {

    @Test
    void findByTitleEndingWith() {
      assertThat(createQuery("findByTitleEndingWith", "Boot"))
          .isEqualTo("title:*Boot");
    }
  }

  @Nested
  class GreaterThan {

    @Test
    void findByPriceGreaterThan() {
      assertThat(createQuery("findByPriceGreaterThan", 10.0))
          .isEqualTo("price:{10.0 TO *]");
    }
  }

  @Nested
  class GreaterThanEqual {

    @Test
    void findByPriceGreaterThanEqual() {
      assertThat(createQuery("findByPriceGreaterThanEqual", 10.0))
          .isEqualTo("price:[10.0 TO *]");
    }
  }

  @Nested
  class LessThan {

    @Test
    void findByPriceLessThan() {
      assertThat(createQuery("findByPriceLessThan", 100.0))
          .isEqualTo("price:[* TO 100.0}");
    }
  }

  @Nested
  class LessThanEqual {

    @Test
    void findByPriceLessThanEqual() {
      assertThat(createQuery("findByPriceLessThanEqual", 100.0))
          .isEqualTo("price:[* TO 100.0]");
    }
  }

  @Nested
  class Between {

    @Test
    void findByPriceBetween() {
      assertThat(createQuery("findByPriceBetween", 10.0, 50.0))
          .isEqualTo("price:[10.0 TO 50.0]");
    }
  }

  @Nested
  class In {

    @Test
    void findByCategoryIn() {
      assertThat(createQuery("findByCategoryIn", List.of("books", "ebooks")))
          .isEqualTo("category:(books OR ebooks)");
    }
  }

  @Nested
  class IsNull {

    @Test
    void findByAuthorIsNull() {
      assertThat(createQuery("findByAuthorIsNull"))
          .isEqualTo("-author:[* TO *]");
    }
  }

  @Nested
  class IsNotNull {

    @Test
    void findByAuthorIsNotNull() {
      assertThat(createQuery("findByAuthorIsNotNull"))
          .isEqualTo("author:[* TO *]");
    }
  }

  @Nested
  class IsTrue {

    @Test
    void findByInStockTrue() {
      assertThat(createQuery("findByInStockTrue"))
          .isEqualTo("inStock:true");
    }
  }

  @Nested
  class IsFalse {

    @Test
    void findByInStockFalse() {
      assertThat(createQuery("findByInStockFalse"))
          .isEqualTo("inStock:false");
    }
  }

  @Nested
  class OrQueries {

    @Test
    void findByTitleOrAuthor() {
      assertThat(createQuery("findByTitleOrAuthor", "Spring", "Picard"))
          .isEqualTo("title:Spring OR author:Picard");
    }
  }

  @Nested
  class ComplexQueries {

    @Test
    void findByTitleAndPriceGreaterThan() {
      assertThat(createQuery("findByTitleAndPriceGreaterThan", "Spring", 10.0))
          .isEqualTo("title:Spring AND price:{10.0 TO *]");
    }

    @Test
    void findByTitleContainingOrAuthorStartingWith() {
      assertThat(createQuery("findByTitleContainingOrAuthorStartingWith", "spring", "Pic"))
          .isEqualTo("title:*spring* OR author:Pic*");
    }
  }

  @Nested
  class Sorting {

    @Test
    void appliesSortFromAccessor() {
      var tree = new PartTree("findByTitle", Product.class);
      var accessor = new StubParameterAccessor(Sort.by("price").descending(), "Spring");
      var creator = new SolrQueryCreator(tree, accessor);
      var solrQuery = creator.createQuery().toSolrQuery();

      assertThat(solrQuery.getQuery()).isEqualTo("title:Spring");
      assertThat(solrQuery.get("sort")).isEqualTo("price desc");
    }
  }

  @Nested
  class FieldAnnotationMapping {

    @Test
    void findByYearMapsToSolrFieldName() {
      assertThat(createQueryWithMapping("findByYear", BookWithFieldMapping.class, 2024))
          .isEqualTo("publication_year:2024");
    }

    @Test
    void findByAuthorMapsToSolrFieldName() {
      assertThat(createQueryWithMapping("findByAuthor", BookWithFieldMapping.class, "Picard"))
          .isEqualTo("author_name:Picard");
    }

    @Test
    void findByPriceUsesPropertyNameWhenNoFieldAnnotation() {
      assertThat(createQueryWithMapping("findByPrice", BookWithFieldMapping.class, 29.99))
          .isEqualTo("price:29.99");
    }

    @Test
    void findByYearGreaterThanMapsToSolrFieldName() {
      assertThat(createQueryWithMapping("findByYearGreaterThan", BookWithFieldMapping.class, 2020))
          .isEqualTo("publication_year:{2020 TO *]");
    }

    @Test
    void findByAuthorAndYearMapsToSolrFieldNames() {
      assertThat(createQueryWithMapping("findByAuthorAndYear", BookWithFieldMapping.class, "Picard", 2024))
          .isEqualTo("author_name:Picard AND publication_year:2024");
    }
  }

  static class BaseDocument {
    String id;
    @Field("base_title")
    String title;
    @Field("created_at")
    String createdAt;
  }

  static class SubDocument extends BaseDocument {
    @Field("sub_category")
    String category;
  }

  static class OverridingSubDocument extends BaseDocument {
    @Field("overridden_title")
    String title;
  }

  @Nested
  class InheritedFieldAnnotationMapping {

    // Explicit clear after each test ensures a clean slate for isolated assertions.
    // In production the WeakHashMap allows GC to reclaim entries automatically when
    // a classloader is replaced; here we clear eagerly rather than waiting on GC.
    @AfterEach
    void clearCache() {
      SolrFieldNameResolver.clearCache();
    }

    @Test
    void resolvesFieldAnnotationFromSuperclass() {
      var resolver = SolrFieldNameResolver.forClass(SubDocument.class);
      assertThat(resolver.resolve("title")).isEqualTo("base_title");
    }

    @Test
    void resolvesFieldAnnotationDeclaredOnSubclass() {
      var resolver = SolrFieldNameResolver.forClass(SubDocument.class);
      assertThat(resolver.resolve("category")).isEqualTo("sub_category");
    }

    @Test
    void resolvesMultipleInheritedFields() {
      var resolver = SolrFieldNameResolver.forClass(SubDocument.class);
      assertThat(resolver.resolve("createdAt")).isEqualTo("created_at");
    }

    @Test
    void subclassAnnotationOverridesParentAnnotation() {
      var resolver = SolrFieldNameResolver.forClass(OverridingSubDocument.class);
      assertThat(resolver.resolve("title")).isEqualTo("overridden_title");
    }
  }

  private String createGeoQuery(String methodName, Object... params) {
    var tree = new PartTree(methodName, Place.class);
    var accessor = new StubParameterAccessor(params);
    var creator = new SolrQueryCreator(tree, accessor);
    return creator.createQuery().toSolrQuery().getQuery();
  }

  @Nested
  class Near {

    @Test
    void findByLocationNearGeneratesGeoFilterQuery() {
      var point = new GeoPoint(51.5074, -0.1278);
      var distance = GeoDistance.kilometers(10.0);
      assertThat(createGeoQuery("findByLocationNear", point, distance))
          .isEqualTo("{!geofilt sfield=location pt=51.5074,-0.1278 d=10.0}");
    }

    @Test
    void findByLocationNearWithMilesConvertsToKilometers() {
      var point = new GeoPoint(40.7128, -74.0060);
      var distance = GeoDistance.miles(5.0);
      var result = createGeoQuery("findByLocationNear", point, distance);
      assertThat(result).startsWith("{!geofilt sfield=location pt=40.7128,-74.006 d=");
      assertThat(result).contains("8.04");
    }
  }

  @Nested
  class Within {

    @Test
    void findByLocationWithinGeneratesBboxQuery() {
      var point = new GeoPoint(48.8566, 2.3522);
      var distance = GeoDistance.kilometers(20.0);
      assertThat(createGeoQuery("findByLocationWithin", point, distance))
          .isEqualTo("{!bbox sfield=location pt=48.8566,2.3522 d=20.0}");
    }
  }

  static class StubParameterAccessor implements org.springframework.data.repository.query.ParameterAccessor {

    private final Object[] values;
    private final Sort sort;

    StubParameterAccessor(Object... values) {
      this(Sort.unsorted(), values);
    }

    StubParameterAccessor(Sort sort, Object... values) {
      this.sort = sort;
      this.values = values;
    }

    @Override
    public org.springframework.data.domain.ScrollPosition getScrollPosition() {
      return org.springframework.data.domain.ScrollPosition.keyset();
    }

    @Override
    public org.springframework.data.domain.Pageable getPageable() {
      return org.springframework.data.domain.Pageable.unpaged();
    }

    @Override
    public Sort getSort() {
      return sort;
    }

    @Override
    public org.springframework.data.domain.Limit getLimit() {
      return org.springframework.data.domain.Limit.unlimited();
    }

    @Override
    public Class<?> findDynamicProjection() {
      return null;
    }

    @Override
    public Object getBindableValue(int index) {
      return values[index];
    }

    @Override
    public boolean hasBindableNullValue() {
      return false;
    }

    @Override
    public Iterator<Object> iterator() {
      return List.of(values).iterator();
    }
  }
}
