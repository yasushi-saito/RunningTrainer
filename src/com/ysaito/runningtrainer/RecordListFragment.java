package com.ysaito.runningtrainer;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ysaito.runningtrainer.FileManager.FilenameSummary;

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
import android.widget.Toast;

/**
 * Activity that lists available game logs.
 *
 */
public class RecordListFragment extends ListFragment {
	private File mRecordDir;
	private MainActivity mActivity;
	private MyAdapter mAdapter;

	private static class MyAdapter extends ArrayAdapter<FileManager.FilenameSummary> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		public void reset(ArrayList<FileManager.FilenameSummary> newRecords) {
			clear();
			addAll(newRecords);
			notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final TextView view = (TextView) super.getView(position, convertView, parent);
			final FileManager.FilenameSummary f = this.getItem(position);
			
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
			public void onFinish(Exception e, ArrayList<FilenameSummary> files) {
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
		final FileManager.FilenameSummary f = mAdapter.getItem(position);
		if (f == null) return;
		
		FileManager.readFileAsync(mRecordDir, f.getBasename(), HealthGraphClient.JsonActivity.class,
				new FileManager.ReadListener<HealthGraphClient.JsonActivity>() {
			public void onFinish(Exception e, HealthGraphClient.JsonActivity record) { 
				if (record == null) {
					Toast.makeText(mActivity,  "Failed to read file : " + f.getBasename() + ": " + e.toString(), Toast.LENGTH_LONG).show();
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

	private void markAsSaved(FileManager.FilenameSummary f, String runkeeperPath) {
		// List the filenames again, just in case some attributes have changed
		ArrayList<FileManager.FilenameSummary> list = FileManager.listFiles(mRecordDir);
		for (FileManager.FilenameSummary r : list) {
			if (r.getLong(FileManager.KEY_START_TIME, -1) == f.getLong(FileManager.KEY_START_TIME, -2)) {
				f.putString(FileManager.KEY_RUNKEEPER_PATH, FileManager.sanitizeString(runkeeperPath));
				File orgFile = new File(mRecordDir, r.getBasename());
				orgFile.renameTo(new File(mRecordDir, f.getBasename()));
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final FileManager.FilenameSummary summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.record_list_resend:
			FileManager.readFileAsync(
					mRecordDir, summary.getBasename(), HealthGraphClient.JsonActivity.class,
					new FileManager.ReadListener<HealthGraphClient.JsonActivity>() {
						public void onFinish(Exception e, HealthGraphClient.JsonActivity activity) {
							if (e != null) {
								Toast.makeText(getActivity(), "Failed to read " + summary.getBasename() + ": " + e.toString(), Toast.LENGTH_LONG).show();
								return;
							}
							HealthGraphClient hgClient = HealthGraphClient.getSingleton();
							getActivity().setProgressBarIndeterminateVisibility(true);
							hgClient.putNewFitnessActivity(
									activity,
									new HealthGraphClient.PutNewFitnessActivityListener() {
										public void onFinish(Exception e, String runkeeperPath) {
											if (e != null) {
												Toast.makeText(getActivity(), "Failed to send activity: " + e.toString(), Toast.LENGTH_LONG).show();
											} else if (runkeeperPath == null) {
												Toast.makeText(getActivity(), "Failed to send activity (reason unknown)", Toast.LENGTH_LONG).show();
											} else {
												Toast.makeText(getActivity(), "Sent activity to runkeeper: " + runkeeperPath, Toast.LENGTH_SHORT).show();
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
			FileManager.deleteFilesAsync(
					mRecordDir, 
					new String[]{summary.getBasename()},
					new FileManager.ResultListener() {
						public void onFinish(Exception e) { 
							// TODO: handle errors
							if (e != null) Util.error(getActivity(), "Failed to delete " + summary.getBasename() + ": " + e.toString());
							startListing();
						}
			});
			return true;
		}
		return super.onContextItemSelected(item);
	}
}
