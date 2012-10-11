package com.ysaito.runningtrainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FileManager {
	private final static String TAG = "FileManager";

	//
	// Keys for HealthGraphClient.JsonActivity
	//
	
	// The start time of the activity. type: long, value: seconds since 1970/1/1
	public static final String KEY_START_TIME = "s";
	
	// Total distance. type: long, value: meters 
	public static final String KEY_DISTANCE = "d";

	// Total duration. type: long, value: seconds
	public static final String KEY_DURATION = "e";
	
	// Path under http://runkeeper.com this activity is saved. type: string
	public static final String KEY_RUNKEEPER_PATH = "r";

	//
	// Keys for Workout
	//
	
	// A unique ID for the workout. type: long, value: the time the workout was created, seconds since 1970/1/1
	public static final String KEY_WORKOUT_ID = "i";
	
	// The name of the workout. type: string 
	public static final String KEY_WORKOUT_NAME = "n";	
	
	private static File getDirUnderRoot(Context context, String subdir) {
		File externalDir = new File(Environment.getExternalStorageDirectory(), "com.ysaito.runningtrainer");
		File dir = new File(externalDir, subdir);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				Util.error(context, dir.getPath() + ": failed to create directory");
				return null;
			}
		}
		return dir;
	}
	
	static private Util.Singleton<File> mWorkoutDir = new Util.Singleton<File>();
	static private Util.Singleton<File> mRecordDir = new Util.Singleton<File>();	
	static private Util.Singleton<File> mLogDir = new Util.Singleton<File>();

	public static File getLogDir(final Context context) {
		return mLogDir.get(new Util.SingletonInitializer<File>() {
			public File createSingleton() { return getDirUnderRoot(context, "log"); }
		});
	}
	
	public static File getWorkoutDir(final Context context) {
		return mWorkoutDir.get(new Util.SingletonInitializer<File>() {
			public File createSingleton() { return getDirUnderRoot(context, "workouts"); }
		});
	}
	
	public static File getRecordDir(final Context context) {
		return mRecordDir.get(new Util.SingletonInitializer<File>() {
			public File createSingleton() { return getDirUnderRoot(context, "activities"); }
		});
	}
	
	/**
	 * Escape a string that may contain '/' so that the result can be used as part of a basename.
	 * Exposed only for unittesting purpose
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
			} else if (ch == ',') {
				b.append("%d");
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
				} else if (nextCh == 'd') {
					b.append(',');
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
	 * Files created by runningtrainer encodes <key,value> mappings in the filenames. Each filename has format
	 * 
	 *   log:<key>=<value>:...:<key>=<value>:.json
	 *
	 * ParsedFilename is a helper class for generating such filenames and parsing themback.
	 */
	public static class ParsedFilename {
		private final TreeMap<String, String> mKeys = new TreeMap<String, String>();
		
		public ParsedFilename() { }
		
		public final void putLong(String key, long value) {
			mKeys.put(key, Long.toString(value));
		}

		public final void putString(String key, String value) {
			mKeys.put(key, value);
		}

		public final long getLong(String key, long defaultValue) {
			String value = mKeys.get(key);
			try {
				if (value != null) return Long.parseLong(value);
			} catch (NumberFormatException e) {
				Log.d(TAG, "Unparsable key (as long: " + key + " in " + getBasename());
			}
			return defaultValue;
		}

		public final String getString(String key, String defaultValue) {
			String value = mKeys.get(key);
			if (value != null) return value;
			return defaultValue;
		}
		
		public final String getBasename() {
			StringBuilder b = new StringBuilder("log");
			for (Map.Entry<String, String> entry : mKeys.entrySet()) {
				String key = entry.getKey();
				Object value = entry.getValue();
				b.append(",");
				b.append(key);
				b.append("=");
				b.append(value);
			}
			b.append(",.json");
			return b.toString();
		}

		private static final Pattern P0 = Pattern.compile("log");
		private static final Pattern P1 = Pattern.compile("([a-zA-Z]+)=([^,]+)");
		
		/**
		 * Parse the basename generated by generateBasename.
		 * This method is public only for testing.
		 * 
		 *  @return The struct containing the parsed result, or null if the basename is malformed. 
		 */
		static public ParsedFilename parse(String basename) {
			try {
				ParsedFilename f = new ParsedFilename();
				Scanner scanner = new Scanner(basename).useDelimiter(",");
				scanner.skip(P0);  // will raise exception on mismatch
				
				while (scanner.hasNext(P1)) {
					final String type = scanner.match().group(1);
					final String value = scanner.match().group(2);
					scanner.next(P1);
					f.putString(type, value);
				}
				String s = scanner.next();
				if (!s.equals(".json")) {
					return null;
				}
				return f;
			} catch (Exception e) {
				Log.e(TAG, basename + ": Failed to parse filename");
				return null;
			}
		}
	}
	
	public interface AsyncRunner<T> {
		public T doInThread() throws Exception;
		public void onFinish(Exception error, T value);
	}

	private static class RunAsyncResult<T> {
		public T object = null;
	}
	
	public static <T> void runAsync(final AsyncRunner<T> listener) {
		final RunAsyncResult<T> result = new RunAsyncResult<T>();
		
		AsyncTask<Void, Void, Exception> task = new AsyncTask<Void, Void, Exception>() {
			@Override protected Exception doInBackground(Void... params) {
				try {
					result.object = listener.doInThread();
					return null;
				} catch (Exception e) {
					return e;
				}
			}
			@Override protected void onPostExecute(Exception error) {
				listener.onFinish(error, result.object);
			}
		};
		task.execute((Void[])null);
	}

	private static final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();

	public static void writeJson(File dir, String basename, Object object) throws Exception {
		mLock.writeLock().lock();
		try {
			Gson gson = new GsonBuilder().create();
			File destFile = new File(dir, basename);
			FileWriter out = new FileWriter(destFile);
			gson.toJson(object, out);
			out.close();
		} finally {
			mLock.writeLock().unlock();
		}
	}
	
	public static <T> T readJson(File dir, String basename, Class<T> classObject) throws Exception {
		mLock.readLock().lock();
		try {
			File sourceFile = new File(dir, basename);
			Gson gson = new GsonBuilder().create();
			return gson.fromJson(new BufferedReader(new FileReader(sourceFile)), classObject);
		} finally {
			mLock.readLock().unlock();
		}
	}

	public static void deleteFile(File dir, String basename) throws Exception {
		mLock.writeLock().lock();
		try {
			if (!new File(dir, basename).delete()) {
				throw new Exception("Failed to delete: " + basename);
			}
		} finally {
			mLock.writeLock().unlock();
		}
	}
	
	public static ArrayList<ParsedFilename> listFiles(File dir) {
		mLock.readLock().lock();
		try {
			ArrayList<ParsedFilename> list = new ArrayList<ParsedFilename>();
			for (String basename : dir.list()) {
				if (basename.startsWith("log,")) {
					ParsedFilename summary = ParsedFilename.parse(basename);
					if (summary != null) list.add(summary);
				}
			}
			return list;
		} finally {
			mLock.readLock().unlock();
		}
	}
}
