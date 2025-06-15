package com.weather;

import java.io.Serializable;

public class City implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String country;
    private double latitude;
    private double longitude;
    private int population;

    public City() {}

    public City(String name, String country, double latitude, double longitude, int population) {
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.population = population;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getPopulation() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = population;
    }

    @Override
    public String toString() {
        return name + ", " + country;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        City city = (City) obj;
        return name.equals(city.name) && country.equals(city.country);
    }

    @Override
    public int hashCode() {
        return name.hashCode() + country.hashCode();
    }
}