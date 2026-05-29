package com.tomaytotomato.data.solr.curator;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class BookCurator {

  private static final int TARGET_TOTAL = 1000;
  private static final int MIN_DESCRIPTION_LENGTH = 50;

  private static final Path DEFAULT_INPUT = Path.of(
      "solr-spring-boot-data-curator/src/main/resources/books_kaggle_7k.csv");
  private static final Path DEFAULT_OUTPUT = Path.of(
      "solr-spring-boot-sample/src/main/resources/curated-books.json");

  private static final Logger log = Logger.getLogger(BookCurator.class.getName());

  public static void main(String[] args) throws IOException {
    var inputPath = args.length > 0 ? Path.of(args[0]) : DEFAULT_INPUT;
    var outputPath = args.length > 1 ? Path.of(args[1]) : DEFAULT_OUTPUT;

    log.info("=== Solr Book Data Curator ===");
    log.info("Input:  " + inputPath.toAbsolutePath());
    log.info("Output: " + outputPath.toAbsolutePath());

    if (!Files.exists(inputPath)) {
      log.severe("ERROR: Input CSV not found at " + inputPath.toAbsolutePath());
      log.severe("Download the Kaggle 7k Books dataset:");
      log.severe("  https://www.kaggle.com/datasets/dylanjcastillo/7k-books-with-metadata");
      log.severe("Place the CSV file at: " + DEFAULT_INPUT.toAbsolutePath());
      System.exit(1);
    }

    var rawRecords = readCsv(inputPath);
    log.info("Read " + rawRecords.size() + " raw records");

    var filtered = filterRichRecords(rawRecords);
    log.info("After quality filter: " + filtered.size() + " records");

    var diversified = diversifyByCategory(filtered);
    log.info("After diversification: " + diversified.size() + " records across "
        + countCategories(diversified) + " categories");

    var curated = enrichAndTransform(diversified);
    log.info("Enriched with prices, geo-locations, and stock status");

    writeJson(curated, outputPath);
    log.info("Wrote " + curated.size() + " curated books to " + outputPath);
    printCategorySummary(curated);
  }

  static List<CsvBookRecord> readCsv(Path path) throws IOException {
    var csvMapper = new CsvMapper();
    var schema = CsvSchema.emptySchema().withHeader();
    List<CsvBookRecord> records = new ArrayList<>();

    try (MappingIterator<CsvBookRecord> iterator =
        csvMapper.readerFor(CsvBookRecord.class).with(schema).readValues(path.toFile())) {
      while (iterator.hasNext()) {
        try {
          records.add(iterator.next());
        } catch (Exception e) {
          // skip malformed rows
        }
      }
    }
    return records;
  }

  static List<CsvBookRecord> filterRichRecords(List<CsvBookRecord> records) {
    return records.stream()
        .filter(CsvBookRecord::hasRichMetadata)
        .filter(r -> r.description().length() >= MIN_DESCRIPTION_LENGTH)
        .filter(r -> parseIntSafe(r.publishedYear()) > 0)
        .filter(r -> parseDoubleSafe(r.averageRating()) > 0)
        .toList();
  }

  static List<CsvBookRecord> diversifyByCategory(List<CsvBookRecord> records) {
    var byGenre = records.stream()
        .collect(Collectors.groupingBy(
            r -> CategoryNormaliser.primaryGenre(r.categories()),
            LinkedHashMap::new,
            Collectors.toList()
        ));

    byGenre.values().forEach(list ->
        list.sort(Comparator
            .comparingInt((CsvBookRecord r) -> parseIntSafe(r.ratingsCount()))
            .reversed()
            .thenComparingDouble(r -> parseDoubleSafe(r.averageRating()))
            .reversed()
        ));

    var genreCount = byGenre.size();
    var basePerGenre = TARGET_TOTAL / genreCount;
    var remainder = TARGET_TOTAL % genreCount;

    // Round-robin: every genre gets at least basePerGenre, largest genres get +1
    var genresSorted = byGenre.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, List<CsvBookRecord>>, Integer>comparing(
            e -> e.getValue().size()).reversed())
        .toList();

    List<CsvBookRecord> selected = new ArrayList<>();
    var alreadySelected = new java.util.HashSet<CsvBookRecord>();
    var genreIndex = 0;

    for (var entry : genresSorted) {
      var quota = basePerGenre + (genreIndex < remainder ? 1 : 0);
      var take = Math.min(quota, entry.getValue().size());
      var batch = entry.getValue().subList(0, take);
      selected.addAll(batch);
      alreadySelected.addAll(batch);
      genreIndex++;
    }

    if (selected.size() < TARGET_TOTAL) {
      var extras = records.stream()
          .filter(r -> !alreadySelected.contains(r))
          .sorted(Comparator
              .comparingInt((CsvBookRecord r) -> parseIntSafe(r.ratingsCount()))
              .reversed())
          .limit(TARGET_TOTAL - selected.size())
          .toList();
      selected.addAll(extras);
    }

    return selected;
  }

  static List<CuratedBook> enrichAndTransform(List<CsvBookRecord> records) {
    var idCounter = new int[]{1};
    return records.stream()
        .map(r -> {
          var categories = CategoryNormaliser.normalise(r.categories());
          var primaryCategory = categories.getFirst();
          var pages = parseIntSafe(r.numPages());
          var rating = parseDoubleSafe(r.averageRating());
          var location = BookshopLocations.forCategory(primaryCategory);
          var price = PricingStrategy.calculate(primaryCategory, pages, rating);
          var inStock = ThreadLocalRandom.current().nextDouble() < 0.8;

          return new CuratedBook(
              String.valueOf(idCounter[0]++),
              r.title(),
              blankToNull(r.subtitle()),
              extractFirstAuthor(r.authors()),
              truncateDescription(r.description(), 500),
              categories,
              rating,
              parseIntSafe(r.ratingsCount()),
              parseIntSafe(r.publishedYear()),
              pages,
              price,
              inStock,
              location.latitude(),
              location.longitude(),
              location.name()
          );
        })
        .toList();
  }

  static void writeJson(List<CuratedBook> books, Path outputPath) throws IOException {
    Files.createDirectories(outputPath.getParent());
    var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    mapper.writeValue(outputPath.toFile(), books);
  }

  private static String extractFirstAuthor(String authors) {
    if (authors == null || authors.isBlank()) return "Unknown";
    var semiIdx = authors.indexOf(';');
    return semiIdx > 0 ? authors.substring(0, semiIdx).trim() : authors.trim();
  }

  private static String truncateDescription(String description, int maxLength) {
    if (description == null) return "";
    if (description.length() <= maxLength) return description;
    var truncated = description.substring(0, maxLength);
    var lastSpace = truncated.lastIndexOf(' ');
    return (lastSpace > 0 ? truncated.substring(0, lastSpace) : truncated) + "...";
  }

  private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  private static int parseIntSafe(String value) {
    if (value == null || value.isBlank()) return 0;
    try {
      return (int) Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double parseDoubleSafe(String value) {
    if (value == null || value.isBlank()) return 0.0;
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  private static long countCategories(List<CsvBookRecord> records) {
    return records.stream()
        .map(r -> CategoryNormaliser.primaryGenre(r.categories()))
        .distinct()
        .count();
  }

  private static void printCategorySummary(List<CuratedBook> books) {
    var sb = new StringBuilder("Category distribution:\n");
    books.stream()
        .flatMap(b -> b.categories().stream())
        .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(e -> sb.append(String.format("  %-25s %d%n", e.getKey(), e.getValue())));

    var locations = books.stream()
        .map(CuratedBook::locationName)
        .distinct()
        .count();
    sb.append(String.format("Geo locations: %d unique bookshops/libraries%n", locations));
    sb.append(String.format("Price range: %.2f - %.2f%n",
        books.stream().mapToDouble(CuratedBook::price).min().orElse(0),
        books.stream().mapToDouble(CuratedBook::price).max().orElse(0)));
    sb.append(String.format("Year range: %d - %d%n",
        books.stream().mapToInt(CuratedBook::year).min().orElse(0),
        books.stream().mapToInt(CuratedBook::year).max().orElse(0)));
    log.info(sb.toString());
  }
}
