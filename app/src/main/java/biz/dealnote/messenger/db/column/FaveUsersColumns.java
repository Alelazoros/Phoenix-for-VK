package biz.dealnote.messenger.db.column;

import android.content.ContentValues;
import android.provider.BaseColumns;

public final class FaveUsersColumns implements BaseColumns {

    private FaveUsersColumns(){}

    public static final String TABLENAME = "fave_users";

    public static final String FULL_ID = TABLENAME + "." + _ID;

    public static final String FOREIGN_USER_FIRST_NAME = "user_first_name";
    public static final String FOREIGN_USER_LAST_NAME = "user_last_name";

    public static final String FOREIGN_USER_PHOTO_50 = "user_photo_50";
    public static final String FOREIGN_USER_PHOTO_100 = "user_photo_100";
    public static final String FOREIGN_USER_PHOTO_200 = "user_photo_200";

    public static final String DESCRIPTION = "description";
    public static final String UPDATED_TIME = "updated_time";
    public static final String FAVE_TYPE = "fave_type";

    public static final String FOREIGN_USER_ONLINE = "user_online";
    public static final String FOREIGN_USER_ONLINE_MOBILE = "user_online_mobile";

}