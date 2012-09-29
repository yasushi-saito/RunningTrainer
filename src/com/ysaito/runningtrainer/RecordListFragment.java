package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Stack;

import com.ysaito.runningtrainer.FileManager.ParsedFilename;
import com.ysaito.runningtrainer.HealthGraphClient.JsonActivity;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity that lists available game logs.
 *
 */
public class RecordListFragment extends ListFragment {
	@SuppressWarnings("unused")
	private static final String TAG = "RecordListFragment";
	
	private File mRecordDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;
	
	private class UndoEntry {
		public void add(String filename, HealthGraphClient.JsonActivity r) { records.add(r); filenames.add(filename); }
		public final ArrayList<String> filenames = new ArrayList<String>();
		public final ArrayList<HealthGraphClient.JsonActivity> records = new ArrayList<HealthGraphClient.JsonActivity>();
	}
	private Stack<UndoEntry> mUndos = new Stack<UndoEntry>();
	
	private static class MyAdapter extends ArrayAdapter<FileManager.ParsedFilename> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		public void reset(ArrayList<FileManager.ParsedFilename> newRecords) {
			clear();
			addAll(newRecords);
			sort(COMPARE_BY_START_TIME);
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final TextView view = (TextView) super.getView(position, convertView, parent);
			final FileManager.ParsedFilename f = this.getItem(position);
			
			GregorianCalendar tmpCalendar = new GregorianCalendar();
			StringBuilder b = new StringBuilder();
			tmpCalendar.setTimeInMillis(f.getLong(FileManager.KEY_START_TIME, 0) * 1000);
		
			// TODO: change the date format depending on settings.locale
			b.append(String.format("%04d/%02d/%02d-%02d:%02d ",
					tmpCalendar.get(Calendar.YEAR),
					tmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
					tmpCalendar.get(Calendar.DAY_OF_MONTH),
					tmpCalendar.get(Calendar.HOUR_OF_DAY),
					tmpCalendar.get(Calendar.MINUTE)));
			b.append(Util.distanceToString(f.getLong(FileManager.KEY_DISTANCE, 0)));
			b.append("  ");
			b.append(Util.distanceUnitString());
			view.setText(b.toString());
			if (f.getString(FileManager.KEY_RUNKEEPER_PATH, null) == null) {
				view.setTextColor(0xffff0000);
			} else {
				view.setTextColor(0xffffffff);
			}
			return view;
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
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

	private void startBusyThrob() {
		getActivity().setProgressBarIndeterminateVisibility(true);
	}
	private void stopBusyThrob() {
		getActivity().setProgressBarIndeterminateVisibility(false);
	}
	
	private void startListing() {
		startBusyThrob();
		FileManager.runAsync(new FileManager.AsyncRunner<ArrayList<ParsedFilename>>() {
			public ArrayList<ParsedFilename> doInThread() throws Exception {
				return FileManager.listFiles(mRecordDir);
			}
			public void onFinish(Exception error, ArrayList<ParsedFilename> files) {
				stopBusyThrob();
				if (files != null) {
					mAdapter.reset(files);
				}
			}
		});
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final FileManager.ParsedFilename f = mAdapter.getItem(position);
		if (f == null) return;

		FileManager.runAsync(new FileManager.AsyncRunner<HealthGraphClient.JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readFile(mRecordDir, f.getBasename(), HealthGraphClient.JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
				if (error != null) {
					Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + error);
					return;
				}
				RecordReplayFragment fragment = (RecordReplayFragment)mActivity.findOrCreateFragment(
						"com.ysaito.runningtrainer.RecordReplayFragment");
				fragment.setRecord(record);
				mActivity.setFragmentForTab("Log", fragment);
			}
		});
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
			}
		});
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
	
	@Override
	public void onPrepareOptionsMenu (Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.record_list_undo).setEnabled(!mUndos.empty());
	}	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.record_list_sort_by_date:
			mAdapter.sort(COMPARE_BY_START_TIME);
			break;
		case R.id.record_list_sort_by_distance:
			mAdapter.sort(COMPARE_BY_DISTANCE);
			break;
		case R.id.record_list_delete_all: {
			// Extract the filenames so that they can be read from a different thread
			final FileManager.ParsedFilename[] filenames = new FileManager.ParsedFilename[mAdapter.getCount()];
			for (int i = 0; i < mAdapter.getCount(); ++i) {
				filenames[i] = mAdapter.getItem(i);
			}
			startBusyThrob();
			FileManager.runAsync(new FileManager.AsyncRunner<UndoEntry>() {
				public UndoEntry doInThread() throws Exception {
					UndoEntry newUndos = new UndoEntry();
					for (FileManager.ParsedFilename f : filenames) {
						String basename = f.getBasename();
						HealthGraphClient.JsonActivity record = FileManager.readFile(mRecordDir, f.getBasename(), HealthGraphClient.JsonActivity.class);
						FileManager.deleteFile(mRecordDir, basename);
						newUndos.add(basename, record);
					}
					return newUndos;
				}
				public void onFinish(Exception error, UndoEntry newUndos) {
					stopBusyThrob();
					if (error != null) {
						Util.error(mActivity, "Failed to delete files: " + error);
					} else {		
						mUndos.add(newUndos);
						startListing();
					}
				}
			});
			break;
		}
			
		case R.id.record_list_undo:
			if (mUndos.empty()) break;
			final UndoEntry undo = mUndos.pop();
			startBusyThrob();
			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
				public Void doInThread() throws Exception {
					for (int i = 0; i < undo.filenames.size(); ++i) {
						FileManager.writeFile(mRecordDir, undo.filenames.get(i), undo.records.get(i));
					}
					return null;
				}
				public void onFinish(Exception error, Void unused) {
					stopBusyThrob();
					if (error != null) {
						Util.error(mActivity, "Failed to restore file: " + error);
					} else {		
						startListing();
					}
				}
			});
			break;
		}
		return true;
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final FileManager.ParsedFilename summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.record_list_resend: 
			startBusyThrob();
			FileManager.runAsync(new FileManager.AsyncRunner<HealthGraphClient.JsonActivity>() {
				public HealthGraphClient.JsonActivity doInThread() throws Exception {
					return FileManager.readFile(mRecordDir, summary.getBasename(), HealthGraphClient.JsonActivity.class);
				}
				public void onFinish(Exception error, HealthGraphClient.JsonActivity record) {
					if (error != null) {
						Util.error(getActivity(), "Failed to read " + summary.getBasename() + ": " + error);
						stopBusyThrob();
						return;
					} 
					HealthGraphClient hgClient = HealthGraphClient.getSingleton();
					hgClient.putNewFitnessActivity(
							record,
							new HealthGraphClient.PutNewFitnessActivityListener() {
								public void onFinish(Exception e, String runkeeperPath) {
									stopBusyThrob();
									if (e != null) {
										Util.error(getActivity(), "Failed to send activity: " + e);
									} else if (runkeeperPath == null) {
										Util.error(getActivity(), "Failed to send activity (reason unknown)");
									} else {
										Util.info(getActivity(), "Sent activity to runkeeper: " + runkeeperPath);
										markAsSaved(summary, runkeeperPath);
										startListing(); 
									}
								}
							});
				}
			});
			return true;
		case R.id.record_list_delete: 
			// Read the file contents so that we can save it it in mUndos.
			startBusyThrob();
			FileManager.runAsync(new FileManager.AsyncRunner<HealthGraphClient.JsonActivity>() {
				public JsonActivity doInThread() throws Exception {
					HealthGraphClient.JsonActivity record = FileManager.readFile(mRecordDir, summary.getBasename(), HealthGraphClient.JsonActivity.class);
					FileManager.deleteFile(mRecordDir, summary.getBasename());
					return record;
				}

				public void onFinish(Exception error, JsonActivity value) {
					stopBusyThrob();
					if (error != null) {
						Util.error(mActivity, "Failed to restore file: " + summary.getBasename() + ": " + error);
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
			});
			return true;
		}
		return super.onContextItemSelected(item);
	}
}
