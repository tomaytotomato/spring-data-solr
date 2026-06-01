package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import java.lang.reflect.Method;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

public class SolrQueryLookupStrategy implements QueryLookupStrategy {

  private final SolrTemplate solrTemplate;
  private final int defaultPageSize;
  private final SolrDocumentResolver documentResolver;

  public SolrQueryLookupStrategy(SolrTemplate solrTemplate) {
    this(solrTemplate, 10, null);
  }

  public SolrQueryLookupStrategy(SolrTemplate solrTemplate, int defaultPageSize) {
    this(solrTemplate, defaultPageSize, null);
  }

  /**
   * Creates a lookup strategy with environment-aware collection resolution.
   *
   * @param solrTemplate     the Solr operations delegate
   * @param defaultPageSize  default page size when no {@code Pageable} argument is present
   * @param documentResolver resolver used to expand {@code ${placeholder}} collection names;
   *                         may be {@code null} to use static resolution (no env)
   */
  public SolrQueryLookupStrategy(SolrTemplate solrTemplate, int defaultPageSize,
      SolrDocumentResolver documentResolver) {
    this.solrTemplate = solrTemplate;
    this.defaultPageSize = defaultPageSize;
    this.documentResolver = documentResolver;
  }

  @Override
  public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata,
      ProjectionFactory factory, NamedQueries namedQueries) {
    var queryMethod = new QueryMethod(method, metadata, factory);
    var queryAnnotation = method.getAnnotation(Query.class);
    if (queryAnnotation != null) {
      if (documentResolver != null) {
        return new StringBasedSolrQuery(queryMethod, solrTemplate,
            queryAnnotation.value(), queryAnnotation.count(), method, defaultPageSize,
            documentResolver);
      }
      return new StringBasedSolrQuery(queryMethod, solrTemplate,
          queryAnnotation.value(), queryAnnotation.count(), method, defaultPageSize);
    }
    if (documentResolver != null) {
      return new PartTreeSolrQuery(queryMethod, solrTemplate, method, defaultPageSize,
          documentResolver);
    }
    return new PartTreeSolrQuery(queryMethod, solrTemplate, method, defaultPageSize);
  }
}
