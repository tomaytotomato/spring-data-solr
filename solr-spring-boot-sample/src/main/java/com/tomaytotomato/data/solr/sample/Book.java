package com.tomaytotomato.data.solr.sample;

import com.tomaytotomato.data.solr.mapping.Score;
import com.tomaytotomato.data.solr.mapping.SolrEntity;
import java.util.List;
import org.apache.solr.client.solrj.beans.Field;

@SolrEntity(collection = "books")
public class Book {

  @Field
  private String id;

  @Field("title_t")
  private String title;

  @Field("subtitle_s")
  private String subtitle;

  @Field("author_s")
  private String author;

  @Field("description_t")
  private String description;

  @Field("categories_ss")
  private List<String> categories;

  @Field("rating_d")
  private double rating;

  @Field("ratings_count_i")
  private int ratingsCount;

  @Field("year_i")
  private int year;

  @Field("pages_i")
  private int pages;

  @Field("price_d")
  private double price;

  @Field("in_stock_b")
  private boolean inStock;

  @Field("location")
  private String location;

  @Field("location_name_s")
  private String locationName;

  @Score
  private Float score;

  public Book() {}

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getSubtitle() { return subtitle; }
  public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

  public String getAuthor() { return author; }
  public void setAuthor(String author) { this.author = author; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public List<String> getCategories() { return categories; }
  public void setCategories(List<String> categories) { this.categories = categories; }

  public double getRating() { return rating; }
  public void setRating(double rating) { this.rating = rating; }

  public int getRatingsCount() { return ratingsCount; }
  public void setRatingsCount(int ratingsCount) { this.ratingsCount = ratingsCount; }

  public int getYear() { return year; }
  public void setYear(int year) { this.year = year; }

  public int getPages() { return pages; }
  public void setPages(int pages) { this.pages = pages; }

  public double getPrice() { return price; }
  public void setPrice(double price) { this.price = price; }

  public boolean isInStock() { return inStock; }
  public void setInStock(boolean inStock) { this.inStock = inStock; }

  public String getLocation() { return location; }
  public void setLocation(String location) { this.location = location; }

  public String getLocationName() { return locationName; }
  public void setLocationName(String locationName) { this.locationName = locationName; }

  public Float getScore() { return score; }
  public void setScore(Float score) { this.score = score; }
}
