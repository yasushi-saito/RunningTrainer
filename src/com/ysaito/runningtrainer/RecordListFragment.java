package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
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
public class RecordListFragment extends ListFragment {
	private File mRecordDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;
	
	private class UndoEntry {
		public UndoEntry(FileManager.ParsedFilename f, HealthGraphClient.JsonActivity r) { record = r; filename = f; }
		public final FileManager.ParsedFilename filename;
		public final HealthGraphClient.JsonActivity record;
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
	
	private void startListing() {
		getActivity().setProgressBarIndeterminateVisibility(true);
		FileManager.listFilesAsync(mRecordDir, new FileManager.ListFilesListener() {
			public void onFinish(Exception e, ArrayList<ParsedFilename> files) {
				getActivity().setProgressBarIndeterminateVisibility(false);
				if (files != null) {
					mAdapter.reset(files);
				}
				// TODO: handle errors
			}
		});
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final FileManager.ParsedFilename f = mAdapter.getItem(position);
		if (f == null) return;
		
		FileManager.readFileAsync(mRecordDir, f.getBasename(), HealthGraphClient.JsonActivity.class,
				new FileManager.ReadListener<HealthGraphClient.JsonActivity>() {
			public void onFinish(Exception e, HealthGraphClient.JsonActivity record) { 
				if (e != null) {
					Util.error(mActivity,  "Failed to read file : " + f.getBasename() + ": " + e);
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

	private void markAsSaved(FileManager.ParsedFilename f, String runkeeperPath) {
		// List the filenames again, just in case some attributes have changed
		ArrayList<FileManager.ParsedFilename> list = FileManager.listFiles(mRecordDir);
		for (FileManager.ParsedFilename r : list) {
			if (r.getLong(FileManager.KEY_START_TIME, -1) == f.getLong(FileManager.KEY_START_TIME, -2)) {
				f.putString(FileManager.KEY_RUNKEEPER_PATH, FileManager.sanitizeString(runkeeperPath));
				File orgFile = new File(mRecordDir, r.getBasename());
				orgFile.renameTo(new File(mRecordDir, f.getBasename()));
			}
		}
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
		case R.id.record_list_undo:
			if (mUndos.empty()) break;
			final UndoEntry undo = mUndos.pop();
			FileManager.writeFileAsync(
					mRecordDir, undo.filename.getBasename(), undo.record, new FileManager.ResultListener() {
						public void onFinish(Exception e) {
							if (e != null) {
								Util.error(mActivity, "Failed to restore file: " + undo.filename.getBasename());
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
			FileManager.readFileAsync(
					mRecordDir, summary.getBasename(), HealthGraphClient.JsonActivity.class,
					new FileManager.ReadListener<HealthGraphClient.JsonActivity>() {
						public void onFinish(Exception e, HealthGraphClient.JsonActivity activity) {
							if (e != null) {
								Util.error(getActivity(), "Failed to read " + summary.getBasename() + ": " + e);
								return;
							}
							HealthGraphClient hgClient = HealthGraphClient.getSingleton();
							getActivity().setProgressBarIndeterminateVisibility(true);
							hgClient.putNewFitnessActivity(
									activity,
									new HealthGraphClient.PutNewFitnessActivityListener() {
										public void onFinish(Exception e, String runkeeperPath) {
											if (e != null) {
												Util.error(getActivity(), "Failed to send activity: " + e);
											} else if (runkeeperPath == null) {
												Util.error(getActivity(), "Failed to send activity (reason unknown)");
											} else {
												Util.info(getActivity(), "Sent activity to runkeeper: " + runkeeperPath);
												getActivity().setProgressBarIndeterminateVisibility(false);
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
			FileManager.readFileAsync(
					mRecordDir, summary.getBasename(), HealthGraphClient.JsonActivity.class,
					new FileManager.ReadListener<HealthGraphClient.JsonActivity>() {
						public void onFinish(final Exception e, final HealthGraphClient.JsonActivity activity) {
							FileManager.deleteFilesAsync(
									mRecordDir, 
									new String[]{summary.getBasename()},
									new FileManager.ResultListener() {
										public void onFinish(Exception e) { 
											if (e != null) {
												Util.error(getActivity(), "Failed to delete " + summary.getBasename() + ": " + e.toString());
											} else {
												if (activity != null) {
													// activity==null if the file was corrupt or something. It's ok not create an undo entry in such case
													UndoEntry undo = new UndoEntry(summary, activity);
													mUndos.push(undo);
												}
												startListing();
											}
										}
									});
							}
					});
			return true;
		}
		return super.onContextItemSelected(item);
	}
}
