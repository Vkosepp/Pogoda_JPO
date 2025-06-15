package com.weather;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WeatherService {

    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String HISTORICAL_URL = "https://archive-api.open-meteo.com/v1/archive";
    private final Gson gson;
    private final CacheService cacheService;

    public WeatherService() {
        this.gson = new Gson();
        this.cacheService = new CacheService();
    }

    public WeatherData getHistoricalData(double latitude, double longitude,
                                         LocalDate startDate, LocalDate endDate) throws Exception {

        String cacheKey = String.format(java.util.Locale.US, "historical_%.4f_%.4f_%s_%s",
                latitude, longitude, startDate.toString(), endDate.toString());

        // Check cache first
        WeatherData cachedData = cacheService.getWeatherData(cacheKey);
        if (cachedData != null) {
            System.out.println("Zwracam dane z cache");
            return cachedData;
        }

        // Use proper historical API endpoint with Locale.US to ensure dots instead of commas
        // Added soil_temperature_0cm to the hourly parameters
        String url = String.format(java.util.Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s" +
                        "&hourly=temperature_2m,wind_speed_10m,precipitation,surface_pressure,soil_temperature_0cm" +
                        "&timezone=Europe/Warsaw",
                HISTORICAL_URL, latitude, longitude,
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        System.out.println("Requesting historical data from URL: " + url);
        String jsonResponse = makeHttpRequest(url);
        WeatherData weatherData = parseWeatherResponse(jsonResponse);

        // Cache the data for 1 hour (historical data doesn't change much)
        cacheService.cacheWeatherData(cacheKey, weatherData, 3600);

        return weatherData;
    }

    public WeatherData getForecastData(double latitude, double longitude, int forecastDays) throws Exception {

        String cacheKey = String.format(java.util.Locale.US, "forecast_%.4f_%.4f_%d", latitude, longitude, forecastDays);

        // Check cache first
        WeatherData cachedData = cacheService.getWeatherData(cacheKey);
        if (cachedData != null) {
            System.out.println("Zwracam dane prognozy z cache");
            return cachedData;
        }

        // Ensure forecast days is within valid range (1-16)
        if (forecastDays < 1) forecastDays = 1;
        if (forecastDays > 16) forecastDays = 16;

        // Added soil_temperature_0cm to the hourly parameters
        String url = String.format(java.util.Locale.US,
                "%s?latitude=%.4f&longitude=%.4f&forecast_days=%d" +
                        "&hourly=temperature_2m,wind_speed_10m,precipitation,surface_pressure,soil_temperature_0cm" +
                        "&timezone=Europe/Warsaw",
                FORECAST_URL, latitude, longitude, forecastDays
        );

        System.out.println("Requesting forecast data from URL: " + url);
        String jsonResponse = makeHttpRequest(url);
        WeatherData weatherData = parseWeatherResponse(jsonResponse);

        // Cache forecast data for 30 minutes (changes more frequently)
        cacheService.cacheWeatherData(cacheKey, weatherData, 1800);

        return weatherData;
    }

    private String makeHttpRequest(String url) throws IOException, ParseException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");
            request.setHeader("User-Agent", "WeatherApp/1.0");

            String responseBody = client.execute(request, response -> {
                int statusCode = response.getCode();

                if (statusCode != 200) {
                    System.err.println("HTTP Error Response: " + statusCode + " - " + response.getReasonPhrase());
                    throw new IOException("HTTP Error: " + statusCode + " - " +
                            response.getReasonPhrase());
                }

                String body = EntityUtils.toString(response.getEntity());
                if (body == null || body.trim().isEmpty()) {
                    throw new IOException("Otrzymano pustą odpowiedź z API");
                }

                return body;
            });

            return responseBody;
        }
    }

    private WeatherData parseWeatherResponse(String jsonResponse) {
        try {
            System.out.println("Parsing JSON response...");
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);

            // Check for API errors
            if (root.has("error")) {
                String errorMessage = root.has("reason") ?
                        root.get("reason").getAsString() :
                        "Unknown API error";
                throw new RuntimeException("API Error: " + errorMessage);
            }

            JsonObject hourly = root.getAsJsonObject("hourly");
            if (hourly == null) {
                throw new RuntimeException("Brak danych godzinowych w odpowiedzi API");
            }

            WeatherData weatherData = new WeatherData();

            // Parse time data
            JsonArray timeArray = hourly.getAsJsonArray("time");
            if (timeArray != null) {
                List<String> times = new ArrayList<>();
                for (int i = 0; i < timeArray.size(); i++) {
                    times.add(timeArray.get(i).getAsString());
                }
                weatherData.setTimes(times);
                System.out.println("Parsed " + times.size() + " time entries");
            }

            // Parse temperature data
            JsonArray tempArray = hourly.getAsJsonArray("temperature_2m");
            if (tempArray != null) {
                List<Double> temperatures = new ArrayList<>();
                for (int i = 0; i < tempArray.size(); i++) {
                    if (tempArray.get(i).isJsonNull()) {
                        temperatures.add(0.0);
                    } else {
                        temperatures.add(tempArray.get(i).getAsDouble());
                    }
                }
                weatherData.setTemperatures(temperatures);
                System.out.println("Parsed " + temperatures.size() + " temperature entries");
            }

            // Parse wind speed data
            JsonArray windArray = hourly.getAsJsonArray("wind_speed_10m");
            if (windArray != null) {
                List<Double> windSpeeds = new ArrayList<>();
                for (int i = 0; i < windArray.size(); i++) {
                    if (windArray.get(i).isJsonNull()) {
                        windSpeeds.add(0.0);
                    } else {
                        windSpeeds.add(windArray.get(i).getAsDouble());
                    }
                }
                weatherData.setWindSpeeds(windSpeeds);
                System.out.println("Parsed " + windSpeeds.size() + " wind speed entries");
            }

            // Parse precipitation data
            JsonArray rainArray = hourly.getAsJsonArray("precipitation");
            if (rainArray != null) {
                List<Double> rainfall = new ArrayList<>();
                for (int i = 0; i < rainArray.size(); i++) {
                    if (rainArray.get(i).isJsonNull()) {
                        rainfall.add(0.0);
                    } else {
                        rainfall.add(rainArray.get(i).getAsDouble());
                    }
                }
                weatherData.setRainfall(rainfall);
                System.out.println("Parsed " + rainfall.size() + " precipitation entries");
            }

            // Parse pressure data
            JsonArray pressureArray = hourly.getAsJsonArray("surface_pressure");
            if (pressureArray != null) {
                List<Double> pressure = new ArrayList<>();
                for (int i = 0; i < pressureArray.size(); i++) {
                    if (pressureArray.get(i).isJsonNull()) {
                        pressure.add(1013.25); // Standard atmospheric pressure
                    } else {
                        pressure.add(pressureArray.get(i).getAsDouble());
                    }
                }
                weatherData.setPressure(pressure);
                System.out.println("Parsed " + pressure.size() + " pressure entries");
            }

            // Parse soil temperature data - THIS WAS MISSING!
            JsonArray soilTempArray = hourly.getAsJsonArray("soil_temperature_0cm");
            if (soilTempArray != null) {
                List<Double> soilTemperature = new ArrayList<>();
                for (int i = 0; i < soilTempArray.size(); i++) {
                    if (soilTempArray.get(i).isJsonNull()) {
                        soilTemperature.add(0.0);
                    } else {
                        soilTemperature.add(soilTempArray.get(i).getAsDouble());
                    }
                }
                weatherData.setSoilTemperature(soilTemperature);
                System.out.println("Parsed " + soilTemperature.size() + " soil temperature entries");
            } else {
                // If soil temperature data is not available, create an empty list to prevent null pointer
                weatherData.setSoilTemperature(new ArrayList<>());
                System.out.println("No soil temperature data available, using empty list");
            }

            System.out.println("Successfully parsed weather data");
            return weatherData;

        } catch (Exception e) {
            System.err.println("Błąd podczas parsowania odpowiedzi JSON: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Nie można sparsować danych pogodowych: " + e.getMessage());
        }
    }
}