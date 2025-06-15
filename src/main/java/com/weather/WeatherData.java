package com.weather;

import java.io.Serializable;
import java.util.List;

public class WeatherData implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> times;
    private List<Double> temperatures;
    private List<Double> windSpeeds;
    private List<Double> rainfall;
    private List<Double> pressure;
    private List<Double> soilTemperature;

    public WeatherData() {
    }

    // Getters and Setters
    public List<String> getTimes() {
        return times;
    }

    public void setTimes(List<String> times) {
        this.times = times;
    }

    public List<Double> getTemperatures() {
        return temperatures;
    }

    public void setTemperatures(List<Double> temperatures) {
        this.temperatures = temperatures;
    }

    public List<Double> getWindSpeeds() {
        return windSpeeds;
    }

    public void setWindSpeeds(List<Double> windSpeeds) {
        this.windSpeeds = windSpeeds;
    }

    public List<Double> getRainfall() {
        return rainfall;
    }

    public void setRainfall(List<Double> rainfall) {
        this.rainfall = rainfall;
    }

    public List<Double> getPressure() {
        return pressure;
    }

    public void setPressure(List<Double> pressure) {
        this.pressure = pressure;
    }

    public List<Double> getSoilTemperature() {
        return soilTemperature;
    }

    public void setSoilTemperature(List<Double> soilTemperature) {
        this.soilTemperature = soilTemperature;
    }

    @Override
    public String toString() {
        return "WeatherData{" +
                "times=" + (times != null ? times.size() : 0) + " entries" +
                ", temperatures=" + (temperatures != null ? temperatures.size() : 0) + " entries" +
                ", windSpeeds=" + (windSpeeds != null ? windSpeeds.size() : 0) + " entries" +
                ", rainfall=" + (rainfall != null ? rainfall.size() : 0) + " entries" +
                ", pressure=" + (pressure != null ? pressure.size() : 0) + " entries" +
                '}';
    }
}