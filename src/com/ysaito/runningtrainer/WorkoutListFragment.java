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
public class WorkoutListFragment extends ListFragment {
	private File mWorkoutDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;

	private class UndoEntry {
		public UndoEntry(String f, Workout w) { filename = f; workout = w; }
		public final String filename;
		public final Workout workout;
	}
	private Stack<UndoEntry> mUndos = new Stack<UndoEntry>();
	
	
	static final String NEW_WORKOUT = "+ New Workout";
	private static class MyAdapter extends ArrayAdapter<FileManager.ParsedFilename> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		public void reset(ArrayList<FileManager.ParsedFilename> newRecords) {
			clear();
			final FileManager.ParsedFilename newWorkout = new FileManager.ParsedFilename();
			newWorkout.putLong(FileManager.KEY_WORKOUT_ID, -1);
			newWorkout.putString(FileManager.KEY_WORKOUT_NAME, NEW_WORKOUT);
			addAll(newRecords);
			add(newWorkout);
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final TextView view = (TextView) super.getView(position, convertView, parent);
			final FileManager.ParsedFilename f = this.getItem(position);
			
			StringBuilder b = new StringBuilder();
			b.append(f.getString(FileManager.KEY_WORKOUT_NAME, "unknown"));
			view.setText(b.toString());
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
		getActivity().setProgressBarIndeterminateVisibility(true);
		
		FileManager.runAsync(new FileManager.AsyncRunner<ArrayList<FileManager.ParsedFilename>>() {
			public ArrayList<FileManager.ParsedFilename> doInThread() throws Exception {
				return FileManager.listFiles(mWorkoutDir);
			}
			public void onFinish(Exception error, ArrayList<ParsedFilename> files) {
				getActivity().setProgressBarIndeterminateVisibility(false);
				if (files == null) {
					files = new ArrayList<FileManager.ParsedFilename>();
				}
				mAdapter.reset(files);
				// TODO: handle errors
			}
		});
	}

	private void startWorkoutEditor(Workout workout) {
		WorkoutEditorFragment fragment = (WorkoutEditorFragment)mActivity.findOrCreateFragment(
				"com.ysaito.runningtrainer.WorkoutEditorFragment");
		fragment.setWorkout(workout);
		mActivity.setFragmentForTab("Workout", fragment);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final FileManager.ParsedFilename f = mAdapter.getItem(position);
		if (f == null) return;
		if (f.getString(FileManager.KEY_WORKOUT_NAME, "unknown").equals(NEW_WORKOUT)) {
			Workout workout = new Workout();
			workout.id = System.currentTimeMillis() / 1000;
			workout.name = "Unnamed Workout";
			workout.type = Workout.TYPE_REPEATS;
			workout.repeats = 1;
			workout.children = new Workout[0];
			startWorkoutEditor(workout);
		} else {
			FileManager.runAsync(new FileManager.AsyncRunner<Workout>() {
				public Workout doInThread() throws Exception {
					return FileManager.readFile(mWorkoutDir, f.getBasename(), Workout.class);
				}
				public void onFinish(Exception error, Workout workout) {
					if (error != null) {
						Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + error);
						return;
					}
					startWorkoutEditor(workout);
				}
			});
		}
	}
	
	@Override
	public void onPrepareOptionsMenu (Menu menu) {
		super.onPrepareOptionsMenu(menu);
		menu.findItem(R.id.workout_list_undo).setEnabled(!mUndos.empty());
	}	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.workout_list_undo:
			if (mUndos.empty()) break;
			final UndoEntry undo = mUndos.pop();
			FileManager.runAsync(new FileManager.AsyncRunner<Void>() {
				public Void doInThread() throws Exception {
					FileManager.writeFile(mWorkoutDir, undo.filename, undo.workout);
					return null;
				}
				public void onFinish(Exception error, Void value) {
					if (error != null) {
						Util.error(mActivity, "Failed to restore file: " + undo.filename + ": " + error);
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
		final FileManager.ParsedFilename summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.workout_list_delete:
			// Read the workout file so that we can save it it in mUndos.
			FileManager.runAsync(new FileManager.AsyncRunner<Workout>() {
				public Workout doInThread() throws Exception {
					Workout workout = FileManager.readFile(mWorkoutDir, summary.getBasename(), Workout.class);
					FileManager.deleteFile(mWorkoutDir, summary.getBasename());
					return workout;
				}
				public void onFinish(Exception e, Workout workout) {
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
			});
			return true;
		}
		return super.onContextItemSelected(item);
	}
}
