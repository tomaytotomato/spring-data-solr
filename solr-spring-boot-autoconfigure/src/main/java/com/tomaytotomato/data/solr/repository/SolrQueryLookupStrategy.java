package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
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

  public SolrQueryLookupStrategy(SolrTemplate solrTemplate) {
    this(solrTemplate, 10);
  }

  public SolrQueryLookupStrategy(SolrTemplate solrTemplate, int defaultPageSize) {
    this.solrTemplate = solrTemplate;
    this.defaultPageSize = defaultPageSize;
  }

  @Override
  public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata,
      ProjectionFactory factory, NamedQueries namedQueries) {
    var queryMethod = new QueryMethod(method, metadata, factory);
    var queryAnnotation = method.getAnnotation(Query.class);
    if (queryAnnotation != null) {
      return new StringBasedSolrQuery(queryMethod, solrTemplate,
          queryAnnotation.value(), queryAnnotation.count(), method, defaultPageSize);
    }
    return new PartTreeSolrQuery(queryMethod, solrTemplate, method, defaultPageSize);
  }
}
