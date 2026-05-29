package com.tomaytotomato.data.solr.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.solr.client.solrj.util.ClientUtils;

/**
 * Fluent Solr query builder, inspired by the original spring-data-solr Criteria API.
 *
 * <p>Each Criteria instance represents a single field with one or more predicates. Instances may
 * be chained via {@link #and(String)} or {@link #or(String)} to build composite queries.
 */
public class Criteria {

  private static final String WILDCARD = "*";

  private final String field;
  private final List<Predicate> predicates = new ArrayList<>();
  private Criteria sibling;
  private Conjunction conjunction;
  private float boost = Float.NaN;

  /** Points back to the first node in the chain so {@code toQueryString()} always renders all. */
  private Criteria root = this;

  private enum Conjunction {
    AND, OR
  }

  /**
   * A single predicate clause. When {@code raw} is {@code true} the value is emitted verbatim
   * without a {@code field:} prefix — used for function queries such as geofilt/bbox.
   */
  private record Predicate(String value, boolean negated, boolean raw) {

    /** Convenience constructor for non-raw predicates (existing behaviour). */
    Predicate(String value, boolean negated) {
      this(value, negated, false);
    }
  }

  private Criteria(String field) {
    this.field = field;
  }

  /** Entry point — begins a criteria for the given Solr field. */
  public static Criteria where(String field) {
    return new Criteria(field);
  }

  /**
   * Creates a criteria that renders the given query string verbatim, without any field prefix or
   * escaping. Useful when integrating a pre-built Solr query string into a {@link SimpleQuery}.
   */
  public static Criteria raw(String queryString) {
    var criteria = new Criteria(queryString);
    criteria.predicates.add(new Predicate(queryString, false, true));
    return criteria;
  }

  // -----------------------------------------------------------------------
  // Predicates
  // -----------------------------------------------------------------------

  /** Matches documents where the field equals the given value exactly. */
  public Criteria is(Object value) {
    predicates.add(new Predicate(escape(value), false));
    return this;
  }

  /** Negation of {@link #is(Object)}. */
  public Criteria isNot(Object value) {
    predicates.add(new Predicate(escape(value), true));
    return this;
  }

  /** Matches documents where the field value contains the given substring. */
  public Criteria contains(String value) {
    predicates.add(new Predicate(WILDCARD + escape(value) + WILDCARD, false));
    return this;
  }

  /** Negation of {@link #contains(String)}. */
  public Criteria notContains(String value) {
    predicates.add(new Predicate(WILDCARD + escape(value) + WILDCARD, true));
    return this;
  }

  /** Matches documents where the field value starts with the given prefix. */
  public Criteria startsWith(String value) {
    predicates.add(new Predicate(escape(value) + WILDCARD, false));
    return this;
  }

  /** Matches documents where the field value ends with the given suffix. */
  public Criteria endsWith(String value) {
    predicates.add(new Predicate(WILDCARD + escape(value), false));
    return this;
  }

  /** Inclusive range query: {@code field:[lower TO upper]}. */
  public Criteria between(Object lower, Object upper) {
    predicates.add(new Predicate("[" + escape(lower) + " TO " + escape(upper) + "]", false));
    return this;
  }

  /** Exclusive upper-bound range: {@code field:[* TO upper)}. */
  public Criteria lessThan(Object upper) {
    predicates.add(new Predicate("[* TO " + escape(upper) + "}", false));
    return this;
  }

  /** Inclusive upper-bound range: {@code field:[* TO upper]}. */
  public Criteria lessThanEqual(Object upper) {
    predicates.add(new Predicate("[* TO " + escape(upper) + "]", false));
    return this;
  }

  /** Exclusive lower-bound range: {@code field:{lower TO *]}. */
  public Criteria greaterThan(Object lower) {
    predicates.add(new Predicate("{" + escape(lower) + " TO *]", false));
    return this;
  }

  /** Inclusive lower-bound range: {@code field:[lower TO *]}. */
  public Criteria greaterThanEqual(Object lower) {
    predicates.add(new Predicate("[" + escape(lower) + " TO *]", false));
    return this;
  }

  /** Matches documents where the field value is one of the given values. */
  public Criteria in(Object... values) {
    return in(Arrays.asList(values));
  }

  /** Matches documents where the field value is one of the given values. */
  public Criteria in(Collection<?> values) {
    var escaped = values.stream()
        .map(this::escape)
        .collect(Collectors.joining(" OR "));
    predicates.add(new Predicate("(" + escaped + ")", false));
    return this;
  }

  /** Matches documents where the field has no value (does not exist). */
  public Criteria isNull() {
    predicates.add(new Predicate("[* TO *]", true));
    return this;
  }

  /** Matches documents where the field has any value (exists). */
  public Criteria isNotNull() {
    predicates.add(new Predicate("[* TO *]", false));
    return this;
  }

  /** Sets a boost factor applied to this criteria clause. */
  public Criteria boost(float factor) {
    this.boost = factor;
    return this;
  }

  /** Inserts a raw Lucene expression for the field value verbatim (no escaping). */
  public Criteria expression(String rawExpression) {
    predicates.add(new Predicate(rawExpression, false));
    return this;
  }

  /**
   * Matches documents whose field value (a spatial point) is within the given distance of the
   * supplied point, using Solr's <em>geofilt</em> filter (circular radius, geodesic distance).
   *
   * <p>Renders as: {@code {!geofilt sfield=<field> pt=<lat,lon> d=<km>}}
   */
  public Criteria near(GeoPoint point, GeoDistance distance) {
    var km = distance.toKilometers();
    predicates.add(new Predicate(
        "{!geofilt sfield=" + field + " pt=" + point.toSolrString() + " d=" + km + "}",
        false,
        true));
    return this;
  }

  /**
   * Matches documents whose field value (a spatial point) falls within the bounding box defined by
   * the given distance from the supplied point, using Solr's <em>bbox</em> filter.
   *
   * <p>Renders as: {@code {!bbox sfield=<field> pt=<lat,lon> d=<km>}}
   */
  public Criteria within(GeoPoint point, GeoDistance distance) {
    var km = distance.toKilometers();
    predicates.add(new Predicate(
        "{!bbox sfield=" + field + " pt=" + point.toSolrString() + " d=" + km + "}",
        false,
        true));
    return this;
  }

  // -----------------------------------------------------------------------
  // Chaining
  // -----------------------------------------------------------------------

  /** Creates a new Criteria for the given field, linked to this one by AND. */
  public Criteria and(String field) {
    var next = new Criteria(field);
    next.root = this.root;
    this.sibling = next;
    this.conjunction = Conjunction.AND;
    return next;
  }

  /** Connects an independently-built Criteria chain to this one with AND. */
  public Criteria and(Criteria other) {
    return connect(other, Conjunction.AND);
  }

  /** Creates a new Criteria for the given field, linked to this one by OR. */
  public Criteria or(String field) {
    var next = new Criteria(field);
    next.root = this.root;
    this.sibling = next;
    this.conjunction = Conjunction.OR;
    return next;
  }

  /** Connects an independently-built Criteria chain to this one with OR. */
  public Criteria or(Criteria other) {
    return connect(other, Conjunction.OR);
  }

  private Criteria connect(Criteria other, Conjunction conj) {
    var otherStart = other.root;
    this.sibling = otherStart;
    this.conjunction = conj;
    var node = otherStart;
    while (node != null) {
      node.root = this.root;
      node = node.sibling;
    }
    return findTail(otherStart);
  }

  private static Criteria findTail(Criteria node) {
    while (node.sibling != null) {
      node = node.sibling;
    }
    return node;
  }

  // -----------------------------------------------------------------------
  // Query rendering
  // -----------------------------------------------------------------------

  /** Renders this criteria (and any chained siblings) as a Solr query string. */
  public String toQueryString() {
    var sb = new StringBuilder();
    root.renderInto(sb);
    return sb.toString();
  }

  private void renderInto(StringBuilder sb) {
    sb.append(renderSelf());
    if (sibling != null) {
      sb.append(" ").append(conjunction.name()).append(" ");
      sibling.renderInto(sb);
    }
  }

  private String renderSelf() {
    if (predicates.isEmpty()) {
      return field + ":*";
    }
    var rendered = predicates.stream()
        .map(p -> {
          if (p.raw()) {
            return p.value();
          }
          return p.negated() ? "-" + field + ":" + p.value() : field + ":" + p.value();
        })
        .collect(Collectors.joining(" AND "));

    return Float.isNaN(boost) ? rendered : rendered + "^" + boost;
  }

  // -----------------------------------------------------------------------
  // Escaping
  // -----------------------------------------------------------------------

  private String escape(Object value) {
    var str = String.valueOf(value);
    return WILDCARD.equals(str) ? str : ClientUtils.escapeQueryChars(str);
  }
}
