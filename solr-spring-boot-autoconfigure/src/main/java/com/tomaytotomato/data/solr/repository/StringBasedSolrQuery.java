package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.FacetPage;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.lang.reflect.Method;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * {@link RepositoryQuery} implementation for {@link Query @Query}-annotated repository methods.
 *
 * <p>When the method returns a paged type ({@link HighlightPage} or {@link FacetPage}) and no
 * {@link Pageable} argument is found in the parameter list, a default page of
 * {@code PageRequest.of(0, defaultPageSize)} is applied and a {@code WARN} is logged. Configure
 * {@code spring.solr.default-page-size} to override the default size (default is {@code 10}).
 */
public class StringBasedSolrQuery implements RepositoryQuery {

  private static final Logger log = LoggerFactory.getLogger(StringBasedSolrQuery.class);

  private final QueryMethod queryMethod;
  private final SolrTemplate solrTemplate;
  private final String queryString;
  private final boolean isCountQuery;
  private final Class<?> domainType;
  private final Method method;
  private final int defaultPageSize;

  public StringBasedSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate,
      String queryString, boolean isCountQuery, Method method) {
    this(queryMethod, solrTemplate, queryString, isCountQuery, method, 10);
  }

  /**
   * Creates a new {@code StringBasedSolrQuery}.
   *
   * @param queryMethod     Spring Data query method descriptor
   * @param solrTemplate    Solr operations delegate
   * @param queryString     raw Solr query string (may contain {@code ?0}, {@code ?1} placeholders)
   * @param isCountQuery    {@code true} when the method is a count query
   * @param method          the repository interface method
   * @param defaultPageSize page size applied when no {@code Pageable} is supplied by the caller
   */
  public StringBasedSolrQuery(QueryMethod queryMethod, SolrTemplate solrTemplate,
      String queryString, boolean isCountQuery, Method method, int defaultPageSize) {
    this.queryMethod = queryMethod;
    this.solrTemplate = solrTemplate;
    this.queryString = queryString;
    this.isCountQuery = isCountQuery;
    this.domainType = queryMethod.getEntityInformation().getJavaType();
    this.method = method;
    this.defaultPageSize = defaultPageSize;
  }

  @Override
  public Object execute(Object[] parameters) {
    var resolvedQuery = resolveParameters(queryString, parameters);
    var collection = SolrDocumentResolver.resolveCollection(domainType);

    if (isCountQuery) {
      return solrTemplate.count(collection, new SolrQuery(resolvedQuery));
    }

    var highlightAnnotation = method.getAnnotation(Highlight.class);
    if (highlightAnnotation != null && HighlightPage.class.isAssignableFrom(method.getReturnType())) {
      var simpleQuery = buildSimpleQuery(resolvedQuery, parameters);
      simpleQuery.setHighlightOptions(HighlightAnnotationAdapter.toHighlightOptions(highlightAnnotation));
      return solrTemplate.queryForHighlightPage(collection, simpleQuery, domainType);
    }

    var facetAnnotation = method.getAnnotation(Facet.class);
    if (facetAnnotation != null && FacetPage.class.isAssignableFrom(method.getReturnType())) {
      var simpleQuery = buildSimpleQuery(resolvedQuery, parameters);
      simpleQuery.setFacetOptions(FacetAnnotationAdapter.toFacetOptions(facetAnnotation));
      return solrTemplate.queryForFacetPage(collection, simpleQuery, domainType);
    }

    var solrQuery = new SolrQuery(resolvedQuery);

    if (queryMethod.isCollectionQuery()) {
      return solrTemplate.query(collection, solrQuery, domainType);
    }

    var results = solrTemplate.query(collection, solrQuery, domainType);
    return results.isEmpty() ? null : results.getFirst();
  }

  /**
   * Builds a {@link SimpleQuery} from the resolved query string, applying pagination from the
   * method parameters if a {@link Pageable} argument is present. When no {@code Pageable} is
   * found, the configured default page size is used and a warning is logged.
   */
  private SimpleQuery buildSimpleQuery(String resolvedQuery, Object[] parameters) {
    var simpleQuery = new SimpleQuery(Criteria.raw(resolvedQuery));
    for (var param : parameters) {
      if (param instanceof Pageable pageable && pageable.isPaged()) {
        simpleQuery.setPageable(pageable);
        return simpleQuery;
      }
    }
    log.warn(
        "Repository method {}.{}() returns a paged result but was called without a Pageable "
            + "argument. Defaulting to page 0, size {}. Pass an explicit Pageable to control "
            + "pagination and suppress this warning, or configure spring.solr.default-page-size.",
        method.getDeclaringClass().getSimpleName(), method.getName(), defaultPageSize);
    simpleQuery.setPageable(PageRequest.of(0, defaultPageSize));
    return simpleQuery;
  }

  private String resolveParameters(String query, Object[] parameters) {
    var resolved = query;
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i] instanceof Pageable) {
        continue;
      }
      resolved = resolved.replace("?" + i, ClientUtils.escapeQueryChars(String.valueOf(parameters[i])));
    }
    return resolved;
  }

  @Override
  public QueryMethod getQueryMethod() {
    return queryMethod;
  }
}
