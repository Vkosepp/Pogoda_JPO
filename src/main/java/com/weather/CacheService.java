package com.weather;

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

public class CacheService {

    private static final String CACHE_DIR = "weather_cache";
    private final Gson gson;
    private JedisPool jedisPool;
    private boolean redisAvailable = false;

    public CacheService() {
        this.gson = new Gson();
        initializeRedis();
        initializeFileCache();
    }

    private void initializeRedis() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);

            // Try to connect to local Redis instance
            jedisPool = new JedisPool(poolConfig, "localhost", 6379, 2000);

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                redisAvailable = true;
                System.out.println("Redis cache dostępny");
            }
        } catch (Exception e) {
            System.out.println("Redis niedostępny, używam cache plików: " + e.getMessage());
            redisAvailable = false;
        }
    }

    private void initializeFileCache() {
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
        } catch (IOException e) {
            System.err.println("Nie można utworzyć katalogu cache: " + e.getMessage());
        }
    }

    public void cacheWeatherData(String key, WeatherData data, int ttlSeconds) {
        if (redisAvailable) {
            cacheWithRedis(key, data, ttlSeconds);
        } else {
            cacheWithFile(key, data, ttlSeconds);
        }
    }

    public WeatherData getWeatherData(String key) {
        if (redisAvailable) {
            return getFromRedis(key);
        } else {
            return getFromFile(key);
        }
    }

    private void cacheWithRedis(String key, WeatherData data, int ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            CachedData cachedData = new CachedData(data, Instant.now().getEpochSecond() + ttlSeconds);
            String jsonData = gson.toJson(cachedData);
            jedis.setex("weather:" + key, ttlSeconds, jsonData);
            System.out.println("Dane zapisane w Redis cache");
        } catch (JedisException e) {
            System.err.println("Błąd Redis cache: " + e.getMessage());
            // Fallback to file cache
            cacheWithFile(key, data, ttlSeconds);
        }
    }

    private WeatherData getFromRedis(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String jsonData = jedis.get("weather:" + key);
            if (jsonData != null) {
                CachedData cachedData = gson.fromJson(jsonData, CachedData.class);
                if (cachedData.getExpiryTime() > Instant.now().getEpochSecond()) {
                    System.out.println("Dane pobrane z Redis cache");
                    return cachedData.getData();
                } else {
                    jedis.del("weather:" + key);
                }
            }
        } catch (JedisException e) {
            System.err.println("Błąd odczytu z Redis: " + e.getMessage());
            // Fallback to file cache
            return getFromFile(key);
        }
        return null;
    }

    private void cacheWithFile(String key, WeatherData data, int ttlSeconds) {
        try {
            String fileName = sanitizeFileName(key) + ".cache";
            Path filePath = Paths.get(CACHE_DIR, fileName);

            CachedData cachedData = new CachedData(data, Instant.now().getEpochSecond() + ttlSeconds);
            String jsonData = gson.toJson(cachedData);

            Files.write(filePath, jsonData.getBytes());
            System.out.println("Dane zapisane w cache pliku: " + fileName);

        } catch (IOException e) {
            System.err.println("Błąd zapisu do cache pliku: " + e.getMessage());
        }
    }

    private WeatherData getFromFile(String key) {
        try {
            String fileName = sanitizeFileName(key) + ".cache";
            Path filePath = Paths.get(CACHE_DIR, fileName);

            if (!Files.exists(filePath)) {
                return null;
            }

            String jsonData = new String(Files.readAllBytes(filePath));
            CachedData cachedData = gson.fromJson(jsonData, CachedData.class);

            if (cachedData.getExpiryTime() > Instant.now().getEpochSecond()) {
                System.out.println("Dane pobrane z cache pliku: " + fileName);
                return cachedData.getData();
            } else {
                // Cache expired, delete file
                Files.deleteIfExists(filePath);
                System.out.println("Cache wygasł, usuwam plik: " + fileName);
            }

        } catch (IOException e) {
            System.err.println("Błąd odczytu z cache pliku: " + e.getMessage());
        }
        return null;
    }

    private String sanitizeFileName(String key) {
        // Replace characters that are not allowed in file names
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public void clearCache() {
        // Clear Redis cache
        if (redisAvailable) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(jedis.keys("weather:*").toArray(new String[0]));
                System.out.println("Redis cache wyczyszczony");
            } catch (JedisException e) {
                System.err.println("Błąd czyszczenia Redis: " + e.getMessage());
            }
        }

        // Clear file cache
        try {
            Path cacheDir = Paths.get(CACHE_DIR);
            if (Files.exists(cacheDir)) {
                Files.list(cacheDir)
                        .filter(path -> path.toString().endsWith(".cache"))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                System.err.println("Nie można usunąć pliku cache: " + e.getMessage());
                            }
                        });
                System.out.println("Cache plików wyczyszczony");
            }
        } catch (IOException e) {
            System.err.println("Błąd czyszczenia cache plików: " + e.getMessage());
        }
    }

    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    // Inner class for cached data with expiry time
    private static class CachedData {
        private WeatherData data;
        private long expiryTime;

        public CachedData() {}

        public CachedData(WeatherData data, long expiryTime) {
            this.data = data;
            this.expiryTime = expiryTime;
        }

        public WeatherData getData() {
            return data;
        }

        public void setData(WeatherData data) {
            this.data = data;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public void setExpiryTime(long expiryTime) {
            this.expiryTime = expiryTime;
        }
    }
}