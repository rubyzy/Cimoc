package com.hiroshi.cimoc.presenter;

import com.hiroshi.cimoc.core.Download;
import com.hiroshi.cimoc.core.manager.ComicManager;
import com.hiroshi.cimoc.core.manager.TaskManager;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.MiniComic;
import com.hiroshi.cimoc.model.Task;
import com.hiroshi.cimoc.rx.RxBus;
import com.hiroshi.cimoc.rx.RxEvent;
import com.hiroshi.cimoc.ui.view.TaskView;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Created by Hiroshi on 2016/9/7.
 */
public class TaskPresenter extends BasePresenter<TaskView> {

    private TaskManager mTaskManager;
    private ComicManager mComicManager;
    private Comic mComic;

    public TaskPresenter() {
        mTaskManager = TaskManager.getInstance();
        mComicManager = ComicManager.getInstance();
    }

    @Override
    protected void initSubscription() {
        addSubscription(RxEvent.TASK_STATE_CHANGE, new Action1<RxEvent>() {
            @Override
            public void call(RxEvent rxEvent) {
                long id = (long) rxEvent.getData(1);
                switch ((int) rxEvent.getData()) {
                    case Task.STATE_PARSE:
                        mBaseView.onTaskParse(id);
                        break;
                    case Task.STATE_DOING:
                        mBaseView.onTaskDoing(id, (int) rxEvent.getData(2));
                        break;
                    case Task.STATE_FINISH:
                        mBaseView.onTaskFinish(id);
                        break;
                    case Task.STATE_ERROR:
                        mBaseView.onTaskError(id);
                        break;
                }
            }
        });
        addSubscription(RxEvent.TASK_PROCESS, new Action1<RxEvent>() {
            @Override
            public void call(RxEvent rxEvent) {
                long id = (long) rxEvent.getData();
                mBaseView.onTaskProcess(id, (int) rxEvent.getData(1), (int) rxEvent.getData(2));
            }
        });
        addSubscription(RxEvent.TASK_ADD, new Action1<RxEvent>() {
            @Override
            public void call(RxEvent rxEvent) {
                List<Task> list = (List<Task>) rxEvent.getData(1);
                Task task = list.get(0);
                if (task.getKey() == mComic.getId()) {
                    mBaseView.onTaskAdd(list);
                }
            }
        });
        addSubscription(RxEvent.COMIC_CHAPTER_CHANGE, new Action1<RxEvent>() {
            @Override
            public void call(RxEvent rxEvent) {
                String last = (String) rxEvent.getData();
                int page = (int) rxEvent.getData(1);
                mComic.setHistory(System.currentTimeMillis());
                mComic.setLast(last);
                mComic.setPage(page);
                mComicManager.update(mComic);
                mBaseView.onChapterChange(last);
            }
        });
        addSubscription(RxEvent.COMIC_PAGE_CHANGE, new Action1<RxEvent>() {
            @Override
            public void call(RxEvent rxEvent) {
                mComic.setPage((Integer) rxEvent.getData());
                mComicManager.update(mComic);
            }
        });
    }

    public Comic getComic() {
        return mComic;
    }

    public void load(final long key) {
        mComic = mComicManager.load(key);
        mCompositeSubscription.add(mTaskManager.list(key)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<Task>>() {
                    @Override
                    public void call(List<Task> list) {
                        for (Task task : list) {
                            int state = task.isFinish() ? Task.STATE_FINISH : Task.STATE_PAUSE;
                            task.setInfo(mComic.getSource(), mComic.getCid(), mComic.getTitle());
                            task.setState(state);
                        }
                        mBaseView.onTaskLoadSuccess(list);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mBaseView.onTaskLoadFail();
                    }
                }));
    }

    public void sortTask(final List<Task> list) {
        mCompositeSubscription.add(Download.get(mComic.getSource(), mComic.getTitle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<String>>() {
                    @Override
                    public void call(final List<String> paths) {
                        Collections.sort(list, new Comparator<Task>() {
                            @Override
                            public int compare(Task lhs, Task rhs) {
                                return paths.indexOf(lhs.getPath()) - paths.indexOf(rhs.getPath());
                            }
                        });
                        mBaseView.onSortSuccess(list);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mBaseView.onLoadIndexFail();
                    }
                }));
    }

    public void deleteTask(final Task task, final boolean isEmpty) {
        mCompositeSubscription.add(Download.delete(mComic.getSource(), mComic.getTitle(), task.getTitle())
                .flatMap(new Func1<Object, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Object o) {
                        return mComicManager.runInTx(new Runnable() {
                            @Override
                            public void run() {
                                if (isEmpty) {
                                    if (mComic.getFavorite() == null && mComic.getHistory() == null) {
                                        mComicManager.delete(mComic);
                                    } else {
                                        mComic.setDownload(null);
                                        mComicManager.update(mComic);
                                    }
                                    RxBus.getInstance().post(new RxEvent(RxEvent.DOWNLOAD_DELETE, mComic.getId()));
                                }
                                mTaskManager.delete(task);
                            }
                        });
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Void>() {
                    @Override
                    public void call(Void aVoid) {
                        mBaseView.onTaskDeleteSuccess();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mBaseView.onTaskDeleteFail();
                    }
                }));
    }

    public void deleteTask(final Collection<Task> collection, final boolean isEmpty) {
        mCompositeSubscription.add(Observable.from(collection)
                .map(new Func1<Task, String>() {
                    @Override
                    public String call(Task task) {
                        return task.getTitle();
                    }
                })
                .toList()
                .flatMap(new Func1<List<String>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(List<String> strings) {
                        return Download.delete(mComic.getSource(), mComic.getTitle(), strings);
                    }
                })
                .flatMap(new Func1<Object, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Object o) {
                        return mComicManager.runInTx(new Runnable() {
                            @Override
                            public void run() {
                                if (isEmpty) {
                                    if (mComic.getFavorite() == null && mComic.getHistory() == null) {
                                        mComicManager.delete(mComic);
                                    } else {
                                        mComic.setDownload(null);
                                        mComicManager.update(mComic);
                                    }
                                    RxBus.getInstance().post(new RxEvent(RxEvent.DOWNLOAD_DELETE, mComic.getId()));
                                }
                                for (Task task : collection) {
                                    mTaskManager.delete(task);
                                }
                            }
                        });
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<Object>() {
                    @Override
                    public void call(Object o) {
                        mBaseView.onTaskDeleteSuccess();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mBaseView.onTaskDeleteFail();
                    }
                }));
    }

    public long updateLast(String last) {
        mComic.setHistory(System.currentTimeMillis());
        if (!last.equals(mComic.getLast())) {
            mComic.setLast(last);
            mComic.setPage(1);
        }
        if (mComic.getId() != null) {
            mComicManager.update(mComic);
        } else {
            long id = mComicManager.insert(mComic);
            mComic.setId(id);
        }
        RxBus.getInstance().post(new RxEvent(RxEvent.HISTORY_COMIC, new MiniComic(mComic)));
        return mComic.getId();
    }

}