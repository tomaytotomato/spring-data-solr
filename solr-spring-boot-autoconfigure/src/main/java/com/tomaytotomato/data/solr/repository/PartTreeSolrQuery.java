package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.FacetPage;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * {@link RepositoryQuery} implementation that resolves derived query methods via
 * Spring Data's {@link PartTree} mechanism and executes them against a Solr collection.
 *
 * <p>When a method returns a paged type ({@code Page}, {@link HighlightPage}, or
 * {@link FacetPage}) and the caller does not supply a {@link org.springframework.data.domain.Pageable}
 * argument, a default page of {@code PageRequest.of(0, defaultPageSize)} is applied and a
 * {@code WARN} is logged. Configure {@code spring.data.solr.default-page-size} to override the
 * default size (default is {@code 10}).
 */
public class PartTreeSolrQuery implements RepositoryQuery {

  private static final Logger log = LoggerFactory.getLogger(PartTreeSolrQuery.class);

  private final QueryMethod queryMethod;
  private final SolrTemplate solrTemplate;
  private final PartTree tree;
  private final Class<?> domainType;
  private final Method method;
  private final int defaultPageSize;
  private final SolrDocumentResolver documentResolver;

  public PartTreeSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate, Method method) {
    this(queryMethod, solrTemplate, method, 10);
  }

  /**
   * Creates a new {@code PartTreeSolrQuery} without placeholder resolution.
   *
   * <p>Retained for backward compatibility. Prefer the
   * {@link #PartTreeSolrQuery(QueryMethod, SolrTemplate, Method, SolrDocumentResolver)} form.
   *
   * @param queryMethod    Spring Data query method descriptor
   * @param solrTemplate   Solr operations delegate
   * @param method         the repository interface method
   * @param defaultPageSize page size applied when no {@code Pageable} is supplied by the caller
   */
  public PartTreeSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate, Method method,
      int defaultPageSize) {
    this.queryMethod = queryMethod;
    this.solrTemplate = solrTemplate;
    this.domainType = queryMethod.getEntityInformation().getJavaType();
    this.tree = new PartTree(queryMethod.getName(), domainType);
    this.method = method;
    this.defaultPageSize = defaultPageSize;
    this.documentResolver = null;
  }

  /**
   * Creates a new {@code PartTreeSolrQuery} with environment-aware collection resolution.
   *
   * @param queryMethod     Spring Data query method descriptor
   * @param solrTemplate    Solr operations delegate
   * @param method          the repository interface method
   * @param documentResolver resolver that expands {@code ${placeholder}} collection names
   */
  public PartTreeSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate, Method method,
      SolrDocumentResolver documentResolver) {
    this(queryMethod, solrTemplate, method, 10, documentResolver);
  }

  /**
   * Creates a new {@code PartTreeSolrQuery} with environment-aware collection resolution and a
   * custom default page size.
   *
   * @param queryMethod     Spring Data query method descriptor
   * @param solrTemplate    Solr operations delegate
   * @param method          the repository interface method
   * @param defaultPageSize page size applied when no {@code Pageable} is supplied by the caller
   * @param documentResolver resolver that expands {@code ${placeholder}} collection names
   */
  public PartTreeSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate, Method method,
      int defaultPageSize, SolrDocumentResolver documentResolver) {
    this.queryMethod = queryMethod;
    this.solrTemplate = solrTemplate;
    this.domainType = queryMethod.getEntityInformation().getJavaType();
    this.tree = new PartTree(queryMethod.getName(), domainType);
    this.method = method;
    this.defaultPageSize = defaultPageSize;
    this.documentResolver = documentResolver;
  }

  @Override
  public Object execute(Object[] parameters) {
    var accessor = new ParametersParameterAccessor(queryMethod.getParameters(), parameters);
    var fieldNameResolver = SolrFieldNameResolver.forClass(domainType);
    var query = new SolrQueryCreator(tree, accessor, fieldNameResolver).createQuery();
    var collection = documentResolver != null
        ? documentResolver.resolve(domainType)
        : SolrDocumentResolver.resolveCollection(domainType);

    if (tree.isCountProjection()) {
      return solrTemplate.count(collection, query);
    }

    if (tree.isExistsProjection()) {
      return solrTemplate.count(collection, query) > 0;
    }

    var highlightAnnotation = method.getAnnotation(Highlight.class);
    if (highlightAnnotation != null && HighlightPage.class.isAssignableFrom(method.getReturnType())) {
      var pageable = accessor.getPageable().isUnpaged()
          ? applyDefaultPageable(method) : accessor.getPageable();
      query.setPageable(pageable);
      query.setHighlightOptions(HighlightAnnotationAdapter.toHighlightOptions(highlightAnnotation));
      return solrTemplate.queryForHighlightPage(collection, query, domainType);
    }

    var facetAnnotation = method.getAnnotation(Facet.class);
    if (facetAnnotation != null && FacetPage.class.isAssignableFrom(method.getReturnType())) {
      var pageable = accessor.getPageable().isUnpaged()
          ? applyDefaultPageable(method) : accessor.getPageable();
      query.setPageable(pageable);
      query.setFacetOptions(FacetAnnotationAdapter.toFacetOptions(facetAnnotation));
      return solrTemplate.queryForFacetPage(collection, query, domainType);
    }

    if (queryMethod.isPageQuery()) {
      var pageable = accessor.getPageable().isUnpaged()
          ? applyDefaultPageable(method) : accessor.getPageable();
      query.setPageable(pageable);
      return solrTemplate.queryForPage(collection, query, domainType);
    }

    if (queryMethod.isCollectionQuery()) {
      return solrTemplate.query(collection, query.toSolrQuery(), domainType);
    }

    var results = solrTemplate.query(collection, query.toSolrQuery(), domainType);
    return results.isEmpty() ? null : results.getFirst();
  }

  /**
   * Returns a default {@link PageRequest} and logs a warning. Called when a page-returning
   * repository method is invoked without a {@link org.springframework.data.domain.Pageable}
   * argument. Set {@code spring.data.solr.default-page-size} to control the page size, or pass an
   * explicit {@code Pageable} to suppress this warning entirely.
   */
  private PageRequest applyDefaultPageable(Method m) {
    log.warn(
        "Repository method {}.{}() returns a paged result but was called without a Pageable "
            + "argument. Defaulting to page 0, size {}. Pass an explicit Pageable to control "
            + "pagination and suppress this warning, or configure spring.data.solr.default-page-size.",
        m.getDeclaringClass().getSimpleName(), m.getName(), defaultPageSize);
    return PageRequest.of(0, defaultPageSize);
  }

  @Override
  public QueryMethod getQueryMethod() {
    return queryMethod;
  }
}
