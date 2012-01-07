package com.axelby.podax;

import java.io.File;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Environment;

public class SubscriptionProvider extends ContentProvider {
	public static String AUTHORITY = "com.axelby.podax.subscriptionprovider";
	public static Uri URI = Uri.parse("content://" + AUTHORITY + "/subscriptions");
	public static final String ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
			+ "/vnd.axelby.subscription";
	public static final String DIR_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
			+ "/vnd.axelby.subscription";
	public static final String PODCAST_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
			+ "/vnd.axelby.podcast";

	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_TITLE = "title";
	public static final String COLUMN_URL = "url";
	public static final String COLUMN_LAST_MODIFIED = "lastModified";
	public static final String COLUMN_LAST_UPDATE = "lastUpdate";
	public static final String COLUMN_ETAG = "eTag";
	public static final String COLUMN_THUMBNAIL = "thumbnail";
	public static final String COLUMN_GPODDER_SYNCTIME = "gpodder_synctime";

	private static final int SUBSCRIPTIONS = 1;
	private static final int SUBSCRIPTION_ID = 2;
	private static final int PODCASTS = 3;
	private static final int SUBSCRIPTIONS_SEARCH = 4;

	static UriMatcher _uriMatcher;
	static HashMap<String, String> _columnMap;

	static {
		_uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		_uriMatcher.addURI(AUTHORITY, "subscriptions", SUBSCRIPTIONS);
		_uriMatcher.addURI(AUTHORITY, "subscriptions/#", SUBSCRIPTION_ID);
		_uriMatcher.addURI(AUTHORITY, "subscriptions/#/podcasts", PODCASTS);
		_uriMatcher.addURI(AUTHORITY, "subscriptions/search", SUBSCRIPTIONS_SEARCH);

		_columnMap = new HashMap<String, String>();
		_columnMap.put(COLUMN_ID, "_id");
		_columnMap.put(COLUMN_TITLE, "title");
		_columnMap.put(COLUMN_URL, "url");
		_columnMap.put(COLUMN_LAST_MODIFIED, "lastModified");
		_columnMap.put(COLUMN_LAST_UPDATE, "id");
		_columnMap.put(COLUMN_ETAG, "eTag");
		_columnMap.put(COLUMN_THUMBNAIL, "thumbnail");
		_columnMap.put(COLUMN_GPODDER_SYNCTIME, "gpodder_synctime");
	}

	DBAdapter _dbAdapter;

	@Override
	public boolean onCreate() {
		_dbAdapter = new DBAdapter(getContext());
		return true;
	}

	@Override
	public String getType(Uri uri) {
		switch (_uriMatcher.match(uri)) {
		case SUBSCRIPTIONS:
			return DIR_TYPE;
		case SUBSCRIPTION_ID:
			return ITEM_TYPE;
		case PODCASTS:
			return PODCAST_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI");
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		int uriMatch = _uriMatcher.match(uri);

		if (uriMatch == PODCASTS) {
			return getContext().getContentResolver().query(PodcastProvider.URI,
					projection, "subscriptionId = ?",
					new String[] { uri.getPathSegments().get(1) },
					PodcastProvider.COLUMN_PUB_DATE + " DESC");
		}

		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		sqlBuilder.setProjectionMap(_columnMap);
		sqlBuilder.setTables("subscriptions");

		switch (uriMatch) {
		case SUBSCRIPTIONS:
			if (sortOrder == null)
				sortOrder = "title IS NULL, title";
			break;
		case SUBSCRIPTION_ID:
			sqlBuilder.appendWhere("_id = " + uri.getLastPathSegment());
			break;
		case SUBSCRIPTIONS_SEARCH:
			sqlBuilder.appendWhere("LOWER(title) LIKE ?");
			if (!selectionArgs[0].startsWith("%"))
				selectionArgs[0] = "%" + selectionArgs[0] + "%";
			if (sortOrder == null)
				sortOrder = "title";
			break;
		default:
			throw new IllegalArgumentException("Unknown URI");
		}

		SQLiteDatabase db = _dbAdapter.getReadableDatabase();
		Cursor c = sqlBuilder.query(db, projection, selection, selectionArgs,
				null, null, sortOrder);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		switch (_uriMatcher.match(uri)) {
		case SUBSCRIPTIONS:
			break;
		case SUBSCRIPTION_ID:
			String extraWhere = COLUMN_ID + " = " + uri.getLastPathSegment();
			if (where != null)
				where = extraWhere + " AND " + where;
			else
				where = extraWhere;
			break;
		default:
			throw new IllegalArgumentException("Unknown URI");
		}

		SQLiteDatabase db = _dbAdapter.getWritableDatabase();
		int count = db.update("subscriptions", values, where, whereArgs);
		if (!URI.equals(uri))
			getContext().getContentResolver().notifyChange(uri, null);
		getContext().getContentResolver().notifyChange(URI, null);
		return count;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		if (values.size() == 0)
			return null;
		switch (_uriMatcher.match(uri)) {
		case SUBSCRIPTIONS:
			break;
		default:
			throw new IllegalArgumentException("Unknown URI");
		}

		SQLiteDatabase db = _dbAdapter.getWritableDatabase();
		long id = db.insert("subscriptions", null, values);
		getContext().getContentResolver().notifyChange(URI, null);
		return ContentUris.withAppendedId(URI, id);
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		switch (_uriMatcher.match(uri)) {
		case SUBSCRIPTIONS:
			break;
		case SUBSCRIPTION_ID:
			String extraWhere = COLUMN_ID + " = " + uri.getLastPathSegment();
			if (where != null)
				where = extraWhere + " AND " + where;
			else
				where = extraWhere;
			getContext().getContentResolver().delete(PodcastProvider.URI,
					"subscriptionId = ?",
					new String[] { uri.getLastPathSegment() });
			break;
		default:
			throw new IllegalArgumentException("Unknown URI");
		}

		SQLiteDatabase db = _dbAdapter.getWritableDatabase();
		int count = db.delete("subscriptions", where, whereArgs);
		getContext().getContentResolver().notifyChange(URI, null);
		return count;
	}

	public static String getThumbnailFilename(long id) {
		String externalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
		String podaxDir = externalPath + "/Android/data/com.axelby.podax/files/";
		File podaxFile = new File(podaxDir);
		if (!podaxFile.exists())
			podaxFile.mkdirs();
		return podaxDir + "/" + id + ".jpg";
	}

}
