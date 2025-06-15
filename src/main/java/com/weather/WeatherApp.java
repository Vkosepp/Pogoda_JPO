package com.weather;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ListView;
import java.util.List;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;



public class WeatherApp extends Application {

    private WeatherService weatherService;
    private CacheService cacheService;
    private CityService cityService;

    // UI Components
    private RadioButton cityRadio, coordinatesRadio;
    private TextField cityField, latField, lonField;
    private ComboBox<String> cityComboBox;
    private RadioButton historicalRadio, forecastRadio;
    private DatePicker startDatePicker, endDatePicker;
    private Spinner<Integer> forecastDaysSpinner;
    private CheckBox windSpeedCheck, soilTempCheck, airTempCheck, rainCheck, pressureCheck;
    private Button generateChartButton;
    private ProgressIndicator loadingIndicator;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        weatherService = new WeatherService();
        cacheService = new CacheService();
        cityService = new CityService(); // Add this line

        primaryStage.setTitle("Aplikacja Pogodowa - Open-Meteo API");

        VBox root = createMainLayout();
        Scene scene = new Scene(root, 800, 750);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createMainLayout() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #87CEEB, #E0F6FF);");

        // Title
        Label titleLabel = new Label("Aplikacja Pogodowa");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2C3E50;");

        // Location selection
        VBox locationBox = createLocationSection();

        // Data mode selection
        VBox modeBox = createModeSection();

        // Data type selection
        VBox dataTypeBox = createDataTypeSection();

        // Control buttons
        HBox buttonBox = createButtonSection();

        // Status area
        HBox statusBox = createStatusSection();

        root.getChildren().addAll(titleLabel, locationBox, modeBox, dataTypeBox, buttonBox, statusBox);

        return root;
    }

    private VBox createLocationSection() {
        VBox locationBox = new VBox(10);
        locationBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");

        Label locationLabel = new Label("Wybór lokalizacji:");
        locationLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Radio buttons for location method
        ToggleGroup locationGroup = new ToggleGroup();
        cityRadio = new RadioButton("Nazwa miasta");
        coordinatesRadio = new RadioButton("Współrzędne geograficzne");
        cityRadio.setToggleGroup(locationGroup);
        coordinatesRadio.setToggleGroup(locationGroup);
        cityRadio.setSelected(true);

        // City selection with autocomplete
        HBox cityBox = new HBox(10);
        cityField = new TextField();
        cityField.setPromptText("Wprowadź nazwę miasta (min. 2 znaki)");
        cityField.setPrefWidth(200);

        // Setup autocomplete functionality
        setupCityAutocomplete();

        cityComboBox = new ComboBox<>();
        // Load popular cities from database instead of hardcoded list
        List<City> popularCities = cityService.getPopularCities(10);
        for (City city : popularCities) {
            cityComboBox.getItems().add(city.toString());
        }
        cityComboBox.setPromptText("Lub wybierz z listy");
        cityComboBox.setPrefWidth(200);

        cityBox.getChildren().addAll(new Label("Miasto:"), cityField, cityComboBox);

        // Coordinates selection
        HBox coordBox = new HBox(10);
        latField = new TextField();
        latField.setPromptText("52.2297"); // Warsaw latitude
        latField.setPrefWidth(100);
        lonField = new TextField();
        lonField.setPromptText("21.0122"); // Warsaw longitude
        lonField.setPrefWidth(100);

        coordBox.getChildren().addAll(new Label("Szerokość:"), latField, new Label("Długość:"), lonField);
        coordBox.setVisible(false);

        // Event handlers
        cityRadio.setOnAction(e -> {
            cityBox.setVisible(true);
            coordBox.setVisible(false);
        });

        coordinatesRadio.setOnAction(e -> {
            cityBox.setVisible(false);
            coordBox.setVisible(true);
        });

        locationBox.getChildren().addAll(locationLabel, cityRadio, coordinatesRadio, cityBox, coordBox);

        return locationBox;
    }

    private void setupCityAutocomplete() {
        // Create context menu for autocomplete suggestions
        ContextMenu contextMenu = new ContextMenu();

        cityField.textProperty().addListener((observable, oldValue, newValue) -> {
            contextMenu.hide();

            if (newValue != null && newValue.length() >= 2) {
                List<City> suggestions = cityService.searchCities(newValue, 8);

                if (!suggestions.isEmpty()) {
                    contextMenu.getItems().clear();

                    for (City city : suggestions) {
                        MenuItem item = new MenuItem(city.toString());
                        item.setOnAction(e -> {
                            cityField.setText(city.getName());
                            contextMenu.hide();
                        });
                        contextMenu.getItems().add(item);
                    }

                    if (!contextMenu.isShowing()) {
                        contextMenu.show(cityField, Side.BOTTOM, 0, 0);
                    }
                }
            }
        });

        // Hide context menu when field loses focus
        cityField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                contextMenu.hide();
            }
        });
    }

    private VBox createModeSection() {
        VBox modeBox = new VBox(10);
        modeBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");

        Label modeLabel = new Label("Tryb danych:");
        modeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        ToggleGroup modeGroup = new ToggleGroup();
        historicalRadio = new RadioButton("Dane historyczne");
        forecastRadio = new RadioButton("Prognoza pogody");
        historicalRadio.setToggleGroup(modeGroup);
        forecastRadio.setToggleGroup(modeGroup);
        historicalRadio.setSelected(true);

        // Historical data controls
        HBox historicalBox = new HBox(10);
        startDatePicker = new DatePicker(LocalDate.now().minusDays(7));
        endDatePicker = new DatePicker(LocalDate.now());
        historicalBox.getChildren().addAll(new Label("Od:"), startDatePicker, new Label("Do:"), endDatePicker);

        // Forecast controls
        HBox forecastBox = new HBox(10);
        forecastDaysSpinner = new Spinner<>(1, 16, 7);
        forecastBox.getChildren().addAll(new Label("Dni prognozy:"), forecastDaysSpinner);
        forecastBox.setVisible(false);

        // Event handlers
        historicalRadio.setOnAction(e -> {
            historicalBox.setVisible(true);
            forecastBox.setVisible(false);
        });

        forecastRadio.setOnAction(e -> {
            historicalBox.setVisible(false);
            forecastBox.setVisible(true);
        });

        modeBox.getChildren().addAll(modeLabel, historicalRadio, forecastRadio, historicalBox, forecastBox);

        return modeBox;
    }

    private VBox createDataTypeSection() {
        VBox dataTypeBox = new VBox(10);
        dataTypeBox.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-background-radius: 10;");

        Label dataLabel = new Label("Dane do wizualizacji:");
        dataLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        windSpeedCheck = new CheckBox("Prędkość wiatru (km/h)");
        soilTempCheck = new CheckBox("Temperatura gleby (°C)");
        airTempCheck = new CheckBox("Temperatura powietrza (°C)");
        rainCheck = new CheckBox("Opady (mm)");
        pressureCheck = new CheckBox("Ciśnienie powierzchniowe (hPa)");

        // Select air temperature by default
        airTempCheck.setSelected(true);

        dataTypeBox.getChildren().addAll(dataLabel, windSpeedCheck, soilTempCheck,
                airTempCheck, rainCheck, pressureCheck);

        return dataTypeBox;
    }

    private HBox createButtonSection() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        generateChartButton = new Button("Generuj wykres");
        generateChartButton.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 20;");
        generateChartButton.setOnAction(e -> generateChart());

        Button cityManagementButton = new Button("Zarządzanie miastami");
        cityManagementButton.setStyle("-fx-background-color: #9B59B6; -fx-text-fill: white; " +
                "-fx-font-size: 14px; -fx-padding: 10 20;");
        cityManagementButton.setOnAction(e -> showCityManagementDialog());

        buttonBox.getChildren().addAll(generateChartButton, cityManagementButton);

        return buttonBox;
    }

    private HBox createStatusSection() {
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER);

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setVisible(false);
        loadingIndicator.setPrefSize(30, 30);

        statusLabel = new Label("Gotowy");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7F8C8D;");

        statusBox.getChildren().addAll(loadingIndicator, statusLabel);

        return statusBox;
    }

    private void generateChart() {
        if (!validateInput()) {
            return;
        }

        loadingIndicator.setVisible(true);
        statusLabel.setText("Pobieranie danych...");
        generateChartButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                WeatherData weatherData = fetchWeatherData();

                javafx.application.Platform.runLater(() -> {
                    createChartWindows(weatherData);
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Wykresy wygenerowane");
                    generateChartButton.setDisable(false);
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    showError("Błąd podczas pobierania danych: " + e.getMessage());
                    loadingIndicator.setVisible(false);
                    statusLabel.setText("Błąd");
                    generateChartButton.setDisable(false);
                });
            }
        });
    }

    private boolean validateInput() {
        if (cityRadio.isSelected()) {
            if (cityField.getText().trim().isEmpty() && cityComboBox.getValue() == null) {
                showError("Proszę wprowadzić nazwę miasta lub wybrać z listy");
                return false;
            }
        } else {
            try {
                Double.parseDouble(latField.getText());
                Double.parseDouble(lonField.getText());
            } catch (NumberFormatException e) {
                showError("Proszę wprowadzić poprawne współrzędne geograficzne");
                return false;
            }
        }

        if (!windSpeedCheck.isSelected() && !soilTempCheck.isSelected() &&
                !airTempCheck.isSelected() && !rainCheck.isSelected() && !pressureCheck.isSelected()) {
            showError("Proszę wybrać przynajmniej jeden typ danych do wizualizacji");
            return false;
        }

        return true;
    }

    private WeatherData fetchWeatherData() throws Exception {
        double lat, lon;

        if (cityRadio.isSelected()) {
            String cityName = cityField.getText().trim();
            if (cityName.isEmpty() && cityComboBox.getValue() != null) {
                // Extract city name from ComboBox selection (format: "Name, Country")
                String selection = cityComboBox.getValue();
                cityName = selection.split(",")[0].trim();
            }

            if (cityName.isEmpty()) {
                throw new Exception("Proszę wprowadzić nazwę miasta");
            }

            // Use CityService to find coordinates
            City city = cityService.findCityByName(cityName);
            if (city != null) {
                lat = city.getLatitude();
                lon = city.getLongitude();
                System.out.println("Znaleziono miasto: " + city.toString() +
                        " na współrzędnych: " + lat + ", " + lon);
            } else {
                // Fallback to hardcoded coordinates for popular Polish cities
                switch (cityName.toLowerCase()) {
                    case "warszawa" -> { lat = 52.2297; lon = 21.0122; }
                    case "kraków" -> { lat = 50.0647; lon = 19.9450; }
                    case "gdańsk" -> { lat = 54.3520; lon = 18.6466; }
                    case "wrocław" -> { lat = 51.1079; lon = 17.0385; }
                    case "poznań" -> { lat = 52.4064; lon = 16.9252; }
                    case "łódź" -> { lat = 51.7592; lon = 19.4550; }
                    default -> throw new Exception("Nie znaleziono miasta: " + cityName +
                            ". Spróbuj użyć współrzędnych geograficznych.");
                }
            }
        } else {
            lat = Double.parseDouble(latField.getText());
            lon = Double.parseDouble(lonField.getText());
        }

        if (historicalRadio.isSelected()) {
            return weatherService.getHistoricalData(lat, lon,
                    startDatePicker.getValue(), endDatePicker.getValue());
        } else {
            return weatherService.getForecastData(lat, lon, forecastDaysSpinner.getValue());
        }
    }

    // ADD THIS NEW METHOD FOR CITY MANAGEMENT (after fetchWeatherData):
    private void showCityManagementDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Zarządzanie miastami");

        VBox dialogBox = new VBox(10);
        dialogBox.setPadding(new Insets(15));

        // Countries ComboBox
        ComboBox<String> countryComboBox = new ComboBox<>();
        countryComboBox.getItems().addAll(cityService.getAvailableCountries());
        countryComboBox.setPromptText("Wybierz kraj");

        // Cities ListView
        ListView<City> citiesListView = new ListView<>();
        citiesListView.setPrefHeight(300);

        countryComboBox.setOnAction(e -> {
            String selectedCountry = countryComboBox.getValue();
            if (selectedCountry != null) {
                List<City> citiesInCountry = cityService.getCitiesByCountry(selectedCountry);
                citiesListView.getItems().clear();
                citiesListView.getItems().addAll(citiesInCountry);
            }
        });

        // Use selected city button
        Button useSelectedButton = new Button("Użyj wybranego miasta");
        useSelectedButton.setOnAction(e -> {
            City selected = citiesListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cityField.setText(selected.getName());
                dialog.close();
            }
        });

        Button closeButton = new Button("Zamknij");
        closeButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, useSelectedButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER);

        dialogBox.getChildren().addAll(
                new Label("Wybierz kraj:"), countryComboBox,
                new Label("Miasta:"), citiesListView,
                buttonBox
        );

        Scene dialogScene = new Scene(dialogBox, 400, 500);
        dialog.setScene(dialogScene);
        dialog.show();
    }

    private void createChartWindows(WeatherData weatherData) {
        // Create separate windows for each selected data type
        if (airTempCheck.isSelected()) {
            createIndividualChartWindow("Temperatura (°C)",
                    weatherData.getTemperatures(), weatherData.getTimes(), weatherData);
        }

        if (windSpeedCheck.isSelected()) {
            createIndividualChartWindow("Prędkość wiatru (km/h)",
                    weatherData.getWindSpeeds(), weatherData.getTimes(), weatherData);
        }

        if (rainCheck.isSelected()) {
            createIndividualChartWindow("Opady (mm)",
                    weatherData.getRainfall(), weatherData.getTimes(), weatherData);
        }

        if (pressureCheck.isSelected()) {
            createIndividualChartWindow("Ciśnienie (hPa)",
                    weatherData.getPressure(), weatherData.getTimes(), weatherData);
        }

        if (soilTempCheck.isSelected()) {
            createIndividualChartWindow("Temperatura gleby (°C)",
                    weatherData.getSoilTemperature(), weatherData.getTimes(), weatherData);
        }
    }

    private void createIndividualChartWindow(String title, List<Double> data, List<String> times, WeatherData weatherData) {
        Stage chartStage = new Stage();
        chartStage.initModality(Modality.NONE);
        chartStage.setTitle("Wykres: " + title);

        VBox chartBox = new VBox(10);
        chartBox.setPadding(new Insets(15));

        LineChart<String, Number> chart = createChart(title, data, times);
        chartBox.getChildren().add(chart);

        // Export button
        Button exportButton = new Button("Eksportuj dane");
        exportButton.setStyle("-fx-background-color: #27AE60; -fx-text-fill: white;");
        exportButton.setOnAction(e -> exportData(weatherData));

        chartBox.getChildren().add(exportButton);

        ScrollPane scrollPane = new ScrollPane(chartBox);
        scrollPane.setFitToWidth(true);

        Scene chartScene = new Scene(scrollPane, 900, 500);
        chartStage.setScene(chartScene);
        chartStage.show();
    }

    private LineChart<String, Number> createChart(String title, List<Double> data, List<String> times) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Data i godzina");
        yAxis.setLabel(title);

        // Automatyczne skalowanie osi Y na podstawie danych
        if (data == null || data.isEmpty()) {
            System.out.println("No data available for chart: " + title);
            // Create empty chart with default range
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(100);
        } else {
            double min = data.stream().filter(d -> d != null).mapToDouble(Double::doubleValue).min().orElse(0);
            double max = data.stream().filter(d -> d != null).mapToDouble(Double::doubleValue).max().orElse(100);

            // Dodaj margines 10% z każdej strony dla lepszej wizualizacji
            double margin = (max - min) * 0.1;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(Math.max(0, min - margin));
            yAxis.setUpperBound(max + margin);

            // Dla ciśnienia, ustaw jeszcze bardziej precyzyjne granice
            if (title.contains("Ciśnienie")) {
                yAxis.setLowerBound(Math.max(900, min - 20));
                yAxis.setUpperBound(Math.min(1100, max + 20));
            }
        }

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setPrefHeight(400);
        chart.setCreateSymbols(true); // Pokaż punkty na wykresie

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(title);

        if (times == null || times.isEmpty()) {
            System.out.println("No time data available for chart: " + title);
            chart.getData().add(series);
            return chart;
        }

        // Generate 3-hour interval axis labels
        List<String> axisLabels = generate3HourAxisLabels(times);

        // Add all axis labels first
        for (String label : axisLabels) {
            xAxis.getCategories().add(label);
        }

        // Add data points with proper positioning
        for (int i = 0; i < data.size() && i < times.size(); i++) {
            if (data.get(i) != null) {
                String timeLabel = formatTimeForChart(times.get(i));
                series.getData().add(new XYChart.Data<>(timeLabel, data.get(i)));
            }
        }

        chart.getData().add(series);

        // Obróć etykiety dla lepszej czytelności
        xAxis.setTickLabelRotation(45);

        return chart;
    }

    private List<String> generate3HourAxisLabels(List<String> times) {
        List<String> axisLabels = new ArrayList<>();

        if (times.isEmpty()) {
            return axisLabels;
        }

        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm");

        try {
            // Parse first time to get starting point
            LocalDateTime startTime = LocalDateTime.parse(times.get(0), inputFormatter);
            LocalDateTime endTime = LocalDateTime.parse(times.get(times.size() - 1), inputFormatter);

            // Find the first 3-hour mark (00:00, 03:00, 06:00, 09:00, 12:00, 15:00, 18:00, 21:00)
            LocalDateTime current = startTime;
            int hour = current.getHour();
            int nextThreeHourMark = ((hour / 3) + 1) * 3;
            if (nextThreeHourMark >= 24) {
                current = current.plusDays(1).withHour(0).withMinute(0);
            } else {
                current = current.withHour(nextThreeHourMark).withMinute(0);
            }

            // Generate 3-hour interval labels
            while (!current.isAfter(endTime)) {
                axisLabels.add(current.format(outputFormatter));
                current = current.plusHours(3);
            }

        } catch (DateTimeParseException e) {
            // Fallback: generate simple labels every 3 hours from available data
            for (int i = 0; i < times.size(); i += 3) {
                axisLabels.add(formatTimeForChart(times.get(i)));
            }
        }

        return axisLabels;
    }

    private String formatTimeForChart(String timeStr) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm");

        try {
            LocalDateTime dateTime = LocalDateTime.parse(timeStr, inputFormatter);
            return dateTime.format(outputFormatter);
        } catch (DateTimeParseException e) {
            // Fallback parsing
            if (timeStr.length() >= 16) {
                try {
                    String dateTimePart = timeStr.substring(0, 16);
                    LocalDateTime dateTime = LocalDateTime.parse(dateTimePart, inputFormatter);
                    return dateTime.format(outputFormatter);
                } catch (Exception ex) {
                    return timeStr.length() > 16 ? timeStr.substring(0, 16) : timeStr;
                }
            } else {
                return timeStr;
            }
        }
    }

    private void exportData(WeatherData weatherData) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz dane pogodowe");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Pliki tekstowe", "*.txt"));

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Eksport danych pogodowych\n");
                writer.write("Data eksportu: " + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + "\n\n");

                List<String> times = weatherData.getTimes();
                List<Double> temps = weatherData.getTemperatures();
                List<Double> winds = weatherData.getWindSpeeds();
                List<Double> rain = weatherData.getRainfall();
                List<Double> pressure = weatherData.getPressure();

                writer.write("Czas\tTemperatura(°C)\tWiatr(km/h)\tOpady(mm)\tCiśnienie(hPa)\n");

                for (int i = 0; i < times.size(); i++) {
                    writer.write(String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\n",
                            times.get(i),
                            temps.size() > i ? temps.get(i) : 0.0,
                            winds.size() > i ? winds.get(i) : 0.0,
                            rain.size() > i ? rain.get(i) : 0.0,
                            pressure.size() > i ? pressure.get(i) : 0.0
                    ));
                }

                statusLabel.setText("Dane wyeksportowane do: " + file.getName());

            } catch (IOException e) {
                showError("Błąd podczas zapisywania pliku: " + e.getMessage());
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Błąd");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}