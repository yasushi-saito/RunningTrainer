package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;

import android.app.ListFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
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

	static final String NEW_WORKOUT = "+ New Workout";
	private static class MyAdapter extends ArrayAdapter<FileManager.FilenameSummary> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		public void reset(ArrayList<FileManager.FilenameSummary> newRecords) {
			clear();
			final FileManager.FilenameSummary newWorkout = new FileManager.FilenameSummary();
			newWorkout.putLong(FileManager.KEY_WORKOUT_ID, -1);
			newWorkout.putString(FileManager.KEY_WORKOUT_NAME, NEW_WORKOUT);
			addAll(newRecords);
			add(newWorkout);
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final TextView view = (TextView) super.getView(position, convertView, parent);
			final FileManager.FilenameSummary f = this.getItem(position);
			
			StringBuilder b = new StringBuilder();
			b.append(f.getString(FileManager.KEY_WORKOUT_NAME, "unknown"));
			view.setText(b.toString());
			return view;
		}
	};
	
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
		FileManager.listFilesAsync(mWorkoutDir, new FileManager.ListFilesListener() {
			public void onFinish(Exception e, ArrayList<FileManager.FilenameSummary> files) {
				getActivity().setProgressBarIndeterminateVisibility(false);
				if (files == null) {
					files = new ArrayList<FileManager.FilenameSummary>();
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
		final FileManager.FilenameSummary f = mAdapter.getItem(position);
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
			FileManager.readFileAsync(mWorkoutDir, f.getBasename(), Workout.class,
					new FileManager.ReadListener<Workout>() {
				public void onFinish(Exception e, Workout workout) { 
					if (workout == null) {
						Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + e.toString());
						return;
					}
					startWorkoutEditor(workout);
				}
			});
		}
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

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final FileManager.FilenameSummary summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.record_list_delete:
			FileManager.deleteFilesAsync(
					mWorkoutDir, 
					new String[]{summary.getBasename()},
					new FileManager.ResultListener() {
						public void onFinish(Exception e) { 
							if (e != null)
								Util.error(mActivity, "Failed to delete " + summary.getBasename() + ": " + e.toString());
							startListing();
						}
			});
			return true;
		}
		return super.onContextItemSelected(item);
	}
}
