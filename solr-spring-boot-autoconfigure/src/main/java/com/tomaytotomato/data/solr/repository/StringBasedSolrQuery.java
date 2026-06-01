package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.FacetPage;
import com.tomaytotomato.data.solr.HighlightPage;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * {@link RepositoryQuery} implementation for {@link Query @Query}-annotated repository methods.
 *
 * <h3>Parameter binding</h3>
 *
 * <p>Two styles are supported:
 *
 * <ol>
 *   <li><b>Named parameters</b> (preferred) — reference method parameters by name, prefixed with a
 *       colon and preceded by whitespace, an open-parenthesis, or start-of-string so that Solr
 *       {@code field:value} syntax is not mistakenly matched:
 *       <pre>{@code @Query("title::title AND author::author")
 * List<Book> findByTitleAndAuthor(@Param("title") String title,
 *                                  @Param("author") String author);}</pre>
 *       When {@link Param @Param} is absent the parameter's <em>compiled name</em> is used
 *       instead — this requires the {@code -parameters} compiler flag, which Spring Boot enables by
 *       default.
 *   <li><b>Positional parameters</b> (legacy, backwards-compatible) — use {@code ?0}, {@code ?1},
 *       … placeholders:
 *       <pre>{@code @Query("title:?0 AND author:?1")
 * List<Book> findByTitleAndAuthor(String title, String author);}</pre>
 *       <b>Known limitation:</b> a literal {@code ?N} token that appears inside a quoted string in
 *       the query will be corrupted by substitution because the replacement is purely textual.
 *       Named parameters do not share this problem.
 * </ol>
 *
 * <h3>Escaping caveat</h3>
 *
 * <p>All parameter values are escaped with {@link ClientUtils#escapeQueryChars}, which is a
 * term-level escaper. It produces incorrect output for range queries ({@code [lo TO hi]}), function
 * queries, and phrase queries. Do not use {@code @Query} with these constructs — build the query
 * programmatically via {@link com.tomaytotomato.data.solr.query.Criteria} and
 * {@link com.tomaytotomato.data.solr.SolrTemplate} instead.
 *
 * <p>When the method returns a paged type ({@link HighlightPage} or {@link FacetPage}) and no
 * {@link Pageable} argument is found in the parameter list, a default page of
 * {@code PageRequest.of(0, defaultPageSize)} is applied and a {@code WARN} is logged. Configure
 * {@code spring.solr.default-page-size} to override the default size (default is {@code 10}).
 */
public class StringBasedSolrQuery implements RepositoryQuery {

  private static final Logger log = LoggerFactory.getLogger(StringBasedSolrQuery.class);

  /**
   * Matches a named parameter token of the form {@code :name} where the colon is preceded by
   * whitespace, an open-parenthesis, a colon (for {@code field::param} syntax), or the start of
   * the string.
   *
   * <p>The double-colon convention ({@code field::param}) deliberately disambiguates from Solr's
   * {@code field:value} syntax: when the character immediately before the {@code :} is another
   * {@code :} or a delimiter we know the token is a named parameter, not a field value.
   *
   * <p>Group 1 captures the single preceding delimiter character (or empty string at
   * start-of-string); group 2 captures the parameter name without the colon. Java variable-width
   * lookbehinds are not supported, so a capturing group is used instead.
   */
  private static final Pattern NAMED_PARAM_PATTERN =
      Pattern.compile("(^|[\\s(:])" + ":([a-zA-Z_][a-zA-Z0-9_]*)");

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
   * @param queryString     raw Solr query string (may contain {@code ?0}/{@code ?1} positional
   *                        placeholders or {@code :name} named parameter references)
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

  /**
   * Resolves parameters into the query string using named-parameter substitution first, then
   * falling back to positional {@code ?N} substitution for any remaining placeholders.
   *
   * <p>Named parameters are matched by {@link #NAMED_PARAM_PATTERN}. Parameter names are sourced
   * from {@link Param @Param} annotations when present; otherwise the compiled parameter name is
   * used (requires {@code -parameters} compiler flag).
   */
  private String resolveParameters(String query, Object[] parameters) {
    var namedValues = buildNamedParameterMap(parameters);
    var resolved = applyNamedParameters(query, namedValues);
    resolved = applyPositionalParameters(resolved, parameters);
    return resolved;
  }

  /**
   * Builds a map from parameter name to escaped value for all non-{@link Pageable} parameters.
   * {@link Param @Param} annotation names take precedence over compiled parameter names.
   */
  private Map<String, String> buildNamedParameterMap(Object[] parameters) {
    var map = new LinkedHashMap<String, String>();
    var methodParams = method.getParameters();
    for (int i = 0; i < methodParams.length && i < parameters.length; i++) {
      if (parameters[i] instanceof Pageable) {
        continue;
      }
      var name = resolveParameterName(methodParams[i]);
      map.put(name, ClientUtils.escapeQueryChars(String.valueOf(parameters[i])));
    }
    return map;
  }

  /**
   * Returns the binding name for a method parameter: the value of {@link Param @Param} when
   * present, otherwise the compiled parameter name.
   */
  private String resolveParameterName(Parameter parameter) {
    for (Annotation annotation : parameter.getAnnotations()) {
      if (annotation instanceof Param param) {
        return param.value();
      }
    }
    return parameter.getName();
  }

  /**
   * Replaces {@code :name} tokens (preceded by whitespace, {@code (}, or start-of-string) with
   * their corresponding escaped values from {@code namedValues}. Tokens that do not match any
   * known parameter name are left untouched so they can surface as a visible error rather than
   * silently vanishing.
   *
   * <p>The pattern captures the preceding delimiter character in group 1 and the parameter name
   * in group 2; the replacement preserves group 1 so that surrounding whitespace and parentheses
   * are not consumed.
   */
  private String applyNamedParameters(String query, Map<String, String> namedValues) {
    if (namedValues.isEmpty()) {
      return query;
    }
    var matcher = NAMED_PARAM_PATTERN.matcher(query);
    var sb = new StringBuilder();
    while (matcher.find()) {
      var delimiter = matcher.group(1);   // "" at start-of-string, or " " / "("
      var name = matcher.group(2);
      if (namedValues.containsKey(name)) {
        matcher.appendReplacement(sb,
            Matcher.quoteReplacement(delimiter + namedValues.get(name)));
      } else {
        matcher.appendReplacement(sb,
            Matcher.quoteReplacement(matcher.group()));
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Replaces {@code ?0}, {@code ?1}, … tokens with their corresponding escaped values.
   * {@link Pageable} parameters are skipped. This is the legacy substitution mode and runs after
   * named-parameter resolution so that queries may freely mix both styles.
   */
  private String applyPositionalParameters(String query, Object[] parameters) {
    var resolved = query;
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i] instanceof Pageable) {
        continue;
      }
      resolved = resolved.replace("?" + i,
          ClientUtils.escapeQueryChars(String.valueOf(parameters[i])));
    }
    return resolved;
  }

  @Override
  public QueryMethod getQueryMethod() {
    return queryMethod;
  }
}
