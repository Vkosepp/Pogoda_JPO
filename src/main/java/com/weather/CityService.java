package com.weather;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class CityService {

    private static final String CITIES_FILE = "/cities.csv";
    private List<City> cities;
    private Map<String, City> cityLookup;

    public CityService() {
        loadCitiesFromCSV();
        buildLookupMap();
    }

    private void loadCitiesFromCSV() {
        cities = new ArrayList<>();

        try (InputStream inputStream = getClass().getResourceAsStream(CITIES_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            if (inputStream == null) {
                System.err.println("Plik cities.csv nie został znaleziony w resources");
                loadDefaultCities();
                return;
            }

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Pomiń nagłówek
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                City city = parseCityFromCSVLine(line);
                if (city != null) {
                    cities.add(city);
                }
            }

            System.out.println("Załadowano " + cities.size() + " miast z pliku CSV");

        } catch (IOException e) {
            System.err.println("Błąd podczas wczytywania pliku miast: " + e.getMessage());
            loadDefaultCities();
        }
    }

    private City parseCityFromCSVLine(String line) {
        try {
            // Handle CSV with quoted fields
            String[] parts = parseCSVLine(line);

            if (parts.length >= 11) {
                String name = parts[0].trim(); // city
                String country = parts[4].trim(); // country
                double latitude = Double.parseDouble(parts[2].trim()); // lat
                double longitude = Double.parseDouble(parts[3].trim()); // lng
                int population = parseIntSafely(parts[9].trim()); // population

                return new City(name, country, latitude, longitude, population);
            }

        } catch (Exception e) {
            System.err.println("Błąd parsowania linii CSV: " + line + " - " + e.getMessage());
        }

        return null;
    }

    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }

        // Add the last field
        result.add(currentField.toString());

        return result.toArray(new String[0]);
    }

    private int parseIntSafely(String value) {
        try {
            return Integer.parseInt(value.replace("\"", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void loadDefaultCities() {
        // Fallback - wczytaj domyślne miasta jeśli CSV nie jest dostępny
        cities = Arrays.asList(
                new City("Warszawa", "Poland", 52.2297, 21.0122, 1790658),
                new City("Kraków", "Poland", 50.0647, 19.9450, 779115),
                new City("Gdańsk", "Poland", 54.3520, 18.6466, 470907),
                new City("Wrocław", "Poland", 51.1079, 17.0385, 641607),
                new City("Poznań", "Poland", 52.4064, 16.9252, 540372),
                new City("Łódź", "Poland", 51.7592, 19.4550, 679941)
        );
        System.out.println("Załadowano domyślne miasta (fallback)");
    }

    private void buildLookupMap() {
        cityLookup = new HashMap<>();

        for (City city : cities) {
            // Klucze wyszukiwania (bez polskich znaków, małe litery)
            String normalizedName = normalizeString(city.getName());
            String fullKey = normalizedName + "_" + normalizeString(city.getCountry());

            cityLookup.put(normalizedName, city);
            cityLookup.put(fullKey, city);
            cityLookup.put(city.getName().toLowerCase(), city);
        }
    }

    private String normalizeString(String input) {
        return input.toLowerCase()
                .replace("ą", "a")
                .replace("ć", "c")
                .replace("ę", "e")
                .replace("ł", "l")
                .replace("ń", "n")
                .replace("ó", "o")
                .replace("ś", "s")
                .replace("ź", "z")
                .replace("ż", "z")
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Wyszukuje miasto po nazwie (obsługuje polskie znaki)
     */
    public City findCityByName(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return null;
        }

        String normalizedInput = normalizeString(cityName.trim());
        return cityLookup.get(normalizedInput);
    }

    /**
     * Wyszukuje miasta pasujące do wzorca (auto-complete)
     */
    public List<City> searchCities(String query, int maxResults) {
        if (query == null || query.trim().length() < 2) {
            return new ArrayList<>();
        }

        String normalizedQuery = normalizeString(query.trim());

        return cities.stream()
                .filter(city -> normalizeString(city.getName()).contains(normalizedQuery))
                .sorted((c1, c2) -> {
                    // Sortuj według populacji (większe miasta pierwsze)
                    return Integer.compare(c2.getPopulation(), c1.getPopulation());
                })
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Zwraca wszystkie miasta z danego kraju
     */
    public List<City> getCitiesByCountry(String country) {
        return cities.stream()
                .filter(city -> city.getCountry().equalsIgnoreCase(country))
                .sorted((c1, c2) -> Integer.compare(c2.getPopulation(), c1.getPopulation()))
                .collect(Collectors.toList());
    }

    /**
     * Zwraca popularne miasta (dla ComboBox)
     */
    public List<City> getPopularCities(int count) {
        return cities.stream()
                .sorted((c1, c2) -> Integer.compare(c2.getPopulation(), c1.getPopulation()))
                .limit(count)
                .collect(Collectors.toList());
    }

    /**
     * Zwraca wszystkie dostępne kraje
     */
    public List<String> getAvailableCountries() {
        return cities.stream()
                .map(City::getCountry)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<City> getAllCities() {
        return new ArrayList<>(cities);
    }
}