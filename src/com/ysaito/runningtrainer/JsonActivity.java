package com.ysaito.runningtrainer;


/**
 * Record of a run log. Corresponds to "newly completed activites" json described in
 * http://developer.runkeeper.com/healthgraph/fitness-activities.
 *
 * This object is converted to a JSON string using GSON.
 *
 */
public class JsonActivity {
    public String type; // "Running", "Cycling", etc.
    public String equipment; // usually "None"
    public String start_time;      // "Sat, 1 Jan 2011 00:00:00"
    public double total_distance;  // total distance, in meters
    public double duration;        // duration, in seconds
    public String notes;
    public JsonWGS84[] path;
    public Boolean post_to_facebook;
    public Boolean post_to_twitter;
    public Boolean detect_pauses;
}