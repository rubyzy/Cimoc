package com.hiroshi.cimoc.ui.adapter;

import android.content.Context;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.hiroshi.cimoc.R;
import com.hiroshi.cimoc.core.manager.SourceManager;
import com.hiroshi.cimoc.fresco.ControllerBuilderProvider;
import com.hiroshi.cimoc.model.MiniComic;

import java.util.Iterator;
import java.util.List;

import butterknife.BindView;

/**
 * Created by Hiroshi on 2016/7/1.
 */
public class ComicAdapter extends BaseAdapter<MiniComic> {

    private ControllerBuilderProvider mProvider;

    public class ViewHolder extends BaseViewHolder {
        @BindView(R.id.item_comic_image) SimpleDraweeView comicImage;
        @BindView(R.id.item_comic_title) TextView comicTitle;
        @BindView(R.id.item_comic_source) TextView comicSource;
        @BindView(R.id.item_comic_new) View comicNew;

        public ViewHolder(View view) {
            super(view);
        }
    }

    public ComicAdapter(Context context, List<MiniComic> list) {
        super(context, list);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_comic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        MiniComic comic = mDataSet.get(position);
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.comicTitle.setText(comic.getTitle());
        viewHolder.comicSource.setText(SourceManager.getTitle(comic.getSource()));
        DraweeController controller = mProvider.get(comic.getSource())
                .setOldController(viewHolder.comicImage.getController())
                .setUri(comic.getCover())
                .build();
        viewHolder.comicImage.setController(controller);
    }

    public void setProvider(ControllerBuilderProvider provider) {
        mProvider = provider;
    }

    @Override
    public RecyclerView.ItemDecoration getItemDecoration() {
        return new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int offset = parent.getWidth() / 90;
                outRect.set(offset, 0, offset, (int) (2.8 * offset));
            }
        };
    }

    public void update(MiniComic comic) {
        int position = mDataSet.indexOf(comic);
        if (position != -1) {
            mDataSet.remove(comic);
            mDataSet.add(0, comic);
            notifyItemMoved(position, 0);
        } else {
            mDataSet.add(0, comic);
            notifyItemInserted(0);
        }
    }

    public void removeBySource(int source) {
        Iterator<MiniComic> iterator = mDataSet.iterator();
        while (iterator.hasNext()) {
            MiniComic comic = iterator.next();
            if (source == comic.getSource()) {
                iterator.remove();
            }
        }
        notifyDataSetChanged();
    }

    public void removeById(long id) {
        for (MiniComic comic : mDataSet) {
            if (id == comic.getId()) {
                remove(comic);
                break;
            }
        }
    }

    public MiniComic getItemById(long id) {
        for (MiniComic comic : mDataSet) {
            if (comic.getId() == id) {
                return comic;
            }
        }
        return null;
    }

}
