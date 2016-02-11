package com.nehori.searchableprovider;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.List;

/**
 * Provides access to the video database.
 */
public class StorageProvider extends ContentProvider {
    String TAG = StorageProvider.class.getSimpleName();

    public static String AUTHORITY = "com.nehori.searchableprovider.StorageProvider";

    // MIME types used for searching words or looking up a single definition
    public static final String WORDS_MIME_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE +
                                                  "/vnd.nehori.searchableprovider";
    public static final String DEFINITION_MIME_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE +
                                                       "/vnd.nehori.searchableprovider";
    private static final String REC_HDD_DEVICE_PATH_FORMAT = "'/storage/[a-z][1-9]*/Stream[1-9]/*'";
    protected static final int LIMIT_USB_CONTENT_COUNT = 30;
    String[] mSuggestionColumns;

    // UriMatcher stuff
    private static final int SEARCH_WORDS = 0;
    private static final int GET_WORD = 1;
    private static final int SEARCH_SUGGEST = 2;
    private static final int REFRESH_SHORTCUT = 3;
    private static final UriMatcher sURIMatcher = buildUriMatcher();

    /**
     * Builds up a UriMatcher for search suggestion and shortcut refresh queries.
     */
    private static UriMatcher buildUriMatcher() {
        UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
        // to get definitions...
        matcher.addURI(AUTHORITY, "videodatabase", SEARCH_WORDS);
        matcher.addURI(AUTHORITY, "videodatabase/#", GET_WORD);
        // to get suggestions...
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);

        /* The following are unused in this implementation, but if we include
         * {@link SearchManager#SUGGEST_COLUMN_SHORTCUT_ID} as a column in our suggestions table, we
         * could expect to receive refresh queries when a shortcutted suggestion is displayed in
         * Quick Search Box, in which case, the following Uris would be provided and we
         * would return a cursor with a single item representing the refreshed suggestion data.
         */
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT, REFRESH_SHORTCUT);
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*", REFRESH_SHORTCUT);
        return matcher;
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate()");
        mSuggestionColumns = new String[] {
            "_id",
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_DURATION
        };

        return true;
    }

    /**
     * Handles all the video searches and suggestion queries from the Search Manager.
     * When requesting a specific word, the uri alone is required.
     * When searching all of the video for matches, the selectionArgs argument must carry
     * the search query as the first element.
     * All other arguments are ignored.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Log.d(TAG, "[search:]selection:" + selection);
        Log.d(TAG, "[search:]sortOrder:" + sortOrder);
        Log.d(TAG, "[search:]uri:" + uri.toString());
        // Use the UriMatcher to see what kind of query we have and format the db query accordingly
        Log.d(TAG, "query:" + selectionArgs[0]);

        switch (sURIMatcher.match(uri)) {
            case SEARCH_SUGGEST:
                Log.d(TAG, "SEARCH_SUGGEST");
                if (selectionArgs == null) {
                  throw new IllegalArgumentException(
                      "selectionArgs must be provided for the Uri: " + uri);
                }
                return search(selectionArgs[0]);
//            case SEARCH_WORDS:
//                Log.d(TAG, "SEARCH_WORDS");
//                if (selectionArgs == null) {
//                  throw new IllegalArgumentException(
//                      "selectionArgs must be provided for the Uri: " + uri);
//                }
//                return search(selectionArgs[0]);
//            case GET_WORD:
//                Log.d(TAG, "GET_WORD");
//                return getWord(uri);
//            case REFRESH_SHORTCUT:
//                return refreshShortcut(uri);
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }
    }

    private String getSearchUsbSelection() {
        StringBuilder selection = new StringBuilder();
        String data = "";
        selection.append(MediaStore.Images.Media.MIME_TYPE);
        selection.append(" = ");
        selection.append("\'" + "image/jpeg" + "\'");
        selection.append(" AND ");
        selection.append(MediaStore.Images.Media.TITLE + " LIKE ?");
        data = MediaStore.Images.Media.DATA;
        if (!selection.toString().isEmpty()) {
            List<String> usbPaths = StorageUtil.getUsbDevicePaths();
            if(usbPaths.size() > 0) {

                selection.append(" AND ");
                selection.append("( ");

                for (int i = 0; i < usbPaths.size(); i++) {
                    // usb path has format "/storage/{uuid}", for example : "/storage/sda1"
                    String path = "'" + usbPaths.get(i) + "/%'";
                    selection.append(data).append(" LIKE ").append(path);
                    if (i != usbPaths.size() - 1) {
                        selection.append(" OR ");
                    }
                }
                selection.append(" ) ");
            } // End of path filter.

            selection.append(" AND ");
            selection.append(data + " NOT GLOB " + REC_HDD_DEVICE_PATH_FORMAT);
        }
        return selection.toString();
    }

    private String getSearchUsbSortOrderAndLimit() {
        String sort = null;
        StringBuilder builder = new StringBuilder();
        builder.append(MediaStore.Video.Media.DATE_TAKEN);
        builder.append(" DESC ");
        builder.append(",");
        builder.append(MediaStore.Images.Media.DATE_MODIFIED);
        builder.append(" DESC ");
        if (!builder.toString().isEmpty()) {
            builder.append(" LIMIT ");
            builder.append(Integer.toString(LIMIT_USB_CONTENT_COUNT));
            sort = builder.toString();
        }
        return sort;
    }

    protected String getStringFromColumn(Cursor c, String column) {
        String str = null;
        if ((c != null) && (c.getCount() > 0) && (column != null) && (!column.isEmpty())) {
            int index = c.getColumnIndex(column);
            Log.d(TAG, "index = " + index);
            if (index >= 0) {
                str = c.getString(index);
            }
        }
        return str;
    }

    private Cursor search(String key) {
        ContentResolver contentresolver = getContext().getApplicationContext().getContentResolver();

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        Log.d(TAG, "[search:] key=" + key);
        String selection = getSearchUsbSelection();
        String[] selectionArgs = new String[] { "%" + key + "%" };
        String sortAndLimit = getSearchUsbSortOrderAndLimit();

        long procStart = System.nanoTime();
        Log.d(TAG, "[search:] uri=" + uri);
        Log.d(TAG, "[search:] selection=" + selection);
        Log.d(TAG, "[search:] sortAndLimit=" + sortAndLimit);

        Cursor cursor = contentresolver.query(uri, null, selection, selectionArgs, sortAndLimit);
        long procEnd = System.nanoTime();
        Log.d(TAG, "HomeNetwork MediaStore query =" + procStart + ":" + procEnd);

        if (cursor == null) {
            Log.d(TAG, "queryMediaStore() cursor is null.");
            return cursor;
        }

        Log.d(TAG, "search() query end. count=" + cursor.getCount());
        MatrixCursor matrixCursor = new MatrixCursor(mSuggestionColumns);
        cursor.moveToFirst();
        String id = getStringFromColumn(cursor, MediaStore.Images.Media._ID);

        matrixCursor.addRow(new String[] {
                    id,
                    Intent.ACTION_VIEW,
                    null,
                    null,
                    "",
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI + "/" + id,
                    getStringFromColumn(cursor, MediaStore.Images.Media.TITLE),
                    getStringFromColumn(cursor, MediaStore.Images.Media.MIME_TYPE),
                    "", //getStringFromColumn(cursor, MediaStore.Images.Media.DATE_TAKEN),
                    "00:00"
        });

        return matrixCursor;
    }

    /**
     * This method is required in order to query the supported types.
     * It's also useful in our own query() method to determine the type of Uri received.
     */
    @Override
    public String getType(Uri uri) {
        StackTraceElement stack = new Throwable().getStackTrace()[1];
        Log.d(TAG, "[search:]" + stack.getMethodName() + ":uri:" + uri);

        switch (sURIMatcher.match(uri)) {
            case SEARCH_WORDS:
                return WORDS_MIME_TYPE;
            case GET_WORD:
                return DEFINITION_MIME_TYPE;
            case SEARCH_SUGGEST:
                return SearchManager.SUGGEST_MIME_TYPE;
            case REFRESH_SHORTCUT:
                return SearchManager.SHORTCUT_MIME_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    // Other required implementations...
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}
