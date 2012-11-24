package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;

import com.ysaito.runningtrainer.FileManager.ParsedFilename;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
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
 * Activity that lists available game logs.
 *
 */
public class WorkoutListFragment extends ListFragment {
	private static final String TAG = "WorkoutListFragment";
	private File mWorkoutDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;

	private class UndoEntry {
		public UndoEntry(String f, JsonWorkout w) { filename = f; workout = w; }
		public final String filename;
		public final JsonWorkout workout;
	}
	private Stack<UndoEntry> mUndos = new Stack<UndoEntry>();
	
	private class MyAdapter extends BaseAdapter {
    	private final LayoutInflater mInflater;
    	private final ArrayList<FileManager.ParsedFilename> mRecords = new ArrayList<FileManager.ParsedFilename>();
    	
    	public MyAdapter(Context context) { 
    		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    	}

		public View getView(final int position, View convertView, ViewGroup parent) {
	   		View layout;
    		if (convertView == null) {
    			layout = (View)mInflater.inflate(R.layout.workout_list_row, parent, false);
    		} else {
    			layout = (View)convertView;
    		}
			final TextView view = (TextView)layout.findViewById(R.id.workout_list_row_name);
			final FileManager.ParsedFilename f = (FileManager.ParsedFilename)getItem(position);
			final StringBuilder b = new StringBuilder();
			final String name = f.getString(FileManager.KEY_WORKOUT_NAME, "unknown");
			b.append(name);
			view.setText(b.toString());
			
			final ImageView deleteView = (ImageView)layout.findViewById(R.id.workout_list_row_delete);
			deleteView.setVisibility(View.VISIBLE);
			deleteView.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					deleteWorkoutAtPosition(position);
				}
			});
			return layout;
		}

		public int getCount() {
			return mRecords.size();
		}

		public Object getItem(int position) {
			if (position < 0 || position >= mRecords.size()) return null;
			return mRecords.get(position);
		}

		public long getItemId(int position) {
			return position;
		}
    	
		public void reset(ArrayList<FileManager.ParsedFilename> newRecords) {
			mRecords.clear();
			mRecords.addAll(newRecords);
			notifyDataSetChanged();
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		Plog.d(TAG, "onCreate");
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
        inflater.inflate(R.menu.workout_list_options_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mActivity = (MainActivity)getActivity();
		mWorkoutDir = FileManager.getWorkoutDir(mActivity);
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
	
	private void startListing() {
		mActivity.startActionBarThrobber("Loading");
		FileManager.runAsync(new FileManager.AsyncRunner<ArrayList<FileManager.ParsedFilename>>() {
			public ArrayList<FileManager.ParsedFilename> doInThread() throws Exception {
				return FileManager.listFiles(mWorkoutDir);
			}
			public void onFinish(Exception error, ArrayList<ParsedFilename> files) {
				mActivity.stopActionBarThrobber();
				if (files == null) {
					files = new ArrayList<FileManager.ParsedFilename>();
				}
				mAdapter.reset(files);
				// TODO: handle errors
			}
		}, Util.DEFAULT_THREAD_POOL);
	}

	private void startWorkoutEditor(JsonWorkout workout) {
		WorkoutEditorFragment fragment = (WorkoutEditorFragment)mActivity.findOrCreateFragment(
				"com.ysaito.runningtrainer.WorkoutEditorFragment");
		fragment.setWorkout(workout);
		mActivity.setFragmentForTab("Workout", fragment);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final FileManager.ParsedFilename f = (FileManager.ParsedFilename)mAdapter.getItem(position);
		if (f == null) return;
		FileManager.runAsync(new FileManager.AsyncRunner<JsonWorkout>() {
			public JsonWorkout doInThread() throws Exception {
				return FileManager.readJson(new File(mWorkoutDir, f.getBasename()), JsonWorkout.class);
			}
			public void onFinish(Exception error, JsonWorkout workout) {
				if (error != null) {
					Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + error);
					return;
				}
				startWorkoutEditor(workout);
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
	
	@Override
	public void onPrepareOptionsMenu (Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.workout_list_undo).setEnabled(!mUndos.empty());
	}	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.workout_list_new: {
			JsonWorkout workout = new JsonWorkout();
			workout.id = System.currentTimeMillis() / 1000;
			workout.name = "Unnamed Workout";
			workout.type = JsonWorkout.TYPE_REPEATS;
			workout.repeats = 1;
			workout.children = new JsonWorkout[0];
			startWorkoutEditor(workout);
			break;
		}
		case R.id.workout_list_undo:
			if (mUndos.empty()) break;
			final UndoEntry undo = mUndos.pop();
			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
				public Void doInThread() throws Exception {
					FileManager.writeJson(new File(mWorkoutDir, undo.filename), undo.workout);
					return null;
				}
				public void onFinish(Exception error, Void value) {
					if (error != null) {
						Util.error(mActivity, "Failed to restore file: " + undo.filename + ": " + error);
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
	public void onCreateContextMenu(
			ContextMenu menu,
			View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		final MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.workout_list_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		switch (item.getItemId()) {
		case R.id.workout_list_delete:
			deleteWorkoutAtPosition(info.position);
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private void deleteWorkoutAtPosition(int position) {
		final FileManager.ParsedFilename summary = (FileManager.ParsedFilename)mAdapter.getItem(position);
		// Read the workout file so that we can save it it in mUndos.
		FileManager.runAsync(new FileManager.AsyncRunner<JsonWorkout>() {
			public JsonWorkout doInThread() throws Exception {
				final File file = new File(mWorkoutDir, summary.getBasename());
				final JsonWorkout workout = FileManager.readJson(file, JsonWorkout.class);
				FileManager.deleteFile(file);
				return workout;
			}
			public void onFinish(Exception e, JsonWorkout workout) {
				if (e != null) {
					Util.error(mActivity, "Failed to delete " + summary.getBasename() + ": " + e);
				} else {
					if (workout != null) {
						// workout==null if the file was corrupt or something. It's ok not create an undo entry in such case
						UndoEntry undo = new UndoEntry(summary.getBasename(), workout);
						mUndos.push(undo);
					}
					startListing();
				}
			}
		}, Util.DEFAULT_THREAD_POOL);
	}
}
