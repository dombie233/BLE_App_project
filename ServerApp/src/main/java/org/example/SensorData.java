package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SensorData {
    public float humidity;

    public float temperature;

    public SensorData() {
    }
    @Override
    public String toString() {
        return "Sensor Data{" +
                "humidity=" + humidity +
                ", temperature=" + temperature +
                '}';
    }
}
