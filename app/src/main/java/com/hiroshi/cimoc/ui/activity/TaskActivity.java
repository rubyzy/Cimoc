package com.hiroshi.cimoc.ui.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.hiroshi.cimoc.R;
import com.hiroshi.cimoc.model.Chapter;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.Task;
import com.hiroshi.cimoc.presenter.TaskPresenter;
import com.hiroshi.cimoc.service.DownloadService;
import com.hiroshi.cimoc.service.DownloadService.DownloadServiceBinder;
import com.hiroshi.cimoc.ui.adapter.BaseAdapter;
import com.hiroshi.cimoc.ui.adapter.TaskAdapter;
import com.hiroshi.cimoc.ui.view.TaskView;
import com.hiroshi.cimoc.utils.DialogUtils;
import com.hiroshi.cimoc.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.OnClick;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Created by Hiroshi on 2016/9/7.
 */
public class TaskActivity extends BackActivity implements TaskView, BaseAdapter.OnItemClickListener, BaseAdapter.OnItemLongClickListener {

    @BindView(R.id.task_layout) View mTaskLayout;
    @BindView(R.id.task_recycler_view) RecyclerView mRecyclerView;

    private TaskAdapter mTaskAdapter;
    private TaskPresenter mPresenter;
    private ServiceConnection mConnection;
    private DownloadServiceBinder mBinder;

    @Override
    protected void initPresenter() {
        mPresenter = new TaskPresenter();
        mPresenter.attachView(this);
    }

    @Override
    protected void initView() {
        super.initView();
        mTaskAdapter = new TaskAdapter(this, new LinkedList<Task>());
        mTaskAdapter.setOnItemClickListener(this);
        mTaskAdapter.setOnItemLongClickListener(this);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(null);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.addItemDecoration(mTaskAdapter.getItemDecoration());
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        long key = getIntent().getLongExtra(EXTRA_KEY, -1);
        mPresenter.load(key);
    }

    @Override
    protected void onDestroy() {
        mPresenter.detachView();
        mPresenter = null;
        super.onDestroy();
        if (mConnection != null) {
            unbindService(mConnection);
            mConnection = null;
            mBinder = null;
        }
        mTaskAdapter = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.task_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.task_delete_multi:
                final List<Task> data = mTaskAdapter.getDateSet();
                final String[] chapter = mTaskAdapter.getTaskTitle();
                final Set<Task> set = new HashSet<>(chapter.length);
                DialogUtils.buildMultiChoiceDialog(this, R.string.task_delete_multi, chapter, null,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if (isChecked) {
                                    set.add(data.get(which));
                                } else {
                                    set.remove(data.get(which));
                                }
                            }
                        }, R.string.task_delete_all, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteTask(new ArrayList<>(data));
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteTask(set);
                            }
                        }).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteTask(Collection<Task> collection) {
        mProgressDialog.show();
        if (collection.isEmpty()) {
            mProgressDialog.hide();
        } else {
            for (Task task : collection) {
                mBinder.getService().removeDownload(task.getId());
            }
            mPresenter.deleteTask(collection, mTaskAdapter.getItemCount() == collection.size());
            mTaskAdapter.removeAll(collection);
        }
    }

    @Override
    public void onItemClick(View view, final int position) {
        Task task = mTaskAdapter.getItem(position);
        switch (task.getState()) {
            case Task.STATE_FINISH:
                final String last = mTaskAdapter.getItem(position).getPath();
                Observable.from(mTaskAdapter.getDateSet())
                        .filter(new Func1<Task, Boolean>() {
                            @Override
                            public Boolean call(Task task) {
                                return task.getState() == Task.STATE_FINISH;
                            }
                        })
                        .map(new Func1<Task, Chapter>() {
                            @Override
                            public Chapter call(Task task) {
                                return new Chapter(task.getTitle(), task.getPath(), task.getMax(), true);
                            }
                        })
                        .toList()
                        .subscribe(new Action1<List<Chapter>>() {
                            @Override
                            public void call(final List<Chapter> list) {
                                for (Chapter chapter : list) {
                                    if (chapter.getPath().equals(last)) {
                                        mTaskAdapter.setLast(last);
                                        long id = mPresenter.updateLast(last);
                                        Intent readerIntent = ReaderActivity.createIntent(TaskActivity.this, id, list);
                                        startActivity(readerIntent);
                                        break;
                                    }
                                }
                            }
                        });
                break;
            case Task.STATE_PAUSE:
            case Task.STATE_ERROR:
                task.setState(Task.STATE_WAIT);
                mTaskAdapter.notifyItemChanged(position);
                Intent taskIntent = DownloadService.createIntent(this, task);
                startService(taskIntent);
                break;
            case Task.STATE_DOING:
            case Task.STATE_WAIT:
            case Task.STATE_PARSE:
                mBinder.getService().removeDownload(task.getId());
                task.setState(Task.STATE_PAUSE);
                mTaskAdapter.notifyItemChanged(task);
                break;
        }
    }

    @Override
    public void onItemLongClick(View view, final int position) {
        DialogUtils.buildPositiveDialog(TaskActivity.this, R.string.dialog_confirm, R.string.task_delete_confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mProgressDialog.show();
                        Task task = mTaskAdapter.getItem(position);
                        mPresenter.deleteTask(task, mTaskAdapter.getItemCount() == 1);
                        mTaskAdapter.remove(position);
                    }
                }).show();
    }

    @OnClick(R.id.task_launch_btn) void onLaunchClick() {
        Comic comic = mPresenter.getComic();
        Intent intent = DetailActivity.createIntent(this, comic.getId(), comic.getSource(), comic.getCid());
        startActivity(intent);
    }

    @Override
    public void onChapterChange(String last) {
        mTaskAdapter.setLast(mPresenter.getComic().getLast());
    }

    @Override
    public void onTaskLoadSuccess(final List<Task> list) {
        mTaskAdapter.setColorId(ThemeUtils.getResourceId(this, R.attr.colorAccent));
        mTaskAdapter.setLast(mPresenter.getComic().getLast());
        mTaskAdapter.addAll(list);
        mPresenter.sortTask(list);
    }

    @Override
    public void onTaskLoadFail() {
        hideProgressBar();
        showSnackbar(R.string.task_load_task_fail);
    }

    @Override
    public void onSortSuccess(final List<Task> list) {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBinder = (DownloadServiceBinder) service;
                mBinder.getService().initTask(mTaskAdapter.getDateSet());
                mTaskAdapter.setData(list);
                mRecyclerView.setAdapter(mTaskAdapter);
                hideProgressBar();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        bindService(new Intent(this, DownloadService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onLoadIndexFail() {
        hideProgressBar();
        showSnackbar(R.string.task_load_index_fail);
    }

    @Override
    public void onTaskAdd(List<Task> list) {
        mTaskAdapter.addAll(0, list);
    }

    @Override
    public void onTaskDeleteSuccess() {
        mProgressDialog.hide();
        showSnackbar(R.string.task_delete_success);
    }

    @Override
    public void onTaskDeleteFail() {
        mProgressDialog.hide();
        showSnackbar(R.string.task_delete_fail);
    }

    @Override
    public void onTaskError(long id) {
        int position = mTaskAdapter.getPositionById(id);
        if (position != -1) {
            Task task = mTaskAdapter.getItem(position);
            if (task.getState() != Task.STATE_PAUSE) {
                mTaskAdapter.getItem(position).setState(Task.STATE_ERROR);
                notifyItemChanged(position);
            }
        }
    }

    @Override
    public void onTaskDoing(long id, int max) {
        int position = mTaskAdapter.getPositionById(id);
        if (position != -1) {
            Task task = mTaskAdapter.getItem(position);
            task.setMax(max);
            task.setState(Task.STATE_DOING);
            notifyItemChanged(position);
        }
    }

    @Override
    public void onTaskFinish(long id) {
        int position = mTaskAdapter.getPositionById(id);
        if (position != -1) {
            Task task = mTaskAdapter.getItem(position);
            task.setProgress(task.getMax());
            task.setState(Task.STATE_FINISH);
            notifyItemChanged(position);
        }
    }

    @Override
    public void onTaskParse(long id) {
        int position = mTaskAdapter.getPositionById(id);
        if (position != -1) {
            mTaskAdapter.getItem(position).setState(Task.STATE_PARSE);
            notifyItemChanged(position);
        }
    }

    @Override
    public void onTaskProcess(long id, int progress, int max) {
        int position = mTaskAdapter.getPositionById(id);
        if (position != -1) {
            Task task = mTaskAdapter.getItem(position);
            task.setMax(max);
            task.setProgress(progress);
            notifyItemChanged(position);
        }
    }

    private void notifyItemChanged(int position) {
        if (!mRecyclerView.isComputingLayout()) {
            mTaskAdapter.notifyItemChanged(position);
        }
    }

    @Override
    protected String getDefaultTitle() {
        return getString(R.string.task_list);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_task;
    }

    @Override
    protected View getLayoutView() {
        return mTaskLayout;
    }

    public static final String EXTRA_KEY = "a";

    public static Intent createIntent(Context context, Long id) {
        Intent intent = new Intent(context, TaskActivity.class);
        intent.putExtra(EXTRA_KEY, id);
        return intent;
    }

}
