package biz.dealnote.messenger.db.impl;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import biz.dealnote.messenger.db.DatabaseIdRange;
import biz.dealnote.messenger.db.MessengerContentProvider;
import biz.dealnote.messenger.db.column.FaveGroupsColumns;
import biz.dealnote.messenger.db.column.FaveLinksColumns;
import biz.dealnote.messenger.db.column.FavePhotosColumns;
import biz.dealnote.messenger.db.column.FavePostsColumns;
import biz.dealnote.messenger.db.column.FaveUsersColumns;
import biz.dealnote.messenger.db.column.FaveVideosColumns;
import biz.dealnote.messenger.db.interfaces.IFaveStorage;
import biz.dealnote.messenger.db.model.entity.CommunityEntity;
import biz.dealnote.messenger.db.model.entity.FaveGroupEntity;
import biz.dealnote.messenger.db.model.entity.FaveLinkEntity;
import biz.dealnote.messenger.db.model.entity.FaveUserEntity;
import biz.dealnote.messenger.db.model.entity.OwnerEntities;
import biz.dealnote.messenger.db.model.entity.PhotoEntity;
import biz.dealnote.messenger.db.model.entity.PostEntity;
import biz.dealnote.messenger.db.model.entity.UserEntity;
import biz.dealnote.messenger.db.model.entity.VideoEntity;
import biz.dealnote.messenger.model.criteria.FavePhotosCriteria;
import biz.dealnote.messenger.model.criteria.FavePostsCriteria;
import biz.dealnote.messenger.model.criteria.FaveVideosCriteria;
import io.reactivex.Completable;
import io.reactivex.Single;

import static biz.dealnote.messenger.db.impl.OwnersStorage.appendOwnersInsertOperations;
import static biz.dealnote.messenger.util.Objects.isNull;
import static biz.dealnote.messenger.util.Objects.nonNull;
import static biz.dealnote.messenger.util.Utils.safeCountOf;

/**
 * Created by hp-dv6 on 28.05.2016.
 * VKMessenger
 */
class FaveStorage extends AbsStorage implements IFaveStorage {

    FaveStorage(@NonNull AppStorages mRepositoryContext) {
        super(mRepositoryContext);
    }

    @Override
    public Single<List<PostEntity>> getFavePosts(@NonNull FavePostsCriteria criteria) {
        return Single.create(e -> {
            final Uri uri = MessengerContentProvider.getFavePostsContentUriFor(criteria.getAccountId());
            final Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);

            final List<PostEntity> dbos = new ArrayList<>(safeCountOf(cursor));
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    dbos.add(mapFavePosts(cursor));
                }

                cursor.close();
            }

            e.onSuccess(dbos);
        });
    }

    @Override
    public Completable storePosts(int accountId, List<PostEntity> posts, OwnerEntities owners, boolean clearBeforeStore) {
        return Completable.create(e -> {
            final Uri uri = MessengerContentProvider.getFavePostsContentUriFor(accountId);

            ArrayList<ContentProviderOperation> operations = new ArrayList<>();

            if (clearBeforeStore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

            for (PostEntity dbo : posts) {
                ContentValues cv = new ContentValues();
                cv.put(FavePostsColumns.POST, GSON.toJson(dbo));

                operations.add(ContentProviderOperation
                        .newInsert(uri)
                        .withValues(cv)
                        .build());
            }

            if (nonNull(owners)) {
                appendOwnersInsertOperations(operations, accountId, owners);
            }

            getContentResolver().applyBatch(MessengerContentProvider.AUTHORITY, operations);
            e.onComplete();
        });
    }

    @Override
    public Single<List<FaveLinkEntity>> getFaveLinks(int accountId) {
        return Single.create(e -> {
            Uri uri = MessengerContentProvider.getFaveLinksContentUriFor(accountId);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);

            List<FaveLinkEntity> data = new ArrayList<>();
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    data.add(mapFaveLink(cursor));
                }

                cursor.close();
            }

            e.onSuccess(data);
        });
    }

    @Override
    public Completable removeLink(int accountId, String id) {
        return Completable.fromAction(() -> {
            final Uri uri = MessengerContentProvider.getFaveLinksContentUriFor(accountId);
            final String where = FaveLinksColumns.LINK_ID + " LIKE ?";
            final String[] args = {id};
            getContentResolver().delete(uri, where, args);
        });
    }

    @Override
    public Completable storeLinks(int accountId, List<FaveLinkEntity> entities, boolean clearBefore) {
        return Completable.create(emitter -> {
            Uri uri = MessengerContentProvider.getFaveLinksContentUriFor(accountId);

            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            if (clearBefore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

            for (FaveLinkEntity entity : entities) {
                ContentValues cv = new ContentValues();
                cv.put(FaveLinksColumns.LINK_ID, entity.getId());
                cv.put(FaveLinksColumns.URL, entity.getUrl());
                cv.put(FaveLinksColumns.TITLE, entity.getTitle());
                cv.put(FaveLinksColumns.DESCRIPTION, entity.getDescription());
                cv.put(FaveLinksColumns.PHOTO_50, entity.getPhoto50());
                cv.put(FaveLinksColumns.PHOTO_100, entity.getPhoto100());

                operations.add(ContentProviderOperation
                        .newInsert(uri)
                        .withValues(cv)
                        .build());
            }

            getContentResolver().applyBatch(MessengerContentProvider.AUTHORITY, operations);
            emitter.onComplete();
        });
    }

    private static ContentValues createCv(FaveGroupEntity dbo) {
        ContentValues cv = new ContentValues();
        cv.put(FaveGroupsColumns._ID, dbo.getId());
        cv.put(FaveGroupsColumns.NAME, dbo.getName());
        cv.put(FaveGroupsColumns.SCREEN_NAME, dbo.getScreenName());
        cv.put(FaveGroupsColumns.IS_CLOSED, dbo.getClosed());
        cv.put(FaveGroupsColumns.PHOTO_200, dbo.getPhoto200());
        cv.put(FaveGroupsColumns.PHOTO_100, dbo.getPhoto100());
        cv.put(FaveGroupsColumns.PHOTO_50, dbo.getPhoto50());
        cv.put(FaveGroupsColumns.UPDATED_TIME, dbo.getUpdateDate());
        cv.put(FaveGroupsColumns.DESCRIPTION, dbo.getDescription());
        cv.put(FaveGroupsColumns.FAVE_TYPE, dbo.getFaveType());
        return cv;
    }

    private static ContentValues createCv(FaveUserEntity dbo) {
        ContentValues cv = new ContentValues();
        cv.put(FaveUsersColumns._ID, dbo.getId());
        cv.put(FaveUsersColumns.DESCRIPTION, dbo.getDescription());
        cv.put(FaveUsersColumns.UPDATED_TIME, dbo.getUpdateDate());
        cv.put(FaveUsersColumns.FAVE_TYPE, dbo.getFaveType());
        cv.put(FaveUsersColumns.FOREIGN_USER_FIRST_NAME, dbo.getFirstName());
        cv.put(FaveUsersColumns.FOREIGN_USER_LAST_NAME, dbo.getLastName());
        cv.put(FaveUsersColumns.FOREIGN_USER_PHOTO_50, dbo.getPhoto50());
        cv.put(FaveUsersColumns.FOREIGN_USER_PHOTO_100, dbo.getPhoto100());
        cv.put(FaveUsersColumns.FOREIGN_USER_PHOTO_200, dbo.getPhoto200());
        cv.put(FaveUsersColumns.FOREIGN_USER_ONLINE, dbo.isOnline());
        cv.put(FaveUsersColumns.FOREIGN_USER_ONLINE_MOBILE, dbo.isOnlineMobile());

        return cv;
    }


    private static FaveUserEntity mapFaveUserDbo(Cursor cursor) {
        FaveUserEntity Ret = new FaveUserEntity(cursor.getInt(cursor.getColumnIndex(FaveUsersColumns._ID)));
        Ret.setDescription(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.DESCRIPTION)))
                .setUpdateDate(cursor.getLong(cursor.getColumnIndex(FaveUsersColumns.UPDATED_TIME)))
                .setFaveType(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FAVE_TYPE)))
                .setFirstName(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_FIRST_NAME)))
                .setLastName(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_LAST_NAME)))
                .setPhoto50(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_PHOTO_50)))
                .setPhoto100(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_PHOTO_100)))
                .setPhoto200(cursor.getString(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_PHOTO_200)))
                .setOnline(cursor.getInt(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_ONLINE)) == 1)
                .setOnlineMobile(cursor.getInt(cursor.getColumnIndex(FaveUsersColumns.FOREIGN_USER_ONLINE_MOBILE)) == 1);
        return Ret;
    }

    private static FaveGroupEntity mapFaveGroupDbo(Cursor cursor) {
        FaveGroupEntity Ret = new FaveGroupEntity(cursor.getColumnIndex(FaveGroupsColumns._ID));
        Ret.setDescription(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.DESCRIPTION)))
                .setUpdateDate(cursor.getLong(cursor.getColumnIndex(FaveGroupsColumns.UPDATED_TIME)))
                .setFaveType(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.FAVE_TYPE)))
                .setName(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.NAME)))
                .setScreenName(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.SCREEN_NAME)))
                .setClosed(cursor.getInt(cursor.getColumnIndex(FaveGroupsColumns.IS_CLOSED)))
                .setPhoto50(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.PHOTO_50)))
                .setPhoto100(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.PHOTO_100)))
                .setPhoto200(cursor.getString(cursor.getColumnIndex(FaveGroupsColumns.PHOTO_200)));
        return Ret;
    }

    @Override
    public Completable removeUser(int accountId, int userId) {
        return Completable.fromAction(() -> {
            final Uri uri = MessengerContentProvider.getFaveUsersContentUriFor(accountId);
            final String where = FaveUsersColumns._ID + " = ?";
            final String[] args = {String.valueOf(userId)};
            getContentResolver().delete(uri, where, args);
        });
    }

    @Override
    public Single<List<FaveUserEntity>> getFaveUsers(int accountId) {
        return Single.create(e -> {
            Uri uri = MessengerContentProvider.getFaveUsersContentUriFor(accountId);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);

            List<FaveUserEntity> dbos = new ArrayList<>(safeCountOf(cursor));
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    dbos.add(mapFaveUserDbo(cursor));
                }

                cursor.close();
            }

            e.onSuccess(dbos);
        });
    }

    @Override
    public Single<List<FaveGroupEntity>> getFaveGroups(int accountId) {
        return Single.create(e -> {
            Uri uri = MessengerContentProvider.getFaveGroupsContentUriFor(accountId);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);

            List<FaveGroupEntity> dbos = new ArrayList<>(safeCountOf(cursor));
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    dbos.add(mapFaveGroupDbo(cursor));
                }

                cursor.close();
            }

            e.onSuccess(dbos);
        });
    }

    @Override
    public Single<int[]> storePhotos(int accountId, List<PhotoEntity> photos, boolean clearBeforeStore) {
        return Single.create(e -> {
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            Uri uri = MessengerContentProvider.getFavePhotosContentUriFor(accountId);

            if (clearBeforeStore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

            // массив для хранения индексов операций вставки для каждого фото
            int[] indexes = new int[photos.size()];

            for (int i = 0; i < photos.size(); i++) {
                PhotoEntity dbo = photos.get(i);

                ContentValues cv = new ContentValues();
                cv.put(FavePhotosColumns.PHOTO_ID, dbo.getId());
                cv.put(FavePhotosColumns.OWNER_ID, dbo.getOwnerId());
                cv.put(FavePhotosColumns.POST_ID, dbo.getPostId());
                cv.put(FavePhotosColumns.PHOTO, GSON.toJson(dbo));

                int index = addToListAndReturnIndex(operations, ContentProviderOperation
                        .newInsert(uri)
                        .withValues(cv)
                        .build());

                indexes[i] = index;
            }

            ContentProviderResult[] results = getContentResolver()
                    .applyBatch(MessengerContentProvider.AUTHORITY, operations);

            final int[] ids = new int[results.length];

            for (int i = 0; i < indexes.length; i++) {
                int index = indexes[i];

                ContentProviderResult result = results[index];
                ids[i] = extractId(result);
            }

            e.onSuccess(ids);
        });
    }

    @Override
    public Single<List<PhotoEntity>> getPhotos(FavePhotosCriteria criteria) {
        return Single.create(e -> {
            String where;
            String[] args;

            Uri uri = MessengerContentProvider.getFavePhotosContentUriFor(criteria.getAccountId());
            DatabaseIdRange range = criteria.getRange();

            if (isNull(range)) {
                where = null;
                args = null;
            } else {
                where = FavePhotosColumns._ID + " >= ? AND " + FavePhotosColumns._ID + " <= ?";
                args = new String[]{String.valueOf(range.getFirst()), String.valueOf(range.getLast())};
            }

            Cursor cursor = getContentResolver().query(uri, null, where, args, null);

            List<PhotoEntity> dbos = new ArrayList<>(safeCountOf(cursor));
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    dbos.add(mapFavePhoto(cursor));
                }

                cursor.close();
            }

            e.onSuccess(dbos);
        });
    }

    @Override
    public Single<List<VideoEntity>> getVideos(FaveVideosCriteria criteria) {
        return Single.create(e -> {
            Uri uri = MessengerContentProvider.getFaveVideosContentUriFor(criteria.getAccountId());

            String where;
            String[] args;

            DatabaseIdRange range = criteria.getRange();
            if (nonNull(range)) {
                where = FaveVideosColumns._ID + " >= ? AND " + FaveVideosColumns._ID + " <= ?";
                args = new String[]{String.valueOf(range.getFirst()), String.valueOf(range.getLast())};
            } else {
                where = null;
                args = null;
            }

            Cursor cursor = getContentResolver().query(uri, null, where, args, null);

            List<VideoEntity> dbos = new ArrayList<>(safeCountOf(cursor));
            if (nonNull(cursor)) {
                while (cursor.moveToNext()) {
                    if (e.isDisposed()) {
                        break;
                    }

                    dbos.add(mapVideo(cursor));
                }

                cursor.close();
            }

            e.onSuccess(dbos);
        });
    }

    private VideoEntity mapVideo(Cursor cursor) {
        String json = cursor.getString(cursor.getColumnIndex(FaveVideosColumns.VIDEO));
        return GSON.fromJson(json, VideoEntity.class);
    }

    @Override
    public Single<int[]> storeVideos(int accountId, List<VideoEntity> videos, boolean clearBeforeStore) {
        return Single.create(e -> {
            Uri uri = MessengerContentProvider.getFaveVideosContentUriFor(accountId);
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();

            if (clearBeforeStore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

            int[] indexes = new int[videos.size()];

            for (int i = 0; i < videos.size(); i++) {
                VideoEntity dbo = videos.get(i);
                ContentValues cv = new ContentValues();
                cv.put(FaveVideosColumns.VIDEO, GSON.toJson(dbo));

                int index = addToListAndReturnIndex(operations, ContentProviderOperation
                        .newInsert(uri)
                        .withValues(cv)
                        .build());
                indexes[i] = index;
            }

            ContentProviderResult[] results = getContentResolver().applyBatch(MessengerContentProvider.AUTHORITY, operations);

            final int[] ids = new int[results.length];

            for (int i = 0; i < indexes.length; i++) {
                int index = indexes[i];

                ContentProviderResult result = results[index];
                ids[i] = extractId(result);
            }

            e.onSuccess(ids);
        });
    }

    @Override
    public Completable storeUsers(int accountId, List<FaveUserEntity> users, boolean clearBeforeStore) {
        return Completable.create(e -> {
            Uri uri = MessengerContentProvider.getFaveUsersContentUriFor(accountId);

            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            if (clearBeforeStore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

//            appendUsersInsertOperation(operations, accountId, users);

            for (FaveUserEntity dbo : users) {
                operations.add(ContentProviderOperation
                        .newInsert(uri)
                        .withValues(createCv(dbo))
                        .build());
            }

            if (!operations.isEmpty()) {
                getContentResolver().applyBatch(MessengerContentProvider.AUTHORITY, operations);
            }

            e.onComplete();
        });
    }

    @Override
    public Completable storeGroups(int accountId, List<FaveGroupEntity> groups, boolean clearBeforeStore) {
        return Completable.create(e -> {
            Uri uri = MessengerContentProvider.getFaveGroupsContentUriFor(accountId);

            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            if (clearBeforeStore) {
                operations.add(ContentProviderOperation
                        .newDelete(uri)
                        .build());
            }

//            appendCommunitiesInsertOperation(operations, accountId, groups);

            for (FaveGroupEntity dbo : groups) {
                operations.add(ContentProviderOperation
                        .newInsert(uri)
                        .withValues(createCv(dbo))
                        .build());
            }

            if (!operations.isEmpty()) {
                getContentResolver().applyBatch(MessengerContentProvider.AUTHORITY, operations);
            }

            e.onComplete();
        });
    }

    private static PhotoEntity mapFavePhoto(Cursor cursor) {
        String json = cursor.getString(cursor.getColumnIndex(FavePhotosColumns.PHOTO));
        return GSON.fromJson(json, PhotoEntity.class);
    }

    private FaveLinkEntity mapFaveLink(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(FaveLinksColumns.LINK_ID));
        String url = cursor.getString(cursor.getColumnIndex(FaveLinksColumns.URL));
        return new FaveLinkEntity(id, url)
                .setTitle(cursor.getString(cursor.getColumnIndex(FaveLinksColumns.TITLE)))
                .setDescription(cursor.getString(cursor.getColumnIndex(FaveLinksColumns.DESCRIPTION)))
                .setPhoto50(cursor.getString(cursor.getColumnIndex(FaveLinksColumns.PHOTO_50)))
                .setPhoto100(cursor.getString(cursor.getColumnIndex(FaveLinksColumns.PHOTO_100)));
    }

    private PostEntity mapFavePosts(Cursor cursor) {
        String json = cursor.getString(cursor.getColumnIndex(FavePostsColumns.POST));
        return GSON.fromJson(json, PostEntity.class);
    }
}
