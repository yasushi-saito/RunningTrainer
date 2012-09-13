package com.ysaito.runningtrainer;

import java.util.ArrayList;

import com.ysaito.runningtrainer.HealthGraphClient.JsonActivity;

import android.app.Activity;
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
		  Log.d(TAG, "Start listing");
		  return mRecordManager.listRecords();
	  }

	  @Override
	  protected void onPostExecute(ArrayList<RecordSummary> records) {
		  // setProgressBarIndeterminateVisibility(false);
		  mAdapter.reset(records);
	  }
	}

	private static class MyAdapter extends ArrayAdapter<RecordSummary> {
		public MyAdapter(Context activity) {
			super(activity, android.R.layout.simple_list_item_1);
		}

		private Settings mSettings;
		public void reset(ArrayList<RecordSummary> newRecords) {
			mSettings = Settings.getSettings(getContext());
			clear();
			addAll(newRecords);
			notifyDataSetChanged();
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView) super.getView(position, convertView, parent);
			view.setText(this.getItem(position).toString(mSettings));
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

	@Override public void onResume() {
		super.onResume();
		Log.d(TAG, "ListFrag: resume");
		startListing();
	}

	@Override public void onStart() {
		super.onStart();
		Log.d(TAG, "ListFrag: start");
		startListing();
	}
	
	private void startListing() {
		ListThread thread = new ListThread();
		// setProgressBarIndeterminateVisibility(true);
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
			Toast.makeText(mActivity,  "Start show!", Toast.LENGTH_LONG).show();
			RecordReplayFragment fragment = (RecordReplayFragment)mActivity.addTabIfNecessary("Log", "com.ysaito.runningtrainer.RecordReplayFragment");
			fragment.setRecord(record);
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
				hgClient.putNewFitnessActivity(
						activity,
						new HealthGraphClient.PutNewFitnessActivityListener() {
							public void onFinish(Exception e, String runkeeperPath) {
								if (e != null) {
									Toast.makeText(getActivity(), "Failed to send activity: " + e.toString(), Toast.LENGTH_LONG).show();
								} else {
									Toast.makeText(getActivity(), "Sent activity to runkeeper: " + runkeeperPath, Toast.LENGTH_SHORT).show();
									mRecordManager.markAsSaved(summary.startTime, runkeeperPath);
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
