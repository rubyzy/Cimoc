package com.hiroshi.cimoc.core;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.hiroshi.cimoc.core.manager.SourceManager;
import com.hiroshi.cimoc.model.ComicDao;
import com.hiroshi.cimoc.model.DaoMaster;
import com.hiroshi.cimoc.model.Source;
import com.hiroshi.cimoc.model.SourceDao;
import com.hiroshi.cimoc.model.TaskDao;
import com.hiroshi.cimoc.utils.FileUtils;

import org.greenrobot.greendao.database.Database;

/**
 * Created by Hiroshi on 2016/8/12.
 */
public class DBOpenHelper extends DaoMaster.OpenHelper {

    public DBOpenHelper(Context context, String name) {
        super(context, name);
    }

    public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }

    @Override
    public void onCreate(Database db) {
        super.onCreate(db);
        initSource(db);
    }

    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        db.beginTransaction();
        switch (oldVersion) {
            case 1:
                SourceDao.createTable(db, false);
                initSource(db);
            case 2:
                updateHighlight(db);
            case 3:
                TaskDao.createTable(db, false);
                updateDownload(db);
            case 5:
                updateHHAAZZ();
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    private void initSource(Database db) {
        SourceDao dao = new DaoMaster(db).newSession().getSourceDao();
        dao.insert(new Source(null, SourceManager.SOURCE_IKANMAN, true));
        dao.insert(new Source(null, SourceManager.SOURCE_DMZJ, true));
        dao.insert(new Source(null, SourceManager.SOURCE_HHAAZZ, true));
        dao.insert(new Source(null, SourceManager.SOURCE_CCTUKU, true));
        dao.insert(new Source(null, SourceManager.SOURCE_U17, true));
        dao.insert(new Source(null, SourceManager.SOURCE_DM5, true));
        dao.insert(new Source(null, SourceManager.SOURCE_WEBTOON, true));
        dao.insert(new Source(null, SourceManager.SOURCE_HHSSEE, true));
        dao.insert(new Source(null, SourceManager.SOURCE_57MH, true));
    }

    private void updateHHAAZZ() {
        if (FileUtils.isDirsExist(FileUtils.getPath(Download.dirPath, "汗汗漫画"))
                && !FileUtils.isDirsExist(FileUtils.getPath(Download.dirPath, "手机汗汗"))) {
            FileUtils.rename(FileUtils.getPath(Download.dirPath, "汗汗漫画"), FileUtils.getPath(Download.dirPath, "手机汗汗"));
        }
    }

    private void updateDownload(Database db) {
        db.execSQL("ALTER TABLE \"COMIC\" RENAME TO \"COMIC2\"");
        ComicDao.createTable(db, false);
        db.execSQL("INSERT INTO \"COMIC\" (\"_id\", \"SOURCE\", \"CID\", \"TITLE\", \"COVER\", \"HIGHLIGHT\", \"UPDATE\", \"FINISH\", \"FAVORITE\", \"HISTORY\", \"DOWNLOAD\", \"LAST\", \"PAGE\")" +
                " SELECT \"_id\", \"SOURCE\", \"CID\", \"TITLE\", \"COVER\", \"HIGHLIGHT\", \"UPDATE\", null, \"FAVORITE\", \"HISTORY\", null, \"LAST\", \"PAGE\" FROM \"COMIC2\"");
        db.execSQL("DROP TABLE \"COMIC2\"");
    }

    private void updateHighlight(Database db) {
        db.execSQL("ALTER TABLE \"COMIC\" RENAME TO \"COMIC2\"");
        ComicDao.createTable(db, false);
        db.execSQL("INSERT INTO \"COMIC\" (\"_id\", \"SOURCE\", \"CID\", \"TITLE\", \"COVER\", \"UPDATE\", \"HIGHLIGHT\", \"FAVORITE\", \"HISTORY\", \"LAST\", \"PAGE\")" +
                " SELECT \"_id\", \"SOURCE\", \"CID\", \"TITLE\", \"COVER\", \"UPDATE\", 0, \"FAVORITE\", \"HISTORY\", \"LAST\", \"PAGE\" FROM \"COMIC2\"");
        db.execSQL("DROP TABLE \"COMIC2\"");
        db.execSQL("UPDATE \"COMIC\" SET \"HIGHLIGHT\" = 1, \"FAVORITE\" = " + System.currentTimeMillis() + " WHERE \"FAVORITE\" = " + 0xFFFFFFFFFFFL);
    }

}
