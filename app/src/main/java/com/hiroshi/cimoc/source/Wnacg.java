package com.hiroshi.cimoc.source;

import com.hiroshi.cimoc.core.manager.SourceManager;
import com.hiroshi.cimoc.core.parser.MangaParser;
import com.hiroshi.cimoc.core.parser.NodeIterator;
import com.hiroshi.cimoc.core.parser.SearchIterator;
import com.hiroshi.cimoc.model.Chapter;
import com.hiroshi.cimoc.model.Comic;
import com.hiroshi.cimoc.model.ImageUrl;
import com.hiroshi.cimoc.soup.Node;
import com.hiroshi.cimoc.utils.StringUtils;

import java.util.LinkedList;
import java.util.List;

import okhttp3.Request;

/**
 * Created by Hiroshi on 2016/8/9.
 */
public class Wnacg extends MangaParser {

    @Override
    public Request getSearchRequest(String keyword, int page) {
        String url = StringUtils.format("https://www.wnacg.com/albums-index-page-%d-sname-%s.html", page, keyword);
        return new Request.Builder().url(url).build();
    }

    @Override
    public SearchIterator getSearchIterator(String html, int page) {
        Node body = new Node(html);
        return new NodeIterator(body.list("#bodywrap > div.grid > div > ul > li")) {
            @Override
            protected Comic parse(Node node) {
                String cid = node.attr("div.info > div.title > a", "href", "-|\\.", 3);
                String title = node.text("div.info > div.title > a");
                String author = StringUtils.match("\\[(.*?)\\]", title, 1);
                title = title.replaceFirst("\\[.*?\\]\\s*", "");
                String cover = node.attr("div.pic_box > a > img", "data-original");
                String update = node.text("div.info > div.info_col").trim();
                update = StringUtils.match("\\d{4}-\\d{2}-\\d{2}", update, 0);
                return new Comic(SourceManager.SOURCE_WNACG, cid, title, cover, update, author);
            }
        };
    }

    @Override
    public Request getInfoRequest(String cid) {
        String url = StringUtils.format("https://www.wnacg.com/photos-index-aid-%s.html", cid);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<Chapter> parseInfo(String html, Comic comic) {
        List<Chapter> list = new LinkedList<>();
        Node body = new Node(html);
        String length = body.text("#bodywrap > div > div.uwconn > label:eq(1)", 3, -2);
        int size = Integer.parseInt(length) % 12 == 0 ? Integer.parseInt(length) / 12 : Integer.parseInt(length) / 12 + 1;
        for (int i = 1; i <= size; ++i) {
            list.add(0, new Chapter("Ch" + i, String.valueOf(i)));
        }

        String title = body.text("#bodywrap > h2");
        String intro = body.text("#bodywrap > div > div.uwconn > p", 3);
        String author = body.text("#bodywrap > div > div.uwuinfo > p");
        String cover = body.attr("#bodywrap > div > div.uwthumb > img", "data-original");
        comic.setInfo(title, cover, null, intro, author, true);

        return list;
    }

    @Override
    public Request getRecentRequest(int page) {
        String url = StringUtils.format("https://www.wnacg.com/albums-index-page-%d.html", page);
        return new Request.Builder().url(url).build();
    }

    @Override
    public List<Comic> parseRecent(String html, int page) {
        List<Comic> list = new LinkedList<>();
        Node body = new Node(html);
        for (Node node : body.list("#bodywrap > div.grid > div > ul > li")) {
            String cid = node.attr("div.info > div.title > a", "href", "-|\\.", 3);
            String title = node.text("div.info > div.title > a");
            String author = StringUtils.match("\\[(.*?)\\]", title, 1);
            title = title.replaceFirst("\\[.*?\\]\\s*", "");
            String cover = node.attr("div.pic_box > a > img", "data-original");
            String update = node.text("div.info > div.info_col").trim();
            update = StringUtils.match("\\d{4}-\\d{2}-\\d{2}", update, 0);
            list.add(new Comic(SourceManager.SOURCE_WNACG, cid, title, cover, update, author));
        }
        return list;
    }

    @Override
    public Request getImagesRequest(String cid, String path) {
        String url = StringUtils.format("https://www.wnacg.com/photos-index-page-%s-aid-%s.html", path, cid);
        return new Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.106 Safari/537.36").build();
    }

    @Override
    public List<ImageUrl> parseImages(String html) {
        List<ImageUrl> list = new LinkedList<>();
        Node body = new Node(html);
        int count = 0;
        for (Node node : body.list("#bodywrap > div.grid > div > ul > li > div.pic_box > a")) {
            String url = "https://www.wnacg.com/".concat(node.attr("href"));
            list.add(new ImageUrl(++count, url, true));
        }
        return list;
    }

    @Override
    public Request getLazyRequest(String url) {
        return new Request.Builder().url(url).build();
    }

    @Override
    public String parseLazy(String html, String url) {
        if (html == null) {
            return null;
        }
        return new Node(html).attr("#picarea", "src");
    }

}
