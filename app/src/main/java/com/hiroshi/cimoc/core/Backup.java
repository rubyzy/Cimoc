package com.hiroshi.cimoc.core;

import android.os.Environment;

import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.utils.FileUtils;
import com.hiroshi.cimoc.utils.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/**
 * Created by Hiroshi on 2016/7/22.
 */
public class Backup {

    public static String dirPath =
            FileUtils.getPath(Environment.getExternalStorageDirectory().getAbsolutePath(), "Cimoc", "backup");

    public static Observable<Integer> save(final List<Comic> list) {
        return Observable.create(new Observable.OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                try {
                    JSONArray array = new JSONArray();
                    for (Comic comic : list) {
                        JSONObject object = new JSONObject();
                        object.put("s", comic.getSource());
                        object.put("i", comic.getCid());
                        object.put("t", comic.getTitle());
                        object.put("c", comic.getCover());
                        object.put("u", comic.getUpdate());
                        object.put("f", comic.getFinish());
                        object.put("l", comic.getLast());
                        object.put("p", comic.getPage());
                        array.put(object);
                    }
                    String name = StringUtils.getDateStringWithSuffix("cimoc");
                    if (FileUtils.writeStringToFile(dirPath, name, array.toString())) {
                        subscriber.onNext(array.length());
                        subscriber.onCompleted();
                    } else {
                        subscriber.onError(new Exception());
                    }
                } catch (Exception e) {
                    subscriber.onError(new Exception());
                }
            }
        });
    }

    public static Observable<String[]> load() {
        return Observable.create(new Observable.OnSubscribe<String[]>() {
            @Override
            public void call(Subscriber<? super String[]> subscriber) {
                String[] files = FileUtils.listFilesNameHaveSuffix(dirPath, "cimoc");
                if (files != null) {
                    Arrays.sort(files);
                    if (files.length == 0) {
                        subscriber.onError(new Exception());
                    } else {
                        subscriber.onNext(files);
                        subscriber.onCompleted();
                    }
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    public static Observable<List<Comic>> restore(final String name) {
        return Observable.create(new Observable.OnSubscribe<List<Comic>>() {
            @Override
            public void call(Subscriber<? super List<Comic>> subscriber) {
                try {
                    List<Comic> list = new LinkedList<>();
                    String jsonString = FileUtils.readSingleLineFromFile(dirPath, name);
                    JSONArray array = new JSONArray(jsonString);
                    for (int i = 0; i != array.length(); ++i) {
                        JSONObject object = array.getJSONObject(i);
                        int source = object.getInt("s");
                        String cid = object.getString("i");
                        String title = object.getString("t");
                        String cover = object.getString("c");
                        String update = object.optString("u", null);
                        Boolean finish = object.has("f") ? object.getBoolean("f") : null;
                        String last = object.optString("l", null);
                        Integer page = object.has("p") ? object.getInt("p") : null;
                        list.add(new Comic(null, source, cid, title, cover, false, update, finish, null, null, null, last, page));
                    }
                    subscriber.onNext(list);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(new Exception());
                }
            }
        }).subscribeOn(Schedulers.io());
    }

}
