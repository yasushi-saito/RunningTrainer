package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;

import com.ysaito.runningtrainer.FileManager.ParsedFilename;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
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
import android.widget.TextView;

/**
 * A fragment that displays list of completed activities. Clicking on the activity name will display
 * a RecordReplayFragment, which contains MapView and the lap stats.
 */
public class RecordListFragment extends ListFragment {
	private static final String TAG = "RecordListFragment";
	
	private File mRecordDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;
	
	private class UndoEntry {
		public void add(String filename, JsonActivity r) { records.add(r); filenames.add(filename); }
		public final ArrayList<String> filenames = new ArrayList<String>();
		public final ArrayList<JsonActivity> records = new ArrayList<JsonActivity>();
	}
	private Stack<UndoEntry> mUndos = new Stack<UndoEntry>();
	
	private class MyAdapter extends BaseAdapter {
    	private final LayoutInflater mInflater;
    	private final ArrayList<FileManager.ParsedFilename> mRecords = new ArrayList<FileManager.ParsedFilename>();
    	
    	public MyAdapter(Context context) { 
    		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    	}

    	public void sort(Comparator<FileManager.ParsedFilename> comparator) {
    		Collections.sort(mRecords, comparator);
			notifyDataSetChanged();
    	}
    	
		public void reset(ArrayList<FileManager.ParsedFilename> newRecords) {
			mRecords.clear();
			mRecords.addAll(newRecords);
			sort(COMPARE_BY_START_TIME);
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
	   		View layout;
    		if (convertView == null) {
    			layout = (View)mInflater.inflate(R.layout.record_list_row, parent, false);
    		} else {
    			layout = (View)convertView;
    		}
			final TextView view = (TextView)layout.findViewById(R.id.record_list_row_text);
			final FileManager.ParsedFilename f = (FileManager.ParsedFilename)getItem(position);
			
			StringBuilder b = new StringBuilder();
			b.append(Util.dateToString(f.getLong(FileManager.KEY_START_TIME, 0)));
			b.append(" (");
			b.append(Util.durationToString(f.getLong(FileManager.KEY_DURATION, 0)));
			b.append(")<br><b>");
			b.append(Util.distanceToString(f.getLong(FileManager.KEY_DISTANCE, 0), Util.DistanceUnitType.KM_OR_MILE));
			b.append("</b> ");
			b.append(Util.distanceUnitString());
			
			view.setText(Html.fromHtml(b.toString()));
			if (f.getString(FileManager.KEY_RUNKEEPER_PATH, null) == null) {
				view.setTextColor(0xffff0000);
			} else {
				view.setTextColor(0xffffffff);
			}
			
			final ImageView deleteView = (ImageView)layout.findViewById(R.id.record_list_row_delete);
			deleteView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					// TODO Auto-generated method stub
					deleteRecordAtPosition(position);
				}
			});
			final ImageView syncView = (ImageView)layout.findViewById(R.id.record_list_row_send);
			syncView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					// TODO Auto-generated method stub
					sendRecordAtPosition(position);
				}
			});
			return layout;
		}

		public int getCount() {
			return mRecords.size();
		}

		public Object getItem(int position) {
			// TODO Auto-generated method stub
			if (position < 0 || position >= mRecords.size()) return null;
			return mRecords.get(position);
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

	private int mNumBackgroundTasksRunning = 0;
	private void startBusyThrob(String text) {
		if (mNumBackgroundTasksRunning == 0) {
			mActivity.startActionBarStatusUpdate(text);
		}
		++mNumBackgroundTasksRunning;
	}
	private void stopBusyThrob() {
		--mNumBackgroundTasksRunning;
		if (mNumBackgroundTasksRunning == 0) {
			mActivity.stopActionBarStatusUpdate();
		}
	}
	
	private void startListing() {
		startBusyThrob("Loading");
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
		final FileManager.ParsedFilename f = (FileManager.ParsedFilename)mAdapter.getItem(position);
		if (f == null) return;

		startBusyThrob("Loading");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readJson(mRecordDir, f.getBasename(), JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
				if (error != null) {
					Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + error);
					return;
				}
				stopBusyThrob();
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
				filenames[i] = (FileManager.ParsedFilename)mAdapter.getItem(i);
			}
			startBusyThrob("Deleting");
			FileManager.runAsync(new FileManager.AsyncRunner<UndoEntry>() {
				public UndoEntry doInThread() throws Exception {
					UndoEntry newUndos = new UndoEntry();
					for (FileManager.ParsedFilename f : filenames) {
						String basename = f.getBasename();
						JsonActivity record = FileManager.readJson(mRecordDir, f.getBasename(), JsonActivity.class);
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
			startBusyThrob("Undoing");
			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
				public Void doInThread() throws Exception {
					for (int i = 0; i < undo.filenames.size(); ++i) {
						FileManager.writeJson(mRecordDir, undo.filenames.get(i), undo.records.get(i));
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
		switch (item.getItemId()) {
		case R.id.record_list_resend: 
			sendRecordAtPosition(info.position);
			return true;
		case R.id.record_list_delete: 
			deleteRecordAtPosition(info.position);
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private void sendRecordAtPosition(int position) {
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		startBusyThrob("Sending");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				return FileManager.readJson(mRecordDir, summary.getBasename(), JsonActivity.class);
			}
			public void onFinish(Exception error, JsonActivity record) {
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
	}
	
	private void deleteRecordAtPosition(int position) {
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		// Read the file contents so that we can save it it in mUndos.
		startBusyThrob("Deleting");
		FileManager.runAsync(new FileManager.AsyncRunner<JsonActivity>() {
			public JsonActivity doInThread() throws Exception {
				JsonActivity record = FileManager.readJson(mRecordDir, summary.getBasename(), JsonActivity.class);
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
	}
}