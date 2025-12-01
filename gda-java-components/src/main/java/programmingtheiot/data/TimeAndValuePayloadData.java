/**
 * This class is part of the Programming the Internet of Things
 * project, and is available via the MIT License, which can be
 * found in the LICENSE file at the top level of this repository.
 * 
 * Copyright (c) 2020 - 2025 by Andrew D. King
 */

package programmingtheiot.data;

import java.io.Serializable;

/**
 * Simplified data container for cloud services that only need timestamp and
 * value.
 * Used by cloud services where device and variable context is embedded in the
 * topic.
 */
public class TimeAndValuePayloadData implements Serializable {
    // static

    private static final long serialVersionUID = 1L;

    // private var's

    private long timeStamp = 0L;
    private float value = 0.0f;
    private String name = "TimeAndValuePayloadData";

    // constructors

    /**
     * Default constructor.
     */
    public TimeAndValuePayloadData() {
        super();
    }

    /**
     * Constructor that extracts timestamp and value from ActuatorData.
     * 
     * @param data The ActuatorData instance
     */
    public TimeAndValuePayloadData(ActuatorData data) {
        super();

        if (data != null) {
            this.timeStamp = System.currentTimeMillis();
            this.value = data.getValue();
            this.name = data.getName();
        }
    }

    /**
     * Constructor that extracts timestamp and value from SensorData.
     * 
     * @param data The SensorData instance
     */
    public TimeAndValuePayloadData(SensorData data) {
        super();

        if (data != null) {
            this.timeStamp = System.currentTimeMillis();
            this.value = data.getValue();
            this.name = data.getName();
        }
    }

    /**
     * Constructor that extracts timestamp and value from SystemPerformanceData.
     * 
     * @param data The SystemPerformanceData instance
     */
    public TimeAndValuePayloadData(SystemPerformanceData data) {
        super();

        if (data != null) {
            this.timeStamp = System.currentTimeMillis();
            this.value = data.getCpuUtilization(); // Default to CPU utilization
            this.name = data.getName();
        }
    }

    // public methods

    /**
     * Gets the name.
     * 
     * @return String The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the timestamp.
     * 
     * @return long The timestamp in milliseconds
     */
    public long getTimeStamp() {
        return this.timeStamp;
    }

    /**
     * Gets the value.
     * 
     * @return float The value
     */
    public float getValue() {
        return this.value;
    }

    /**
     * Sets the name.
     * 
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the timestamp.
     * 
     * @param timeStamp The timestamp in milliseconds
     */
    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    /**
     * Sets the value.
     * 
     * @param value The value
     */
    public void setValue(float value) {
        this.value = value;
    }

    /**
     * Returns a string representation of this data.
     * 
     * @return String The string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("TimeAndValuePayloadData [");
        sb.append("name=").append(this.name);
        sb.append(", timeStamp=").append(this.timeStamp);
        sb.append(", value=").append(this.value);
        sb.append("]");

        return sb.toString();
    }
}