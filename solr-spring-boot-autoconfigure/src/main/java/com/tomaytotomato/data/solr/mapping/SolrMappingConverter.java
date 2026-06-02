package com.tomaytotomato.data.solr.mapping;

import java.util.Optional;

/**
 * Central holder for {@link SolrCustomConversions} used by {@link SolrDocumentReader} and
 * {@link SolrDocumentWriter} during document mapping.
 *
 * <p>Provides type-safe dispatch: given a source value and a desired target type, it scans the
 * registered {@link SolrConverterRegistration}s and applies the first matching one. Converters
 * registered as plain {@link SolrDocumentConverter} instances (without type tokens) are not
 * matched and are silently skipped — use {@link SolrConverterRegistration} to register converters
 * with explicit type information.
 */
public class SolrMappingConverter {

  private final SolrCustomConversions conversions;

  /**
   * Creates a {@code SolrMappingConverter} backed by the given custom conversions.
   *
   * @param conversions the custom conversions to use; must not be {@code null}
   */
  public SolrMappingConverter(SolrCustomConversions conversions) {
    this.conversions = conversions;
  }

  /**
   * Creates a {@code SolrMappingConverter} with no custom converters registered.
   */
  public SolrMappingConverter() {
    this(SolrCustomConversions.empty());
  }

  /**
   * Returns the custom conversions registered with this converter.
   *
   * @return the custom conversions; never {@code null}
   */
  public SolrCustomConversions getConversions() {
    return conversions;
  }

  /**
   * Returns {@code true} if a registered converter exists that can convert from
   * {@code sourceType} to {@code targetType}.
   *
   * @param sourceType the type of the source value
   * @param targetType the desired target type
   * @return {@code true} if a matching converter is registered
   */
  public boolean canConvert(Class<?> sourceType, Class<?> targetType) {
    return findRegistration(sourceType, targetType).isPresent();
  }

  /**
   * Converts {@code value} to {@code targetType} using the first registered converter that
   * matches the source and target types.
   *
   * @param <T>        the target type
   * @param value      the value to convert; must not be {@code null}
   * @param targetType the desired result type; must not be {@code null}
   * @return the converted value
   * @throws IllegalArgumentException if no matching converter is registered
   */
  @SuppressWarnings("unchecked")
  public <T> T convert(Object value, Class<T> targetType) {
    var registration = findRegistration(value.getClass(), targetType)
        .orElseThrow(() -> new IllegalArgumentException(
            "No converter registered from %s to %s"
                .formatted(value.getClass().getName(), targetType.getName())));
    return (T) ((SolrConverterRegistration<Object, T>) registration).getConverter().convert(value);
  }

  @SuppressWarnings("rawtypes")
  private Optional<SolrConverterRegistration> findRegistration(Class<?> sourceType,
      Class<?> targetType) {
    return conversions.getConverters().stream()
        .filter(c -> c instanceof SolrConverterRegistration<?, ?> reg
            && reg.getSourceType().isAssignableFrom(sourceType)
            && reg.getTargetType().isAssignableFrom(targetType))
        .map(c -> (SolrConverterRegistration) c)
        .findFirst();
  }
}
