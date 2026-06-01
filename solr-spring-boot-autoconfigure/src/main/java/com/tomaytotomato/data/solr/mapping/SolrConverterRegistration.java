package com.tomaytotomato.data.solr.mapping;

/**
 * Bundles a {@link SolrDocumentConverter} with its explicit source and target type tokens.
 *
 * <p>Because {@link SolrDocumentConverter} is a functional interface, generic type parameters are
 * erased at runtime. Registering a converter via {@link SolrConverterRegistration} preserves the
 * type information needed by {@link SolrMappingConverter} to select the correct converter for a
 * given field type during read and write operations.
 *
 * <p>Register converters via {@link SolrCustomConversions}:
 *
 * <pre>{@code
 * @Bean
 * public SolrCustomConversions solrCustomConversions() {
 *     return new SolrCustomConversions(List.of(
 *         SolrConverterRegistration.of(LocalDate.class, String.class, LocalDate::toString),
 *         SolrConverterRegistration.of(String.class, LocalDate.class, LocalDate::parse)
 *     ));
 * }
 * }</pre>
 *
 * @param <S> the source type
 * @param <T> the target type
 * @since 0.4.0
 */
public final class SolrConverterRegistration<S, T> {

  private final Class<S> sourceType;
  private final Class<T> targetType;
  private final SolrDocumentConverter<S, T> converter;

  private SolrConverterRegistration(Class<S> sourceType, Class<T> targetType,
      SolrDocumentConverter<S, T> converter) {
    this.sourceType = sourceType;
    this.targetType = targetType;
    this.converter = converter;
  }

  /**
   * Creates a new {@code SolrConverterRegistration} for the given source type, target type, and
   * converter function.
   *
   * @param <S>        the source type
   * @param <T>        the target type
   * @param sourceType the class of the value to convert from; must not be {@code null}
   * @param targetType the class of the value to convert to; must not be {@code null}
   * @param converter  the converter to apply; must not be {@code null}
   * @return a new registration; never {@code null}
   */
  public static <S, T> SolrConverterRegistration<S, T> of(Class<S> sourceType, Class<T> targetType,
      SolrDocumentConverter<S, T> converter) {
    return new SolrConverterRegistration<>(sourceType, targetType, converter);
  }

  /**
   * Returns the source type this converter accepts.
   *
   * @return the source type; never {@code null}
   */
  public Class<S> getSourceType() {
    return sourceType;
  }

  /**
   * Returns the target type this converter produces.
   *
   * @return the target type; never {@code null}
   */
  public Class<T> getTargetType() {
    return targetType;
  }

  /**
   * Returns the underlying converter function.
   *
   * @return the converter; never {@code null}
   */
  public SolrDocumentConverter<S, T> getConverter() {
    return converter;
  }
}
