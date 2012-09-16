package com.ysaito.runningtrainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
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
	
	public RecordManager(Context context) {
		// File externalDir = context.getExternalFilesDir(null);
		File externalDir = new File("/sdcard/com.ysaito.runningtrainer");
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

	/**
	 * Escape a string that may contain '/' so that the result can be used as part of a basename.
	 */
	static public String sanitizeString(String s) {
		final StringBuilder b = new StringBuilder();
		final int len = s.length();
		for (int i = 0; i < len; ++i) {
			final char ch = s.charAt(i);
			if (ch == '%') {
				b.append("%%");
			} else if (ch == ':') {
				b.append("%c");
			} else if (ch == '/') {
				b.append("%s");
			} else {
				b.append(ch);
			}
		}
		return b.toString();
	}

	/**
	 * Reverse the transformation done by sanitizeString. Throws ParseException when the string isn't properly encoded.
	 */
	static public String unsanitizeString(String s) throws Exception {
		final StringBuilder b = new StringBuilder();
		final int len = s.length();
		for (int i = 0; i < len; ++i) {
			final char ch = s.charAt(i);
			if (ch != '%') {
				b.append(ch);
			} else {
				if (i == len - 1) throw new Exception("Illegal escaped string " + s);
				++i;
				final char nextCh = s.charAt(i);
				if (nextCh == '%') {
					b.append('%');
				} else if (nextCh == 'c') {
					b.append(':');
				} else if (nextCh == 's') {
					b.append('/');
				} else {
					throw new Exception("Illegal escaped string " + s);
				}
			}
		}
		return b.toString();
	}
	
	/**
	 * Given an activity record, generate the summary object that includes that the filename (basename) that will store the record. 
	 * This function is public only for testing.
	 * 
	 * @param startTimeSeconds The time the activity. Seconds since 1970/1/1. 
	 * @param distance distance in meters
	 * @param mDuration duration in seconds
	 * @param The path the activity is stored in runkeeper. null if it is not yet sent to runkeeper.
	 * 
	 * @return A basename string, in the form "log:<params>.json" 
	 */
	static public String generateBasename(double startTimeSeconds, double distance, double durationSeconds, String runkeeperPath) {
		StringBuilder b = new StringBuilder("log:s=");
		// start time is used as the primary key, so round down to integers to avoid rounding errors
		b.append((long)startTimeSeconds);
		b.append(":d=");
		b.append(distance);
		b.append(":e=");
		b.append(durationSeconds);
		if (runkeeperPath != null) {
			b.append(":r=");
			b.append(sanitizeString(runkeeperPath));
		}
		b.append(":.json");
		return b.toString();
	}

	/**
	 * Parse the basename generated by generateBasename.
	 * This method is public only for testing.
	 * 
	 *  @return The struct containing the parsed result, or null if the basename is malformed. 
	 */
	static final Pattern P0 = Pattern.compile("log");
	static final Pattern P1 = Pattern.compile("([a-zA-Z+])=([^:]+)");
	
	static public RecordSummary parseBasename(String basename) {
		try {
			RecordSummary r = new RecordSummary();
			Scanner scanner = new Scanner(basename).useDelimiter(":");
			scanner.skip(P0);  // will raise exception on mismatch
			r.basename = basename; 
			while (scanner.hasNext(P1)) {
				final String type = scanner.match().group(1);
				final String value = scanner.match().group(2);
				scanner.next(P1);
				if (type.equals("s")) {
					r.startTimeSeconds = Double.parseDouble(value);
				} else if (type.equals("d")) {
					r.distance = Double.parseDouble(value);
				} else if (type.equals("e")) {
					r.durationSeconds = Double.parseDouble(value);
				} else if (type.equals("r")) {
					r.runkeeperPath = unsanitizeString(value);
				}
			}
			String s = scanner.next();
			if (!s.equals(".json")) {
				return null;
			}
			Log.d(TAG, "REC: "+ r.startTimeSeconds + "/" + basename);
			return r;
		} catch (Exception e) {
			Log.e(TAG, basename + ": Failed to parse the basenam");
			return null;
		}
	}
	
	public void addRecord(double startTimeSeconds, HealthGraphClient.JsonActivity record) {
		if (mRootDir == null) return;
		Gson gson = new GsonBuilder().create();
		try {
			File destFile = new File(mRootDir, generateBasename(startTimeSeconds, record.total_distance, record.duration, null));
			FileWriter out = new FileWriter(destFile);
			gson.toJson(record, out);
			out.close();
			Toast.makeText(mContext, "Wrote record to " + destFile.getPath(), Toast.LENGTH_LONG).show();
		} catch (IOException e) {
			Toast.makeText(mContext, mRootDir.getPath() + ": failed to save log: " + e.toString(), Toast.LENGTH_LONG).show();
		}
	}
	
	public HealthGraphClient.JsonActivity readRecord(String basename) {
		File sourceFile = new File(mRootDir, basename);
		try {
			Gson gson = new GsonBuilder().create();
			return gson.fromJson(new BufferedReader(new FileReader(sourceFile)), HealthGraphClient.JsonActivity.class);
		} catch (IOException e) {
			Log.d(TAG, sourceFile.getPath() + ": " + e.toString());
		}
		return null;
	}
	
	public void markAsSaved(double startTimeSeconds, String runkeeperPath) {
		ArrayList<RecordSummary> list = listRecords();
		for (RecordSummary r : list) {
			if ((long)r.startTimeSeconds == (long)startTimeSeconds) {
				String newBasename = generateBasename(r.startTimeSeconds, r.distance, r.durationSeconds, runkeeperPath);
				File orgFile = new File(mRootDir, r.basename);
				orgFile.renameTo(new File(mRootDir, newBasename));
			}
		}
	}
	
	public void deleteRecord(RecordSummary summary) {
		new File(mRootDir, summary.basename).delete(); // TODO: handle errors
	}
	
	public ArrayList<RecordSummary> listRecords() {
		ArrayList<RecordSummary> list = new ArrayList<RecordSummary>();
		Log.d(TAG, "LIST:");
		if (mRootDir != null) {
			Log.d(TAG, "LIST2:");
			for (String basename : mRootDir.list()) {
				Log.d(TAG, "File: " + basename);
				if (basename.startsWith("log:")) {
					RecordSummary summary = parseBasename(basename);
					if (summary != null) list.add(summary);
				}
			}
		}
	    return list;
	}
}
