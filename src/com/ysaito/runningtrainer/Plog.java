package com.ysaito.runningtrainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;
import android.util.Log;

public class Plog {
	private static String TAG = "Plog";
	private static boolean ENABLED = false;
	
	private static class LogEntry {
		long timestamp;  // millisecs since 1970/1/1
		String tag;
		String message;
	}
	private static class FlushThread extends Thread {
		private final Buffer mBuffer;
		private final double mFlushIntervalSeconds;
		private FileWriter mFile;
		private final StringBuilder mBuilder = new StringBuilder();
		private final Calendar mCalendar = new GregorianCalendar();

		FlushThread(Buffer buffer, File logPath, double flushInterval) { 
			mBuffer = buffer;
			mFlushIntervalSeconds = flushInterval;
			try {
				mFile = new FileWriter(logPath, true/*append*/);
			} catch (IOException e) {
				Log.e(TAG, logPath.getPath() + ": open failed: "  + e);
			}
		}
		public void run() {
			for (;;) {
				try {
					Thread.sleep((long)(mFlushIntervalSeconds * 1000));
				} catch (Exception e) {
					
				}
				ArrayList<LogEntry> entries = mBuffer.waitAndDrain();
				flush(entries);
			}
		}
		
		private void flush(ArrayList<LogEntry> entries) {
			if (mFile == null) return;
			mBuilder.setLength(0);
			for (LogEntry e : entries) {
				mCalendar.setTimeInMillis(e.timestamp);
				mBuilder.append(String.format("%04d-%02d-%02d:%02d:%02d:%02d",
						mCalendar.get(Calendar.YEAR),
						mCalendar.get(Calendar.MONTH),
						mCalendar.get(Calendar.DAY_OF_MONTH),
						mCalendar.get(Calendar.HOUR_OF_DAY),
						mCalendar.get(Calendar.MINUTE),
						mCalendar.get(Calendar.SECOND)));
				mBuilder.append(" ");
				mBuilder.append(e.tag);
				mBuilder.append(" ");
				mBuilder.append(e.message);
				mBuilder.append("\n");
			}
			try {
				Log.d(TAG, "Write: " + mBuilder.toString());
				mFile.write(mBuilder.toString());
				mFile.flush();
			} catch (IOException e) {
				Log.d(TAG, "Failed to flush log: " + e);
			}
		}
	}
	
	private static class Buffer {
		private ArrayList<LogEntry> mEntries;
		private File mLogFile;
		
		public Buffer(File logDir, double flushInterval) {
			mEntries = new ArrayList<LogEntry>();
			mLogFile = new File(logDir, "log." + android.os.Process.myPid() + ".txt");
			Thread thread = new FlushThread(this, mLogFile, flushInterval);
			thread.start();
		}
		
		public synchronized void add(String tag, String message) {
			LogEntry entry = new LogEntry();
			entry.timestamp = System.currentTimeMillis();
			entry.tag = tag;
			entry.message = message;
			mEntries.add(entry);
			notifyAll();
		}
		
		public synchronized ArrayList<LogEntry> waitAndDrain() {
			while (mEntries.size() == 0) {
				try {
					wait();
				} catch (Exception e) { 
				}
			}
			ArrayList<LogEntry> r = mEntries;
			mEntries = new ArrayList<LogEntry>();
			return r;
		}
		
		public File getLogFile() { return mLogFile; }
	}
	
	private static Buffer mBuffer;
	
	public static void init(Context context) {
		if (ENABLED && mBuffer == null) {
			mBuffer = new Buffer(FileManager.getLogDir(context), 4.0);
			// Read and dump the system log contents. This will catch the stack trace of
			// any previous crash of this process, if any. This is needed, because starting
			// from Jellybean, a log-cat app cannot see other app's logs.
			readSystemLog();
		}
	}

	private static void readSystemLog() {
		try {
			Process process = Runtime.getRuntime().exec("logcat -d");
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				mBuffer.add("LC", line);
			}
			bufferedReader.close();
		} catch (IOException e) {
			Log.e(TAG, "logcat: " + e);
		}
	}
	
	public static File getLogFile() { return mBuffer.getLogFile(); }
	
	public static final void d(String tag, String message) {
		if (ENABLED) {
			mBuffer.add(tag, message);
		}
	}
}
