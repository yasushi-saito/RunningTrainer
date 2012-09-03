package com.ysaito.runningtrainer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RecordManager {
	private final static String TAG = "Util";
	
	// The SDCARD directory in which records are stored
	private final File mRootDir;
	private final Context mContext;
	
	static private Pattern BASENAME_RE = Pattern.compile("log:s=([\\d]+):d=([\\d.]+):e=([\\d.]+)\\.json");
	
	public RecordManager(Context context) {
		File externalDir = context.getExternalFilesDir(null);
		mContext = context;
		if (externalDir == null) {
        	Toast.makeText(context, "SD card is not found on this device. No record will be kept", Toast.LENGTH_LONG).show();
        	mRootDir = null;
		} else {
			mRootDir = new File(externalDir, "com.ysaito.RunningTrainer");
			if (!mRootDir.exists()) {
				if (!mRootDir.mkdirs()) {
					Toast.makeText(context, mRootDir.getPath() + ": failed to create directory", Toast.LENGTH_LONG).show();
				}
			}
		}
	}
	
	static private String generateBasename(long startTime, Record record) {
		return "log:s=" + startTime + ":d=" + record.total_distance + ":e=" + record.duration + ".json";
	}
	
	/**
	 * Parse the basename generated by generateBasename.
	 * 
	 *  @return The struct containing the parsed result, or null if the basename is malformed. 
	 */
	public static class RecordSummary {
		public String basename;
		public long startTime;
		public double totalDistance;
		public double duration;
	}
	static private RecordSummary parseBasename(String basename) {
		Matcher m = BASENAME_RE.matcher(basename);
		if (!m.find()) return null;
		
		try {
			RecordSummary r = new RecordSummary();
			r.basename = basename;
			r.startTime = Long.parseLong(m.group(1));
			r.totalDistance = Double.parseDouble(m.group(2));
			r.duration = Double.parseDouble(m.group(3));
			return r;
		} catch (NumberFormatException e) {
			Log.e(TAG, basename + ": Failed to parse the basenam");
			return null;
		}
	}
	
	public void addRecord(long startTime, Record record) {
		if (mRootDir == null) return;
		Gson gson = new GsonBuilder().create();
		try {
			File destFile = new File(mRootDir, generateBasename(startTime, record));
			FileWriter out = new FileWriter(destFile);
			gson.toJson(record, out);
			out.close();
			Toast.makeText(mContext, "Wrote record to " + destFile.getPath(), Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			Toast.makeText(mContext, mRootDir.getPath() + ": failed to save log: " + e.toString(), Toast.LENGTH_LONG).show();
		}
	}
	
	public ArrayList<RecordSummary> listRecords() {
		ArrayList<RecordSummary> list = new ArrayList<RecordSummary>();
		if (mRootDir != null) {
			for (String basename : mRootDir.list()) {
				if (basename.startsWith("log:")) {
					RecordSummary summary = parseBasename(basename);
					if (summary != null) list.add(summary);
				}
			}
		}
	    return list;
	}
}
