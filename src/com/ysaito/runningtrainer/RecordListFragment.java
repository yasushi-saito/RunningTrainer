package com.ysaito.runningtrainer;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Stack;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.ysaito.runningtrainer.FileManager.ParsedFilename;

import android.app.ActionBar.LayoutParams;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Html;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A fragment that displays list of completed activities. Clicking on the activity name will display
 * a RecordReplayFragment, which contains a MapView and lap stats.
 */
public class RecordListFragment extends ListFragment {
	private static final String TAG = "RecordListFragment";
	private File mRecordDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;

	private class RecordSummaryDialog extends DialogFragment {
		private final JsonActivity mRecord;
		private final float SCREEN_DENSITY = mActivity.getResources().getDisplayMetrics().scaledDensity;
		private final float TEXT_SIZE = 10 * SCREEN_DENSITY;
	
		
		public RecordSummaryDialog(JsonActivity record) {
			mRecord = record;
		}
		
		private final TableRow addRow(String attr, String value) {
			TableRow tr = new TableRow(mActivity);
			tr.setLayoutParams(new LayoutParams(
					LayoutParams.FILL_PARENT,
					LayoutParams.WRAP_CONTENT));
			
			TextView attrView = new TextView(mActivity);
			attrView.setText(Html.fromHtml("<b>" + attr + " </b>"));
			attrView.setTextSize(TEXT_SIZE);
			tr.addView(attrView);
			
			TextView valueView = new TextView(mActivity);
			valueView.setText(value);
			valueView.setTextSize(TEXT_SIZE);
			tr.addView(valueView);
			return tr;
		}
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			TableLayout view = new TableLayout(mActivity);
			view.addView(addRow("foo", "bar"));
			view.addView(addRow("foo2", "bar2"));
			
			// Build the dialog and set up the button click handlers
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage("Activity Summary")
			.setView(view)
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					;
				}
			});
			return builder.create();
		}
	}
	
	private class SigninDialog extends DialogFragment {
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			// Build the dialog and set up the button click handlers
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setMessage("You are not signed into RunKeeper.")
			.setPositiveButton(R.string.sign_in, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					HealthGraphClient.getSingleton().startAuthentication(mActivity);
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
				}
			});
			return builder.create();
		}
	}
	
	private class UndoEntry {
		public void add(String filename, JsonActivity r) { records.add(r); filenames.add(filename); }
		public final ArrayList<String> filenames = new ArrayList<String>();
		public final ArrayList<JsonActivity> records = new ArrayList<JsonActivity>();
	}
	private Stack<UndoEntry> mUndos = new Stack<UndoEntry>();
	
	private class MyAdapter extends BaseAdapter {
    	private final LayoutInflater mInflater;
    	private final ArrayList<FileManager.ParsedFilename> mRecords = new ArrayList<FileManager.ParsedFilename>();
    	private final HashSet<FileManager.ParsedFilename> mFilesSending = new HashSet<FileManager.ParsedFilename>();
    	private Comparator<FileManager.ParsedFilename> mComparator = COMPARE_BY_START_TIME;
    	
    	public MyAdapter(Context context) { 
    		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    	}

		public void reset(ArrayList<FileManager.ParsedFilename> newRecords) {
			mRecords.clear();
			mRecords.addAll(newRecords);
    		Collections.sort(mRecords, mComparator);
			notifyDataSetChanged();
		}
		
		public void setComparator(Comparator<FileManager.ParsedFilename> comparator) { 
			mComparator = comparator; 
    		Collections.sort(mRecords, comparator);
			notifyDataSetChanged();
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
	   		View layout;
    		if (convertView == null) {
    			layout = (View)mInflater.inflate(R.layout.record_list_row, parent, false);
    		} else {
    			layout = (View)convertView;
    		}
			final TextView textView = (TextView)layout.findViewById(R.id.record_list_row_text);
			final ImageView deleteView = (ImageView)layout.findViewById(R.id.record_list_row_delete);
			final ImageView syncView = (ImageView)layout.findViewById(R.id.record_list_row_send);
			final FileManager.ParsedFilename f = (FileManager.ParsedFilename)getItem(position);
			
			StringBuilder b = new StringBuilder();
			b.append(Util.dateToString(f.getLong(FileManager.KEY_START_TIME, 0)));
			b.append(" (");
			b.append(Util.durationToString(f.getLong(FileManager.KEY_DURATION, 0)));
			b.append(")<br><b>");
			b.append(Util.distanceToString(f.getLong(FileManager.KEY_DISTANCE, 0), Util.DistanceUnitType.KM_OR_MILE));
			b.append("</b> ");
			b.append(Util.distanceUnitString());
			
			textView.setText(Html.fromHtml(b.toString()));
			final boolean synced = (f.getString(FileManager.KEY_RUNKEEPER_PATH, null) != null);
			final boolean sending = mFilesSending.contains(f);

			syncView.clearColorFilter();
			if (sending) {
				syncView.setColorFilter(0xffff8000);
				textView.setTextColor(0xffff8000);
			} else if (!synced) {
				syncView.setColorFilter(0xff00ff00);
				textView.setTextColor(0xff00ff00);
			} else {
				syncView.setColorFilter(0xffa0a0a0);
				textView.setTextColor(0xffa0a0a0);
			}
			
			deleteView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					deleteRecordAtPosition(position);
				}
			});
			syncView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					sendRecordAtPosition(position);
				}
			});
			return layout;
		}

		public int getCount() {
			return mRecords.size();
		}
		
		public Object getItem(int position) {
			return getParsedFilename(position);
		}

		public final FileManager.ParsedFilename getParsedFilename(int position) {
			if (position < 0 || position >= mRecords.size()) return null;
			return mRecords.get(position);
		}
		
		public final void markAsSending(ParsedFilename f) {
			mFilesSending.add(f);
			mAdapter.notifyDataSetChanged();
		}
		
		public final void unmarkAsSending(ParsedFilename f) {
			mFilesSending.remove(f);
			mAdapter.notifyDataSetChanged();
		}
		
		public long getItemId(int position) {
			return position;
		}
	};

	@Override public void onDestroy() { super.onDestroy(); Plog.d(TAG, "onDestroy"); }
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Plog.d(TAG, "onCreate");
		setHasOptionsMenu(true);
	}
	
    @Override
    public View onCreateView(
    		LayoutInflater inflater, 
    		ViewGroup container,
            Bundle savedInstanceState) {
    	Plog.d(TAG, "onCreateView");
    	return super.onCreateView(inflater,  container, savedInstanceState);
    }
    
	@Override
    public void onCreateOptionsMenu (Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.record_list_options_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
	
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mActivity = (MainActivity)getActivity();
		mRecordDir = FileManager.getRecordDir(mActivity);
		mAdapter = new MyAdapter(mActivity);
		setListAdapter(mAdapter);
		startListing();
		registerForContextMenu(getListView());
		HealthGraphClient.getSingleton().init(mActivity);
	}
	
	// This listener can't be an anonymous instance. SharedPreferences listener is 
	// stored only as a weakref.
	Settings.OnChangeListener mSettingsListener = new Settings.OnChangeListener() {
		public void onChange() { 
			startListing();
		}
	};
	
	@Override public void onResume() {
		super.onResume();
		Settings.registerOnChangeListener(mSettingsListener);
		startListing();
	}

	@Override public void onPause() {
		super.onPause();
		Settings.UnregisterOnChangeListener(mSettingsListener);
	}
	
	@Override public void onStart() {
		super.onStart();
		startListing();
	}

	private int numQueuedListingRequests = 0;
	private Executor LISTING_THREAD_POOL = Executors.newFixedThreadPool(1);
	
	private void startListing() {
		if (numQueuedListingRequests > 1) return;
		mActivity.startActionBarThrobber("Loading");
		++numQueuedListingRequests;
		
		FileManager.runAsync(new FileManager.AsyncRunner<ArrayList<ParsedFilename>>() {
			public ArrayList<ParsedFilename> doInThread() throws Exception {
				return FileManager.listFiles(mRecordDir);
			}
			public void onFinish(Exception error, ArrayList<ParsedFilename> files) {
				mActivity.stopActionBarThrobber();
				if (files != null) {
					mAdapter.reset(files);
				}
				--numQueuedListingRequests;
			}
		}, LISTING_THREAD_POOL);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		startReplayActivity(position);
	}
	
	private final void startReplayActivity(int position) {
		final FileManager.ParsedFilename f = (FileManager.ParsedFilename)mAdapter.getItem(position);
		if (f == null) return;

		mActivity.startActionBarThrobber("Loading");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readJson(new File(mRecordDir, f.getBasename()), JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
				if (error != null) {
					Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + error);
					return;
				}
				mActivity.stopActionBarThrobber();
				RecordReplayFragment fragment = (RecordReplayFragment)mActivity.findOrCreateFragment(
						"com.ysaito.runningtrainer.RecordReplayFragment");
				fragment.setRecord(record);
				mActivity.setFragmentForTab("Log", fragment);
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
	
	@Override
	public void onCreateContextMenu(
			ContextMenu menu,
			View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		final MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.record_list_context_menu, menu);
	}

	private void markAsSaved(final FileManager.ParsedFilename f, final String runkeeperPath) {
		// List the filenames again, just in case some attributes have changed
		FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
			public Void doInThread() throws Exception {
				ArrayList<FileManager.ParsedFilename> list = FileManager.listFiles(mRecordDir);
				for (FileManager.ParsedFilename r : list) {
					if (r.getLong(FileManager.KEY_START_TIME, -1) == f.getLong(FileManager.KEY_START_TIME, -2)) {
						f.putString(FileManager.KEY_RUNKEEPER_PATH, FileManager.sanitizeString(runkeeperPath));
						File orgFile = new File(mRecordDir, r.getBasename());
						orgFile.renameTo(new File(mRecordDir, f.getBasename()));
					}
				}
				return null;
			}
			
			public void onFinish(Exception error, Void unused) {
				if (error != null) {
					Util.error(getActivity(), "Failed to rename: " + error);
				}
				startListing(); 
			}
		}, Util.DEFAULT_THREAD_POOL);
	}

	private static final Comparator<FileManager.ParsedFilename> COMPARE_BY_START_TIME = new Comparator<FileManager.ParsedFilename>() {
		public int compare(FileManager.ParsedFilename f1, FileManager.ParsedFilename f2) {
			final long date1 = f1.getLong(FileManager.KEY_START_TIME, -1);
			final long date2 = f2.getLong(FileManager.KEY_START_TIME, -1);
			if (date1 == date2) return 0;
			if (date1 > date2) return -1;
			return 1;
		}
	};

	private static final Comparator<FileManager.ParsedFilename> COMPARE_BY_DISTANCE = new Comparator<FileManager.ParsedFilename>() {
		public int compare(FileManager.ParsedFilename f1, FileManager.ParsedFilename f2) {
			final long distance1 = f1.getLong(FileManager.KEY_DISTANCE, -1);
			final long distance2 = f2.getLong(FileManager.KEY_DISTANCE, -1);
			if (distance1 == distance2) return 0;
			if (distance1 > distance2) return -1;
			return 1;
		}
	};

	// See if we have already downloaded the same uri.
	private boolean hasDownloadedActivity(String uri) {
		final String sanitized = FileManager.sanitizeString(uri);
		final int N = mAdapter.getCount();
		for (int i = 0; i < N; ++i) {
			if (sanitized.equals(mAdapter.getParsedFilename(i).getString(FileManager.KEY_RUNKEEPER_PATH, null))) return true;
		}
		return false;
	}

	private Executor downloadThreadPool = null;
	private int mNumActivitiesDownloading = 0;
	private long mLastListingUpdate = 0;

	private void showSigninDialog() {
		DialogFragment dialog = new SigninDialog();
		dialog.show(mActivity.getFragmentManager(), "signin");
	}
	
	private void startDownloadFromRunKeeper() {
		final int MAX_ACTIVITIES = 500;
		if (!HealthGraphClient.getSingleton().isSignedIn()) {
			showSigninDialog();
			return;
		}
		if (downloadThreadPool == null) downloadThreadPool = Executors.newFixedThreadPool(8);
		HealthGraphClient.getSingleton().getFitnessActivities(new HealthGraphClient.GetResponseListener() {
			public void onFinish(Exception e, Object o) {
				if (e != null) {
					Util.error(mActivity, "Failed to list activities: " + e);
					return;
				}
				JsonFitnessActivities activities = (JsonFitnessActivities)o;
				for (JsonActivity activity : activities.items) {
					if (!hasDownloadedActivity(activity.uri)) {
						startDownloadActivityFromRunKeeper(activity);
						++mNumActivitiesDownloading;
						if (mNumActivitiesDownloading >= MAX_ACTIVITIES) break;
					}
				}
				String message;
				if (mNumActivitiesDownloading > 0) {
					message = "Start downloading " + mNumActivitiesDownloading + " activities";
				} else {
					message = "Found no activity to download";
				}
				Toast.makeText(mActivity, message, Toast.LENGTH_LONG).show();
				
			}
		}, downloadThreadPool);
	}
	
	// "Sat, 5 Mar 2011 11:00:00"
	private static final SimpleDateFormat DATE_PARSER = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss");
    			
	private void startDownloadActivityFromRunKeeper(final JsonActivity activity) {
		HealthGraphClient.getSingleton().getFitnessActivityAsString(activity.uri, new HealthGraphClient.GetResponseListener() {
			public void onFinish(Exception e, Object o) {
				if (e != null) {
					Util.error(mActivity, "Failed to get activity: " + e);
					return;
				}
				final String jsonString = (String)o;
    			final FileManager.ParsedFilename summary = new FileManager.ParsedFilename();
    			
				try {
					Date date = DATE_PARSER.parse(activity.start_time);
					summary.putLong(FileManager.KEY_START_TIME, date.getTime() / 1000);
				} catch (ParseException error) {
					summary.putLong(FileManager.KEY_START_TIME, 0);
				}
    			summary.putLong(FileManager.KEY_DISTANCE, (long)activity.total_distance);
    			summary.putLong(FileManager.KEY_DURATION, (long)activity.duration);
    			summary.putString(FileManager.KEY_RUNKEEPER_PATH, FileManager.sanitizeString(activity.uri));
    		
    			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
    				public Void doInThread() throws Exception {
    					final FileWriter out = new FileWriter(new File(mRecordDir, summary.getBasename()));
    					out.write(jsonString);
    					out.close();
    					return null;
    				}
    				public void onFinish(Exception error, Void value) {
    					if (error != null) Util.error(mActivity, "Failed to write: " + summary.getBasename() + ": " + error);
    					
    					// Update the listing every 10 seconds, or when all the activities have been downloaded.
    					--mNumActivitiesDownloading;
    					if (mNumActivitiesDownloading == 0) {
    						Toast.makeText(mActivity, "Finished downloading", Toast.LENGTH_LONG).show();
    						startListing();
    					} else {
    						final long now = System.currentTimeMillis();
    						if (now - mLastListingUpdate >= 3 * 1000) {
    							mLastListingUpdate = now;
    							startListing();
    						}
    					}
    				}
    			}, Util.DEFAULT_THREAD_POOL);
			}
		}, downloadThreadPool);
		
	}
	
	@Override
	public void onPrepareOptionsMenu (Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.record_list_undo).setEnabled(!mUndos.empty());
	}	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.record_list_sort_by_date:
			mAdapter.setComparator(COMPARE_BY_START_TIME);
			break;
		case R.id.record_list_sort_by_distance:
			mAdapter.setComparator(COMPARE_BY_DISTANCE);
			break;
		case R.id.record_list_download_from_runkeeper:
			startDownloadFromRunKeeper();
			break;
		case R.id.record_list_delete_all: {
			// Extract the filenames so that they can be read from a different thread
			final FileManager.ParsedFilename[] filenames = new FileManager.ParsedFilename[mAdapter.getCount()];
			for (int i = 0; i < mAdapter.getCount(); ++i) {
				filenames[i] = (FileManager.ParsedFilename)mAdapter.getItem(i);
			}
			mActivity.startActionBarThrobber("Deleting");
			FileManager.runAsync(new FileManager.AsyncRunner<UndoEntry>() {
				public UndoEntry doInThread() throws Exception {
					UndoEntry newUndos = new UndoEntry();
					for (FileManager.ParsedFilename f : filenames) {
						String basename = f.getBasename();
						try {
							JsonActivity record = FileManager.readJson(new File(mRecordDir, f.getBasename()), JsonActivity.class);
							newUndos.add(basename, record);
						} catch (Exception e) {
							
						}
						FileManager.deleteFile(new File(mRecordDir, basename));
					}
					return newUndos;
				}
				public void onFinish(Exception error, UndoEntry newUndos) {
					mActivity.stopActionBarThrobber();
					if (error != null) {
						Util.error(mActivity, "Failed to delete files: " + error);
					} else {		
						mUndos.add(newUndos);
						startListing();
					}
				}
			}, Util.DEFAULT_THREAD_POOL);
			break;
		}
			
		case R.id.record_list_undo:
			if (mUndos.empty()) break;
			final UndoEntry undo = mUndos.pop();
			mActivity.startActionBarThrobber("Undoing");
			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
				public Void doInThread() throws Exception {
					for (int i = 0; i < undo.filenames.size(); ++i) {
						FileManager.writeJson(new File(mRecordDir, undo.filenames.get(i)), undo.records.get(i));
					}
					return null;
				}
				public void onFinish(Exception error, Void unused) {
					mActivity.stopActionBarThrobber();
					if (error != null) {
						Util.error(mActivity, "Failed to restore file: " + error);
					} else {		
						startListing();
					}
				}
			}, Util.DEFAULT_THREAD_POOL);
			break;
		}
		return true;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.record_list_show_summary: 
			showRecordSummary(info.position);
			return true;
		case R.id.record_list_show_in_map: 
			startReplayActivity(info.position);
			return true;
		case R.id.record_list_resend: 
			sendRecordAtPosition(info.position);
			return true;
		case R.id.record_list_delete: 
			deleteRecordAtPosition(info.position);
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private final void showRecordSummary(int position) { 
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		if (summary == null) return;

		mActivity.startActionBarThrobber("Loading");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readJson(new File(mRecordDir, summary.getBasename()), JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
				if (error != null) {
					Util.error(getActivity(), "Failed to read " + summary.getBasename() + ": " + error);
					mActivity.stopActionBarThrobber();
					return;
				} 
				DialogFragment dialog = new RecordSummaryDialog(record);
				dialog.show(mActivity.getFragmentManager(), "record_summary");
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
	
	private void sendRecordAtPosition(int position) {
		if (!HealthGraphClient.getSingleton().isSignedIn()) {
			showSigninDialog();
			return;
		}
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		if (summary == null) return;
		mAdapter.markAsSending(summary);
		
		mActivity.startActionBarThrobber("Sending");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readJson(new File(mRecordDir, summary.getBasename()), JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
				if (error != null) {
					Util.error(getActivity(), "Failed to read " + summary.getBasename() + ": " + error);
					mActivity.stopActionBarThrobber();
					mAdapter.unmarkAsSending(summary);
					return;
				} 
				HealthGraphClient hgClient = HealthGraphClient.getSingleton();
				hgClient.putNewFitnessActivity(
						record,
						new HealthGraphClient.PutNewFitnessActivityListener() {
							public void onFinish(Exception e, String runkeeperPath) {
								mActivity.stopActionBarThrobber();
								mAdapter.unmarkAsSending(summary);
								if (e != null) {
									Util.error(getActivity(), "Failed to send activity: " + e);
								} else if (runkeeperPath == null) {
									Util.error(getActivity(), "Failed to send activity (reason unknown)");
								} else {
									Util.info(getActivity(), "Sent activity to runkeeper: " + runkeeperPath);
									markAsSaved(summary, runkeeperPath);
								}
							}
						});
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
	
	private void deleteRecordAtPosition(int position) {
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		// Read the file contents so that we can save it it in mUndos.
		mActivity.startActionBarThrobber("Deleting");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				JsonActivity record = null;
				final File file = new File(mRecordDir, summary.getBasename());
				try {
					record = FileManager.readJson(file, JsonActivity.class);
				} finally {
					FileManager.deleteFile(file);
				}
				return record;
			}
			
			public void onFinish(Exception error, JsonActivity value) {
				mActivity.stopActionBarThrobber();
				if (error != null) {
					Util.error(mActivity, "Failed to delete file: " + summary.getBasename() + ": " + error);
				} else {
					if (value != null) {
						// activity==null if the file was corrupt or something. It's ok not create an undo entry in such case
						UndoEntry undo = new UndoEntry();
						undo.add(summary.getBasename(), value);
						mUndos.push(undo);
					}
					startListing();
				}
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
}