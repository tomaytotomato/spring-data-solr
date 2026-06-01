package com.tomaytotomato.data.solr.query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.common.params.FacetParams;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Wraps a {@link Criteria} with pagination, sorting, filter queries, and field projections,
 * converting the whole into a SolrJ {@link SolrQuery} via {@link #toSolrQuery()}.
 */
public class SimpleQuery {

  private final Criteria criteria;
  private Pageable pageable;
  private Sort sort;
  private final List<String> filterQueries = new ArrayList<>();
  private final List<String> projectionFields = new ArrayList<>();
  private String requestHandler;
  private String defType;
  private String cursorMark;
  private HighlightOptions highlightOptions;
  private FacetOptions facetOptions;

  public SimpleQuery(Criteria criteria) {
    this.criteria = criteria;
  }

  public SimpleQuery(Criteria criteria, Pageable pageable) {
    this.criteria = criteria;
    this.pageable = pageable;
  }

  public SimpleQuery addFilterQuery(String fq) {
    filterQueries.add(fq);
    return this;
  }

  public SimpleQuery addProjectionOnField(String field) {
    projectionFields.add(field);
    return this;
  }

  public SimpleQuery setRequestHandler(String handler) {
    this.requestHandler = handler;
    return this;
  }

  public SimpleQuery setDefType(String defType) {
    this.defType = defType;
    return this;
  }

  public SimpleQuery setCursorMark(String cursorMark) {
    this.cursorMark = cursorMark;
    return this;
  }

  public String getCursorMark() {
    return cursorMark;
  }

  public SimpleQuery setHighlightOptions(HighlightOptions options) {
    this.highlightOptions = options;
    return this;
  }

  public HighlightOptions getHighlightOptions() {
    return highlightOptions;
  }

  public SimpleQuery setFacetOptions(FacetOptions options) {
    this.facetOptions = options;
    return this;
  }

  public FacetOptions getFacetOptions() {
    return facetOptions;
  }

  public SimpleQuery setPageable(Pageable pageable) {
    this.pageable = pageable;
    return this;
  }

  public Pageable getPageable() {
    return pageable;
  }

  public Criteria getCriteria() {
    return criteria;
  }

  public SimpleQuery setSort(Sort sort) {
    this.sort = sort;
    return this;
  }

  /** Converts this query into a SolrJ {@link SolrQuery} ready for execution. */
  public SolrQuery toSolrQuery() {
    var solrQuery = new SolrQuery(criteria.toQueryString());

    applyPagination(solrQuery);
    applySort(solrQuery);
    applyFilterQueries(solrQuery);
    applyProjectionFields(solrQuery);
    applyRequestHandler(solrQuery);
    applyDefType(solrQuery);
    applyCursorMark(solrQuery);
    applyHighlighting(solrQuery);
    applyFaceting(solrQuery);

    return solrQuery;
  }

  private void applyPagination(SolrQuery solrQuery) {
    if (pageable != null && pageable.isPaged()) {
      solrQuery.setStart((int) pageable.getOffset());
      solrQuery.setRows(pageable.getPageSize());
    }
  }

  private void applySort(SolrQuery solrQuery) {
    var effectiveSort = resolveSort();
    if (effectiveSort == null || effectiveSort.isUnsorted()) {
      return;
    }
    var sortClauses = effectiveSort.stream()
        .map(order -> order.getProperty() + " " + order.getDirection().name().toLowerCase())
        .collect(Collectors.joining(","));
    solrQuery.set("sort", sortClauses);
  }

  private Sort resolveSort() {
    if (sort != null) {
      return sort;
    }
    if (pageable != null && pageable.getSort().isSorted()) {
      return pageable.getSort();
    }
    return null;
  }

  private void applyFilterQueries(SolrQuery solrQuery) {
    if (!filterQueries.isEmpty()) {
      solrQuery.setFilterQueries(filterQueries.toArray(new String[0]));
    }
  }

  private void applyProjectionFields(SolrQuery solrQuery) {
    if (!projectionFields.isEmpty()) {
      solrQuery.setFields(projectionFields.toArray(new String[0]));
    }
  }

  private void applyRequestHandler(SolrQuery solrQuery) {
    if (requestHandler != null) {
      solrQuery.setRequestHandler(requestHandler);
    }
  }

  private void applyDefType(SolrQuery solrQuery) {
    if (defType != null) {
      solrQuery.set("defType", defType);
    }
  }

  private void applyCursorMark(SolrQuery solrQuery) {
    if (cursorMark != null) {
      solrQuery.set("cursorMark", cursorMark);
    }
  }

  private void applyHighlighting(SolrQuery solrQuery) {
    if (highlightOptions == null) {
      return;
    }
    solrQuery.setHighlight(true);
    solrQuery.set("hl.tag.pre", highlightOptions.getPreTag());
    solrQuery.set("hl.tag.post", highlightOptions.getPostTag());
    solrQuery.set("hl.snippets", highlightOptions.getSnippets());
    solrQuery.set("hl.fragsize", highlightOptions.getFragsize());
    for (var field : highlightOptions.getFields()) {
      solrQuery.addHighlightField(field);
    }
  }

  private void applyFaceting(SolrQuery solrQuery) {
    if (facetOptions == null) {
      return;
    }
    solrQuery.set(FacetParams.FACET, true);
    solrQuery.set(FacetParams.FACET_MINCOUNT, facetOptions.getMinCount());
    solrQuery.set(FacetParams.FACET_LIMIT, facetOptions.getLimit());
    if (facetOptions.getSort() != null) {
      solrQuery.set(FacetParams.FACET_SORT, facetOptions.getSort());
    }
    for (var field : facetOptions.getFacetFields()) {
      solrQuery.addFacetField(field);
    }
    for (var fq : facetOptions.getFacetQueries()) {
      solrQuery.addFacetQuery(fq);
    }
  }
}
