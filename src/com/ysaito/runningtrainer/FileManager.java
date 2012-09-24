package com.ysaito.runningtrainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Pattern;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FileManager {
	private final static String TAG = "FileManager";

	// Keys for HealthGraphClient.JsonActivity
	public static final String KEY_START_TIME = "s";
	public static final String KEY_DISTANCE = "d";
	public static final String KEY_DURATION = "e";
	public static final String KEY_RUNKEEPER_PATH = "r";

	// Keys for Workout
	public static final String KEY_WORKOUT_ID = "i";
	public static final String KEY_WORKOUT_NAME = "n";	
	
	private static File getDirUnderRoot(Context context, String subdir) {
		File externalDir = new File("/sdcard/com.ysaito.runningtrainer");
		if (externalDir == null) {
        	Toast.makeText(context, "SD card is not found on this device. No record will be kept", Toast.LENGTH_LONG).show();
        	return null;
		}
		File dir = new File(externalDir, subdir);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				Toast.makeText(context, dir.getPath() + ": failed to create directory", Toast.LENGTH_LONG).show();
				return null;
			}
		}
		return dir;
	}
	
	static private Util.Singleton<File> mWorkoutDir = new Util.Singleton<File>();
	static private Util.Singleton<File> mRecordDir = new Util.Singleton<File>();	
	
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

	public static class ParsedFilename {
		public final TreeMap<String, String> mKeys = new TreeMap<String, String>();
		public ParsedFilename() { }
		
		public void putLong(String key, long value) {
			mKeys.put(key, Long.toString(value));
		}

		public void putString(String key, String value) {
			mKeys.put(key, value);
		}

		public long getLong(String key, long defaultValue) {
			String value = mKeys.get(key);
			try {
				if (value != null) return Long.parseLong(value);
			} catch (NumberFormatException e) {
				Log.d(TAG, "Unparsable key (as long: " + key + " in " + getBasename());
			}
			return defaultValue;
		}

		public String getString(String key, String defaultValue) {
			String value = mKeys.get(key);
			if (value != null) return value;
			return defaultValue;
		}
		
		public String getBasename() {
			return generateBasename(this);
		}
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
	static public String generateBasename(ParsedFilename f) {
		StringBuilder b = new StringBuilder("log");
		for (Map.Entry<String, String> entry : f.mKeys.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			b.append(":");
			b.append(key);
			b.append("=");
			b.append(value);
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
	static final Pattern P1 = Pattern.compile("([a-zA-Z]+)=([^:]+)");
	
	static public ParsedFilename parseBasename(String basename) {
		try {
			ParsedFilename f = new ParsedFilename();
			Scanner scanner = new Scanner(basename).useDelimiter(":");
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
			Log.e(TAG, basename + ": Failed to parse the basenam");
			return null;
		}
	}

	public static void writeFileAsync(File dir, String basename, Object jsonObject, ResultListener listener) {
		WriteFileThread thread = new WriteFileThread(dir, basename, jsonObject, listener);
		thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
	}
	
	public interface ReadListener<T> {
		public void onFinish(Exception e, T obj);
	}
	
	public static <T> void readFileAsync(File dir, String basename, Class<T> classObject, ReadListener<T> listener) {
		ReadFileThread<T> thread = new ReadFileThread<T>(dir, basename, classObject, listener);
		thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
	}
	
	public interface ResultListener {
		void onFinish(Exception e);
	}

	public interface ListFilesListener {
		/**
		 * Called when listFilesAsync finishes. Exactly one of @p e or @p files is non-null.
		 */
		void onFinish(Exception e, ArrayList<ParsedFilename> files);
	}

	/**
	 * List and parse filenames under @p dir in the background. Run @p listener when done.
	 */
	public static void listFilesAsync(File dir, ListFilesListener listener) {
		ListThread thread = new ListThread(dir, listener);
		thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
	}
	
	/**
	 * Delete files @p basenames in @p dir in the background. Call @p listener on completion
	 */
	public static void deleteFilesAsync(File dir, String[] basenames, ResultListener listener) {
		DeleteFilesThread thread = new DeleteFilesThread(dir, basenames, listener);
		thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
	}
	
	private static class WriteFileThread extends AsyncTask<Void, Void, Void> {
		private final File mDir;
		private final String mBasename;
		private final Object mJsonObject;
		private final ResultListener mListener;
		private Exception mException = null;
		
		public WriteFileThread(
				File dir,
				String basename,
				Object jsonObject,
				ResultListener listener) { 
			mDir = dir; 
			mBasename = basename;
			mJsonObject = jsonObject;
			mListener = listener; 
		}
		
		@Override protected Void doInBackground(Void... unused) {
			try {
				Gson gson = new GsonBuilder().create();
				File destFile = new File(mDir, mBasename);
				FileWriter out = new FileWriter(destFile);
				gson.toJson(mJsonObject, out);
				out.close();
			} catch (Exception e) {
				mException = e;
			}
			return null;
		}
		
		@Override protected void onPostExecute(Void unused) {
			mListener.onFinish(mException);
		}
	}

	private static class ReadFileThread<T> extends AsyncTask<Void, Void, T> {
		private final File mDir;
		private final String mBasename;
		private final Class<T> mClassObject;
		private final ReadListener<T> mListener;
		private Exception mException = null;
		
		public ReadFileThread(File dir, String basename, Class<T> classObject, ReadListener<T> listener) {
			mDir = dir;
			mBasename = basename;
			mClassObject = classObject;
			mListener = listener;
		}
		
		@Override protected T doInBackground(Void... unused) {
			try {
				File sourceFile = new File(mDir, mBasename);
				Gson gson = new GsonBuilder().create();
				return gson.fromJson(new BufferedReader(new FileReader(sourceFile)), mClassObject);
			} catch (Exception e) {
				mException = e;
				return null;
			}
		}
		
		@Override protected void onPostExecute(T record) {
			mListener.onFinish(mException, record);
		}
	}
	
	private static class DeleteFilesThread extends AsyncTask<Void, Void, Void> {
		private final File mDir;
		private final String[] mBasenames;
		private final ResultListener mListener;
		private Exception mException = null;
		
		public DeleteFilesThread(
				File dir,
				String[] basenames,
				ResultListener listener) { 
			mDir = dir; 
			mBasenames = basenames;
			mListener = listener; 
		}
		
		@Override protected Void doInBackground(Void... unused) {
			for (String basename : mBasenames) {
				deleteFile(mDir, basename);
			}
			return null;
		}
		
		@Override protected void onPostExecute(Void unused) {
			mListener.onFinish(mException);
		}
	}

	private static void deleteFile(File dir, String basename) {
		Log.d(TAG, "Delete: " + basename);
		new File(dir, basename).delete(); // TODO: handle errors
	}

	private static class ListThread extends AsyncTask<Void, Void, ArrayList<ParsedFilename>> {
		private final File mDir;
		private final ListFilesListener mListener;
		private Exception mException = null;
		ListThread(File dir, ListFilesListener listener) { mDir = dir; mListener = listener; }
		
		@Override
		protected ArrayList<ParsedFilename> doInBackground(Void... unused) {
			try {
				return listFiles(mDir);
			} catch (Exception e) {
				mException = e;
				return null;
			}
		}

		@Override
		protected void onPostExecute(ArrayList<ParsedFilename> records) {
			mListener.onFinish(mException, records);
		}
	}

	public static ArrayList<ParsedFilename> listFiles(File dir) {
		ArrayList<ParsedFilename> list = new ArrayList<ParsedFilename>();
		for (String basename : dir.list()) {
			if (basename.startsWith("log:")) {
				ParsedFilename summary = parseBasename(basename);
				if (summary != null) list.add(summary);
			}
		}
	    return list;
	}
	
	
}
