package com.tomaytotomato.data.solr.query;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CriteriaTest {

  @Nested
  class Is {

    @Test
    void producesFieldEqualsValueQuery() {
      var result = Criteria.where("title").is("Spring Boot").toQueryString();
      assertThat(result).isEqualTo("title:Spring\\ Boot");
    }

    @Test
    void producesMatchAllQuery() {
      var result = Criteria.where("*").is("*").toQueryString();
      assertThat(result).isEqualTo("*:*");
    }

    @Test
    void escapesSpecialCharacters() {
      var result = Criteria.where("title").is("hello+world").toQueryString();
      assertThat(result).isEqualTo("title:hello\\+world");
    }
  }

  @Nested
  class IsNot {

    @Test
    void producesNegatedQuery() {
      var result = Criteria.where("status").isNot("inactive").toQueryString();
      assertThat(result).isEqualTo("-status:inactive");
    }

    @Test
    void escapesSpecialCharactersInNegatedQuery() {
      var result = Criteria.where("title").isNot("hello (world)").toQueryString();
      assertThat(result).isEqualTo("-title:hello\\ \\(world\\)");
    }
  }

  @Nested
  class Contains {

    @Test
    void wrapsValueInWildcards() {
      var result = Criteria.where("title").contains("spring").toQueryString();
      assertThat(result).isEqualTo("title:*spring*");
    }

    @Test
    void escapesSpecialCharactersInsideWildcards() {
      var result = Criteria.where("title").contains("spring(boot").toQueryString();
      assertThat(result).isEqualTo("title:*spring\\(boot*");
    }

    @Test
    void escapesColonInsideWildcards() {
      var result = Criteria.where("title").contains("field:value").toQueryString();
      assertThat(result).isEqualTo("title:*field\\:value*");
    }
  }

  @Nested
  class NotContains {

    @Test
    void wrapsValueInWildcardsNegated() {
      var result = Criteria.where("title").notContains("spring").toQueryString();
      assertThat(result).isEqualTo("-title:*spring*");
    }

    @Test
    void escapesSpecialCharactersInsideWildcardsNegated() {
      var result = Criteria.where("title").notContains("spring(boot").toQueryString();
      assertThat(result).isEqualTo("-title:*spring\\(boot*");
    }
  }

  @Nested
  class StartsWith {

    @Test
    void appendsTrailingWildcard() {
      var result = Criteria.where("title").startsWith("Spring").toQueryString();
      assertThat(result).isEqualTo("title:Spring*");
    }

    @Test
    void escapesSpecialCharactersBeforeTrailingWildcard() {
      var result = Criteria.where("title").startsWith("Spring(Boot").toQueryString();
      assertThat(result).isEqualTo("title:Spring\\(Boot*");
    }

    @Test
    void escapesColonBeforeTrailingWildcard() {
      var result = Criteria.where("title").startsWith("field:val").toQueryString();
      assertThat(result).isEqualTo("title:field\\:val*");
    }
  }

  @Nested
  class EndsWith {

    @Test
    void prependsLeadingWildcard() {
      var result = Criteria.where("title").endsWith("Boot").toQueryString();
      assertThat(result).isEqualTo("title:*Boot");
    }

    @Test
    void escapesSpecialCharactersAfterLeadingWildcard() {
      var result = Criteria.where("title").endsWith("Spring(Boot").toQueryString();
      assertThat(result).isEqualTo("title:*Spring\\(Boot");
    }

    @Test
    void escapesColonAfterLeadingWildcard() {
      var result = Criteria.where("title").endsWith("field:val").toQueryString();
      assertThat(result).isEqualTo("title:*field\\:val");
    }
  }

  @Nested
  class Between {

    @Test
    void producesRangeQueryInclusiveOnBothEnds() {
      var result = Criteria.where("price").between(10, 50).toQueryString();
      assertThat(result).isEqualTo("price:[10 TO 50]");
    }

    @Test
    void worksWithStrings() {
      var result = Criteria.where("name").between("a", "z").toQueryString();
      assertThat(result).isEqualTo("name:[a TO z]");
    }

    @Test
    void escapesSpecialCharactersInLowerBound() {
      var result = Criteria.where("title").between("foo:bar", "z").toQueryString();
      assertThat(result).isEqualTo("title:[foo\\:bar TO z]");
    }

    @Test
    void escapesSpecialCharactersInUpperBound() {
      var result = Criteria.where("title").between("a", "test[value").toQueryString();
      assertThat(result).isEqualTo("title:[a TO test\\[value]");
    }

    @Test
    void escapesSpecialCharactersInBothBounds() {
      var result = Criteria.where("field").between("a+b", "c+d").toQueryString();
      assertThat(result).isEqualTo("field:[a\\+b TO c\\+d]");
    }
  }

  @Nested
  class LessThan {

    @Test
    void producesExclusiveUpperBoundRangeQuery() {
      var result = Criteria.where("price").lessThan(100).toQueryString();
      assertThat(result).isEqualTo("price:[* TO 100}");
    }

    @Test
    void escapesSpecialCharactersInUpperBound() {
      var result = Criteria.where("title").lessThan("foo:bar").toQueryString();
      assertThat(result).isEqualTo("title:[* TO foo\\:bar}");
    }

    @Test
    void escapesSquareBracketInUpperBound() {
      var result = Criteria.where("title").lessThan("test[value").toQueryString();
      assertThat(result).isEqualTo("title:[* TO test\\[value}");
    }
  }

  @Nested
  class LessThanEqual {

    @Test
    void producesInclusiveUpperBoundRangeQuery() {
      var result = Criteria.where("price").lessThanEqual(100).toQueryString();
      assertThat(result).isEqualTo("price:[* TO 100]");
    }

    @Test
    void escapesSpecialCharactersInUpperBound() {
      var result = Criteria.where("title").lessThanEqual("foo:bar").toQueryString();
      assertThat(result).isEqualTo("title:[* TO foo\\:bar]");
    }

    @Test
    void escapesSquareBracketInUpperBound() {
      var result = Criteria.where("title").lessThanEqual("test[value").toQueryString();
      assertThat(result).isEqualTo("title:[* TO test\\[value]");
    }
  }

  @Nested
  class GreaterThan {

    @Test
    void producesExclusiveLowerBoundRangeQuery() {
      var result = Criteria.where("price").greaterThan(10).toQueryString();
      assertThat(result).isEqualTo("price:{10 TO *]");
    }

    @Test
    void escapesSpecialCharactersInLowerBound() {
      var result = Criteria.where("title").greaterThan("foo:bar").toQueryString();
      assertThat(result).isEqualTo("title:{foo\\:bar TO *]");
    }

    @Test
    void escapesSquareBracketInLowerBound() {
      var result = Criteria.where("title").greaterThan("test[value").toQueryString();
      assertThat(result).isEqualTo("title:{test\\[value TO *]");
    }
  }

  @Nested
  class GreaterThanEqual {

    @Test
    void producesInclusiveLowerBoundRangeQuery() {
      var result = Criteria.where("price").greaterThanEqual(10).toQueryString();
      assertThat(result).isEqualTo("price:[10 TO *]");
    }

    @Test
    void escapesSpecialCharactersInLowerBound() {
      var result = Criteria.where("title").greaterThanEqual("foo:bar").toQueryString();
      assertThat(result).isEqualTo("title:[foo\\:bar TO *]");
    }

    @Test
    void escapesSquareBracketInLowerBound() {
      var result = Criteria.where("title").greaterThanEqual("test[value").toQueryString();
      assertThat(result).isEqualTo("title:[test\\[value TO *]");
    }
  }

  @Nested
  class In {

    @Test
    void producesOrGroupFromVarargs() {
      var result = Criteria.where("status").in("active", "pending").toQueryString();
      assertThat(result).isEqualTo("status:(active OR pending)");
    }

    @Test
    void producesOrGroupFromCollection() {
      var result = Criteria.where("status").in(List.of("active", "pending")).toQueryString();
      assertThat(result).isEqualTo("status:(active OR pending)");
    }

    @Test
    void escapesSpecialCharactersInValues() {
      var result = Criteria.where("tag").in("hello world", "foo+bar").toQueryString();
      assertThat(result).isEqualTo("tag:(hello\\ world OR foo\\+bar)");
    }
  }

  @Nested
  class IsNull {

    @Test
    void producesNotExistsQuery() {
      var result = Criteria.where("description").isNull().toQueryString();
      assertThat(result).isEqualTo("-description:[* TO *]");
    }
  }

  @Nested
  class IsNotNull {

    @Test
    void producesExistsQuery() {
      var result = Criteria.where("description").isNotNull().toQueryString();
      assertThat(result).isEqualTo("description:[* TO *]");
    }
  }

  @Nested
  class Boost {

    @Test
    void appendsBoostFactorToQuery() {
      var result = Criteria.where("title").is("Spring").boost(2.0f).toQueryString();
      assertThat(result).isEqualTo("title:Spring^2.0");
    }
  }

  @Nested
  class Expression {

    @Test
    void usesRawLuceneExpressionVerbatim() {
      var result = Criteria.where("score").expression("[0.5 TO 1.0]").toQueryString();
      assertThat(result).isEqualTo("score:[0.5 TO 1.0]");
    }
  }

  @Nested
  class AndChaining {

    @Test
    void combinesTwoCriteriaWithAnd() {
      var result = Criteria.where("title").startsWith("Spring")
          .and("author").is("Picard")
          .toQueryString();
      assertThat(result).isEqualTo("title:Spring* AND author:Picard");
    }

    @Test
    void combinesThreeCriteriaWithAnd() {
      var result = Criteria.where("title").is("Spring")
          .and("author").is("Picard")
          .and("year").greaterThan(2000)
          .toQueryString();
      assertThat(result).isEqualTo("title:Spring AND author:Picard AND year:{2000 TO *]");
    }
  }

  @Nested
  class OrChaining {

    @Test
    void combinesTwoCriteriaWithOr() {
      var result = Criteria.where("status").is("active")
          .or("status").is("pending")
          .toQueryString();
      assertThat(result).isEqualTo("status:active OR status:pending");
    }

    @Test
    void combinesThreeCriteriaWithOr() {
      var result = Criteria.where("status").is("active")
          .or("status").is("pending")
          .or("status").is("trial")
          .toQueryString();
      assertThat(result).isEqualTo("status:active OR status:pending OR status:trial");
    }
  }

  @Nested
  class GeoQueries {

    @Test
    void nearGeneratesGeoFilterFunctionQuery() {
      var point = new GeoPoint(51.5074, -0.1278);
      var distance = GeoDistance.kilometers(10.0);
      var result = Criteria.where("location").near(point, distance).toQueryString();
      assertThat(result).isEqualTo("{!geofilt sfield=location pt=51.5074,-0.1278 d=10.0}");
    }

    @Test
    void withinGeneratesBboxFunctionQuery() {
      var point = new GeoPoint(51.5074, -0.1278);
      var distance = GeoDistance.kilometers(10.0);
      var result = Criteria.where("location").within(point, distance).toQueryString();
      assertThat(result).isEqualTo("{!bbox sfield=location pt=51.5074,-0.1278 d=10.0}");
    }

    @Test
    void nearConvertsMillesToKilometers() {
      var point = new GeoPoint(40.7128, -74.0060);
      var distance = GeoDistance.miles(10.0);
      var result = Criteria.where("geo").near(point, distance).toQueryString();
      // 10 miles = 16.0934 km
      assertThat(result).contains("{!geofilt sfield=geo pt=40.7128,-74.006 d=");
      assertThat(result).contains("16.09");
    }

    @Test
    void nearCanBeChainedWithAndCriteria() {
      var point = new GeoPoint(51.5074, -0.1278);
      var distance = GeoDistance.kilometers(5.0);
      var result = Criteria.where("category").is("restaurant")
          .and(Criteria.where("location").near(point, distance))
          .toQueryString();
      assertThat(result).isEqualTo("category:restaurant AND {!geofilt sfield=location pt=51.5074,-0.1278 d=5.0}");
    }
  }

  @Nested
  class MixedChaining {

    @Test
    void andAfterOrChainsCorrectly() {
      var result = Criteria.where("category").is("books")
          .or("category").is("ebooks")
          .and("inStock").is("true")
          .toQueryString();
      assertThat(result).isEqualTo("category:books OR category:ebooks AND inStock:true");
    }
  }

  @Nested
  class CombiningIndependentChains {

    @Test
    void combinesTwoCriteriaWithAndUsingCriteriaOverload() {
      var first = Criteria.where("title").is("Spring");
      var second = Criteria.where("author").is("Picard");
      var result = first.and(second).toQueryString();
      assertThat(result).isEqualTo("title:Spring AND author:Picard");
    }

    @Test
    void combinesTwoCriteriaWithOrUsingCriteriaOverload() {
      var first = Criteria.where("title").is("Spring");
      var second = Criteria.where("author").is("Picard");
      var result = first.or(second).toQueryString();
      assertThat(result).isEqualTo("title:Spring OR author:Picard");
    }

    @Test
    void combinesMultiNodeChainsWithOr() {
      var branch1 = Criteria.where("title").is("Spring").and("year").greaterThan(2000);
      var branch2 = Criteria.where("author").is("Picard");
      var result = branch1.or(branch2).toQueryString();
      assertThat(result).isEqualTo("title:Spring AND year:{2000 TO *] OR author:Picard");
    }
  }
}
