package com.ysaito.runningtrainer;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.app.ListFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
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
	private static final String TAG = "RecordList";
	private RecordManager mRecordManager;
	private MainActivity mActivity;
	private MyAdapter mAdapter;

	private class ListThread extends AsyncTask<Void, Void, ArrayList<RecordSummary>> {
	  /**
	   * @param mode not used
	   */
	  @Override
	  protected ArrayList<RecordSummary> doInBackground(Void... unused) {
		  return mRecordManager.listRecords();
	  }

	  @Override
	  protected void onPostExecute(ArrayList<RecordSummary> records) {
		  getActivity().setProgressBarIndeterminateVisibility(false);
		  mAdapter.reset(records);
	  }
	}

	private static class MyAdapter extends ArrayAdapter<RecordSummary> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		public void reset(ArrayList<RecordSummary> newRecords) {
			clear();
			addAll(newRecords);
			notifyDataSetChanged();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final TextView view = (TextView) super.getView(position, convertView, parent);
			final RecordSummary record = this.getItem(position);
			
			GregorianCalendar tmpCalendar = new GregorianCalendar();
			StringBuilder b = new StringBuilder();
			tmpCalendar.setTimeInMillis((long)(record.startTimeSeconds * 1000));
		
			// TODO: change the date format depending on settings.locale
			b.append(String.format("%04d/%02d/%02d-%02d:%02d ",
					tmpCalendar.get(Calendar.YEAR),
					tmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
					tmpCalendar.get(Calendar.DAY_OF_MONTH),
					tmpCalendar.get(Calendar.HOUR_OF_DAY),
					tmpCalendar.get(Calendar.MINUTE)));
			b.append(Util.distanceToString(record.distance));
			b.append("  ");
			b.append(Util.distanceUnitString());
			view.setText(b.toString());
			if (record.runkeeperPath == null) {
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
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//setContentView(R.layout.record_list);
		
		Log.d(TAG, "ListFrag: created");
		mActivity = (MainActivity)getActivity();
		mRecordManager = new RecordManager(mActivity);
		mAdapter = new MyAdapter(mActivity);
		setListAdapter(mAdapter);
		startListing();
		
		registerForContextMenu(getListView());
	}

	Settings.OnChangeListener mSettingsListener = new Settings.OnChangeListener() {
		public void onChange() { 
			Log.d(TAG, "ONCHANGESTE");
			startListing();
		}
	};
	
	@Override public void onResume() {
		super.onResume();
		Settings.registerOnChangeListener(mSettingsListener);
		Log.d(TAG, "RESUMERUSUME");
		startListing();
	}

	@Override public void onPause() {
		super.onPause();
		Settings.UnregisterOnChangeListener(mSettingsListener);
		Log.d(TAG, "PAUSE");
	}
	
	@Override public void onStart() {
		super.onStart();
		startListing();
	}
	
	private void startListing() {
		ListThread thread = new ListThread();
		getActivity().setProgressBarIndeterminateVisibility(true);
		thread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
	}

	private class ReadRecordTask extends AsyncTask<String, Void, HealthGraphClient.JsonActivity> {
		@Override protected HealthGraphClient.JsonActivity doInBackground(String... basename) {
			return mRecordManager.readRecord(basename[0]);
		}
		
		@Override protected void onPostExecute(HealthGraphClient.JsonActivity record) {
			if (record == null) {
				Toast.makeText(mActivity,  "Failed to read record", Toast.LENGTH_LONG).show();
				return;
			}
			RecordReplayFragment fragment = (RecordReplayFragment)mActivity.addTabIfNecessary(
					"Log", 
					"com.ysaito.runningtrainer.RecordReplayFragment",
					"List");
			fragment.setRecord(record);
			mActivity.selectTab("Log");
		}
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		final RecordSummary summary = mAdapter.getItem(position);
		if (summary != null) {
			new ReadRecordTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{summary.basename});
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
		final RecordSummary summary = mAdapter.getItem(info.position);

		switch (item.getItemId()) {
		case R.id.record_list_resend:
			HealthGraphClient.JsonActivity activity = mRecordManager.readRecord(summary.basename);
			if (activity == null) {
				Toast.makeText(getActivity(), "Failed to read " + summary.basename, Toast.LENGTH_LONG).show();
			} else {
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
									mRecordManager.markAsSaved(summary.startTimeSeconds, runkeeperPath);
									startListing(); 
								}
							}
						});
			}
			return true;
		case R.id.record_list_delete:
			new DeleteRecordTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new RecordSummary[]{summary});
			return true;
		}
		return super.onContextItemSelected(item);
	}

	private class DeleteRecordTask extends AsyncTask<RecordSummary[], String, String> {
		@Override protected String doInBackground(RecordSummary[]... records) {
			for (RecordSummary record : records[0]) {
				mRecordManager.deleteRecord(record);
			}
			return null;
		}
		
		@Override protected void onPostExecute(String unused) {
			startListing();
		}
	}
}
