/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package biz.dealnote.messenger.player;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import biz.dealnote.messenger.BuildConfig;
import biz.dealnote.messenger.Constants;
import biz.dealnote.messenger.Extra;
import biz.dealnote.messenger.Injection;
import biz.dealnote.messenger.R;
import biz.dealnote.messenger.api.HttpLogger;
import biz.dealnote.messenger.api.PicassoInstance;
import biz.dealnote.messenger.api.ProxyUtil;
import biz.dealnote.messenger.domain.IAudioInteractor;
import biz.dealnote.messenger.domain.InteractorFactory;
import biz.dealnote.messenger.media.exo.CustomHttpDataSourceFactory;
import biz.dealnote.messenger.media.exo.ExoEventAdapter;
import biz.dealnote.messenger.media.exo.ExoUtil;
import biz.dealnote.messenger.model.Audio;
import biz.dealnote.messenger.model.IdPair;
import biz.dealnote.messenger.util.Logger;
import biz.dealnote.messenger.util.PhoenixToast;
import biz.dealnote.messenger.util.RxUtils;
import biz.dealnote.messenger.util.Utils;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static biz.dealnote.messenger.util.Objects.nonNull;
import static biz.dealnote.messenger.util.Utils.firstNonEmptyString;
import static biz.dealnote.messenger.util.Utils.isEmpty;

public class MusicPlaybackService extends Service {

    private static final String TAG = "MusicPlaybackService";
    private static final boolean D = BuildConfig.DEBUG;

    public static final String PLAYSTATE_CHANGED = "biz.dealnote.phoenix.player.playstatechanged";
    public static final String POSITION_CHANGED = "biz.dealnote.phoenix.player.positionchanged";
    public static final String META_CHANGED = "biz.dealnote.phoenix.player.metachanged";
    public static final String PREPARED = "biz.dealnote.phoenix.player.prepared";
    public static final String REPEATMODE_CHANGED = "biz.dealnote.phoenix.player.repeatmodechanged";
    public static final String SHUFFLEMODE_CHANGED = "biz.dealnote.phoenix.player.shufflemodechanged";
    public static final String QUEUE_CHANGED = "biz.dealnote.phoenix.player.queuechanged";


    public static final String SERVICECMD = "biz.dealnote.phoenix.player.musicservicecommand";
    public static final String TOGGLEPAUSE_ACTION = "biz.dealnote.phoenix.player.togglepause";
    public static final String PAUSE_ACTION = "biz.dealnote.phoenix.player.pause";
    public static final String STOP_ACTION = "biz.dealnote.phoenix.player.stop";
    public static final String PREVIOUS_ACTION = "biz.dealnote.phoenix.player.previous";
    public static final String NEXT_ACTION = "biz.dealnote.phoenix.player.next";
    public static final String REPEAT_ACTION = "biz.dealnote.phoenix.player.repeat";
    public static final String SHUFFLE_ACTION = "biz.dealnote.phoenix.player.shuffle";

    /**
     * Called to update the service about the foreground state of Apollo's activities
     */
    public static final String FOREGROUND_STATE_CHANGED = "biz.dealnote.phoenix.player.fgstatechanged";
    public static final String NOW_IN_FOREGROUND = "nowinforeground";
    public static final String FROM_MEDIA_BUTTON = "frommediabutton";
    public static final String REFRESH = "biz.dealnote.phoenix.player.refresh";
    final String SHUTDOWN = "biz.dealnote.phoenix.player.shutdown";

    /**
     * Called to update the remote control client
     */
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String CMDPLAYLIST = "playlist";

    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE = 1;

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    private static final int TRACK_ENDED = 1;
    private static final int TRACK_WENT_TO_NEXT = 2;
    private static final int RELEASE_WAKELOCK = 3;
    private static final int SERVER_DIED = 4;
    private static final int FOCUSCHANGE = 5;
    private static final int FADEDOWN = 6;
    private static final int FADEUP = 7;

    private static final int IDLE_DELAY = 60000;
    private static final int MAX_HISTORY_SIZE = 100;
    private static final LinkedList<Integer> mHistory = new LinkedList<>();

    private static final Shuffler mShuffler = new Shuffler(MAX_HISTORY_SIZE);

    private final IBinder mBinder = new ServiceStub(this);
    private MultiPlayer mPlayer;
    private WakeLock mWakeLock;

    private AlarmManager mAlarmManager;
    private PendingIntent mShutdownIntent;
    private boolean mShutdownScheduled;

    private AudioManager mAudioManager;

    private boolean mIsSupposedToBePlaying = false;

    /**
     * Used to track what type of audio focus loss caused the playback to pause
     */
    private boolean mPausedByTransientLossOfFocus = false;

    private boolean mAnyActivityInForeground = false;

    private MediaSessionCompat mMediaSession;

    private MediaControllerCompat.TransportControls mTransportController;

    private int mPlayPos = -1;

    private String CoverAudio;
    private String CoverAlbom;

    private int mShuffleMode = SHUFFLE_NONE;

    private int mRepeatMode = REPEAT_NONE;

    private List<Audio> mPlayList = null;

    private MusicPlayerHandler mPlayerHandler;

    private NotificationHelper mNotificationHelper;

    private MediaMetadataCompat mMediaMetadataCompat;

    @Override
    public IBinder onBind(final Intent intent) {
        if (D) Logger.d(TAG, "Service bound, intent = " + intent);
        cancelShutdown();
        return mBinder;
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        if (D) Logger.d(TAG, "Service unbound");

        if (mIsSupposedToBePlaying || mPausedByTransientLossOfFocus || isPreparing()) {
            Logger.d(TAG, "onUnbind, mIsSupposedToBePlaying || mPausedByTransientLossOfFocus || isPreparing()");
            return true;


        } else if (Utils.safeIsEmpty(mPlayList) || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            scheduleDelayedShutdown();
            Logger.d(TAG, "onUnbind, scheduleDelayedShutdown");
            return true;
        }

        stopSelf();
        Logger.d(TAG, "onUnbind, stopSelf(mServiceStartId)");
        return true;
    }

    @Override
    public void onRebind(final Intent intent) {
        cancelShutdown();
    }

    @Override
    public void onCreate() {
        if (D) Logger.d(TAG, "Creating service");
        super.onCreate();

        mNotificationHelper = new NotificationHelper(this);

        final HandlerThread thread = new HandlerThread("MusicPlayerHandler", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mPlayerHandler = new MusicPlayerHandler(this, thread.getLooper());

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        setUpRemoteControlClient();

        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mPlayerHandler);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICECMD);
        filter.addAction(TOGGLEPAUSE_ACTION);
        filter.addAction(PAUSE_ACTION);
        filter.addAction(STOP_ACTION);
        filter.addAction(NEXT_ACTION);
        filter.addAction(PREVIOUS_ACTION);
        filter.addAction(REPEAT_ACTION);
        filter.addAction(SHUFFLE_ACTION);

        registerReceiver(mIntentReceiver, filter);

        final PowerManager powerManager = (PowerManager) Objects.requireNonNull(getSystemService(Context.POWER_SERVICE));
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        mWakeLock.setReferenceCounted(false);

        // Initialize the delayed shutdown intent
        final Intent shutdownIntent = new Intent(this, MusicPlaybackService.class);
        shutdownIntent.setAction(SHUTDOWN);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);

        // Listen for the idle state
        scheduleDelayedShutdown();

        notifyChange(META_CHANGED);
    }

    private void setUpRemoteControlClient() {
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        mMediaSession = new MediaSessionCompat(getApplication(), "TAG", null, null);
        PlaybackStateCompat playbackStateCompat = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_STOP
                )
                .setState(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, position(), 1.0f)
                .build();
        mMediaSession.setPlaybackState(playbackStateCompat);
        mMediaSession.setCallback(mMediaSessionCallback);
        mMediaSession.setActive(true);
        updateRemoteControlClient(META_CHANGED);
        mTransportController = mMediaSession.getController().getTransportControls();
    }

    private final MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() {
            super.onPlay();
            play();
        }

        @Override
        public void onPause() {
            super.onPause();
            pause();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            gotoNext(true);
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            prev();
        }

        @Override
        public void onStop() {
            super.onStop();
            pause();
            Logger.d(getClass().getSimpleName(), "Stopping services. onStop()");
            stopSelf();
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
            seek(pos);
        }
    };

    @Override
    public void onDestroy() {
        if (D) Logger.d(TAG, "Destroying service");
        super.onDestroy();

        final Intent audioEffectsIntent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(audioEffectsIntent);

        mAlarmManager.cancel(mShutdownIntent);

        mPlayerHandler.removeCallbacksAndMessages(null);

        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        mMediaSession.release();

        mPlayerHandler.removeCallbacksAndMessages(null);

        unregisterReceiver(mIntentReceiver);

        mWakeLock.release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (D) Logger.d(TAG, "Got new intent " + intent + ", startId = " + startId);

        if (intent != null) {
            final String action = intent.getAction();

            if (intent.hasExtra(NOW_IN_FOREGROUND)) {
                mAnyActivityInForeground = intent.getBooleanExtra(NOW_IN_FOREGROUND, false);
                updateNotification(null);
            }

            if (SHUTDOWN.equals(action)) {
                mShutdownScheduled = false;
                releaseServiceUiAndStop();
                return START_NOT_STICKY;
            }

            handleCommandIntent(intent);
            MediaButtonReceiver.handleIntent(mMediaSession, intent);
        }

        scheduleDelayedShutdown();
        return START_STICKY;
    }

    private void releaseServiceUiAndStop() {
        if (isPlaying() || mPausedByTransientLossOfFocus || mPlayerHandler.hasMessages(TRACK_ENDED)) {
            return;
        }

        if (D) Logger.d(TAG, "Nothing is playing anymore, releasing notification");

        mNotificationHelper.killNotification();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        if (!mAnyActivityInForeground) {
            stopSelf();
        }
    }

    private void handleCommandIntent(Intent intent) {
        final String action = intent.getAction();
        final String command = SERVICECMD.equals(action) ? intent.getStringExtra(CMDNAME) : null;

        if (D) Logger.d(TAG, "handleCommandIntent: action = " + action + ", command = " + command);

        if (CMDNEXT.equals(command) || NEXT_ACTION.equals(action)) {
            mTransportController.skipToNext();
        }
        if (CMDPREVIOUS.equals(command) || PREVIOUS_ACTION.equals(action)) {
            mTransportController.skipToPrevious();
        }

        if (CMDTOGGLEPAUSE.equals(command) || TOGGLEPAUSE_ACTION.equals(action)) {
            if (isPlaying()) {
                mTransportController.pause();
                mPausedByTransientLossOfFocus = false;
            } else {
                mTransportController.play();
            }
        }

        if (CMDPAUSE.equals(command) || PAUSE_ACTION.equals(action)) {
            mTransportController.pause();
            mPausedByTransientLossOfFocus = false;
        }

        if (CMDPLAY.equals(command)) {
            play();
        }
        if (CMDSTOP.equals(command) || STOP_ACTION.equals(action)) {
            mTransportController.pause();
            mPausedByTransientLossOfFocus = false;
            seek(0);
            releaseServiceUiAndStop();
        }

        if (REPEAT_ACTION.equals(action)) {
            cycleRepeat();
        }

        if (SHUFFLE_ACTION.equals(action)) {
            cycleShuffle();
        }

        if (CMDPLAYLIST.equals(action)) {
            ArrayList<Audio> apiAudios = intent.getParcelableArrayListExtra(Extra.AUDIOS);
            int position = intent.getIntExtra(Extra.POSITION, 0);
            int forceShuffle = intent.getIntExtra(Extra.SHUFFLE_MODE, SHUFFLE_NONE);
            setShuffleMode(forceShuffle);
            open(apiAudios, position);
        }
    }

    public static final int MAX_QUEUE_SIZE = 200;

    private static List<IdPair> listToIdPair(ArrayList<Audio> audios) {
        List<IdPair> result = new ArrayList<>();
        for (Audio item : audios) {
            result.add(new IdPair(item.getId(), item.getOwnerId()));
        }
        return result;
    }

    public static void startForPlayList(Context context, @NonNull ArrayList<Audio> audios, int position, boolean forceShuffle) {
        String url = audios.get(0).getUrl();
        IAudioInteractor interactor = InteractorFactory.createAudioInteractor();

        if (interactor.isAudioPluginAvailable() && (isEmpty(url) || "https://vk.com/mp3/audio_api_unavailable.mp3".equals(url))) {
            audios = (ArrayList<Audio>) interactor
                    .getById(listToIdPair(audios))
                    .subscribeOn(Schedulers.io())
                    .blockingGet();
        }

        Logger.d(TAG, "startForPlayList, count: " + audios.size() + ", position: " + position);

        ArrayList<Audio> target;
        int targetPosition;

        if (audios.size() <= MAX_QUEUE_SIZE) {
            target = audios;
            targetPosition = position;
        } else {
            target = new ArrayList<>(MusicPlaybackService.MAX_QUEUE_SIZE);
            int half = MusicPlaybackService.MAX_QUEUE_SIZE / 2;

            int startAt = position - half;
            if (startAt < 0) {
                startAt = 0;
            }

            targetPosition = position - startAt;
            for (int i = startAt; target.size() < MusicPlaybackService.MAX_QUEUE_SIZE; i++) {
                if (i > audios.size() - 1) {
                    break;
                }

                target.add(audios.get(i));
            }

            if (target.size() < MusicPlaybackService.MAX_QUEUE_SIZE) {
                for (int i = startAt - 1; target.size() < MusicPlaybackService.MAX_QUEUE_SIZE; i--) {
                    target.add(0, audios.get(i));
                    targetPosition++;
                }
            }
        }

        Intent intent = new Intent(context, MusicPlaybackService.class);
        intent.setAction(CMDPLAYLIST);
        intent.putParcelableArrayListExtra(Extra.AUDIOS, target);
        intent.putExtra(Extra.POSITION, targetPosition);
        intent.putExtra(Extra.SHUFFLE_MODE, forceShuffle ? SHUFFLE : SHUFFLE_NONE);
        context.startService(intent);
    }

    /**
     * Updates the notification, considering the current play and activity state
     */
    private void updateNotification(Bitmap cover) {
        mNotificationHelper.buildNotification(getApplicationContext(), getArtistName(),
                getTrackName(), isPlaying(), cover, mMediaSession.getSessionToken());
    }

    private void scheduleDelayedShutdown() {
        if (D) Log.v(TAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
        mShutdownScheduled = true;
    }

    private void cancelShutdown() {
        if (D) Logger.d(TAG, "Cancelling delayed shutdown, scheduled = " + mShutdownScheduled);
        if (mShutdownScheduled) {
            mAlarmManager.cancel(mShutdownIntent);
            mShutdownScheduled = false;
        }
    }

    /**
     * Stops playback
     *
     * @param goToIdle True to go to the idle state, false otherwise
     */
    private void stop(final boolean goToIdle) {
        if (D) Logger.d(TAG, "Stopping playback, goToIdle = " + goToIdle);

        if (mPlayer != null && mPlayer.isInitialized()) {
            mPlayer.stop();
        }

        if (goToIdle) {
            scheduleDelayedShutdown();
            mIsSupposedToBePlaying = false;
        } else {
            stopForeground(false); //надо подумать
        }
    }

    private boolean isInitialized() {
        return mPlayer != null && mPlayer.isInitialized();
    }

    private boolean isPreparing() {
        return mPlayer != null && mPlayer.isPreparing();
    }

    /**
     * Called to open a new file as the current track and prepare the next for
     * playback
     */
    private void playCurrentTrack(boolean UpdateMeta) {
        synchronized (this) {
            Logger.d(TAG, "playCurrentTrack, mPlayListLen: " + Utils.safeCountOf(mPlayList));

            if (Utils.safeIsEmpty(mPlayList)) {
                return;
            }

            stop(Boolean.FALSE);

            Audio current = mPlayList.get(mPlayPos);
            openFile(current, UpdateMeta);
        }
    }

    /**
     * @param force True to force the player onto the track next, false
     *              otherwise.
     * @return The next position to play.
     */
    private int getNextPosition(final boolean force) {
        if (!force && mRepeatMode == REPEAT_CURRENT) {
            return Math.max(mPlayPos, 0);
        }

        if (mShuffleMode == SHUFFLE) {
            if (mPlayPos >= 0) {
                mHistory.add(mPlayPos);
            }

            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.remove(0);
            }

            Stack<Integer> notPlayedTracksPositions = new Stack<>();
            boolean allWerePlayed = mPlayList.size() - mHistory.size() == 0;
            if (!allWerePlayed) {
                for (int i = 0; i < mPlayList.size(); i++) {
                    if (!mHistory.contains(i)) {
                        notPlayedTracksPositions.push(i);
                    }
                }
            } else {
                for (int i = 0; i < mPlayList.size(); i++) {
                    notPlayedTracksPositions.push(i);
                }
                mHistory.clear();
            }
            return notPlayedTracksPositions.get(mShuffler.nextInt(notPlayedTracksPositions.size()));
        }

        if (mPlayPos >= Utils.safeCountOf(mPlayList) - 1) {
            if (mRepeatMode == REPEAT_NONE && !force) {
                return -1;
            }
            if (mRepeatMode == REPEAT_ALL || force) {
                return 0;
            }
            return -1;
        } else {
            return mPlayPos + 1;
        }
    }

    /**
     * Notify the change-receivers that something has changed.
     */
    private void notifyChange(final String what) {
        if (D) Logger.d(TAG, "notifyChange: what = " + what);

        updateRemoteControlClient(what);

        if (what.equals(POSITION_CHANGED)) {
            return;
        }

        final Intent intent = new Intent(what);
        intent.putExtra("id", getCurrentTrack());
        intent.putExtra("artist", getArtistName());
        intent.putExtra("album", getAlbumName());
        intent.putExtra("track", getTrackName());
        intent.putExtra("playing", isPlaying());
        sendStickyBroadcast(intent);

        if (what.equals(PLAYSTATE_CHANGED)) {
            mNotificationHelper.updatePlayState(isPlaying());
        }
    }

    /**
     * Updates the lockscreen controls.
     *
     * @param what The broadcast
     */
    private void updateRemoteControlClient(final String what) {
        switch (what) {
            case PLAYSTATE_CHANGED:
            case POSITION_CHANGED:
                int playState = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                PlaybackStateCompat pmc = new PlaybackStateCompat.Builder()
                        .setState(playState, position(), 1.0f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SEEK_TO)
                        .build();
                mMediaSession.setPlaybackState(pmc);
                break;
            case META_CHANGED:
                fetchCoverAndUpdateMetadata();
                break;
        }
    }

    private void fetchCoverAndUpdateMetadata() {
        if (getAlbumCover() == null || getAlbumCover().isEmpty()) {
            updateMetadata(null);
            return;
        }

        PicassoInstance.with()
                .load(getAlbumCoverBig())
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        updateMetadata(bitmap);
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        updateMetadata(null);
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                    }
                });
    }

    private void updateMetadata(Bitmap cover) {
        updateNotification(cover);
        mMediaMetadataCompat = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getArtistName())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, CoverAlbom == null ? getAlbumName() : CoverAlbom)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getTrackName())
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration())
                .build();
        mMediaSession.setMetadata(mMediaMetadataCompat);
    }
    public void GetCoverURL(Audio audio) throws Exception {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(HttpLogger.DEFAULT_LOGGING_INTERCEPTOR).addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request().newBuilder().addHeader("User-Agent", Constants.USER_AGENT(null)).build();
                        return chain.proceed(request);
                    }});
        ProxyUtil.applyProxyConfig(builder, Injection.provideProxySettings().getActiveProxy());
        Request request = new Request.Builder()
                .url("https://axzodu785h.execute-api.us-east-1.amazonaws.com/dev?track=" + URLEncoder.encode(audio.getTitle(), "UTF-8") + "&artist=" + URLEncoder.encode(audio.getArtist(), "UTF-8")).build();

        builder.build().newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call th, IOException e) {
                e.printStackTrace();
            }

            @Override public void onResponse(Call th, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject obj = new JSONObject(response.body().string());
                        if(obj.has("image"))
                            CoverAudio = obj.getString("image");
                        if(obj.has("album"))
                            CoverAlbom = obj.getString("album");

                        Handler uiHandler = new Handler(MusicPlaybackService.this.getMainLooper());
                        uiHandler.post(() -> {
                            fetchCoverAndUpdateMetadata();
                            notifyChange(META_CHANGED);
                        });

                    } catch (JSONException e) {
                        e.printStackTrace();
                    };
                }
            }
        });
    }
    /**
     * Opens a file and prepares it for playback
     *
     * @param audio The path of the file to open
     */
    public void openFile(final Audio audio, boolean UpdateMeta) {
        synchronized (this) {
            if (audio == null) {
                stop(Boolean.TRUE);
                return;
            }
            if(UpdateMeta) {
                CoverAudio = null;
                CoverAlbom = null;
            }
            mPlayer.setDataSource(audio.getOwnerId(), audio.getId(), audio.getUrl());
            if(UpdateMeta) {
                try {
                    GetCoverURL(audio);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns the audio session ID
     *
     * @return The current media player audio session ID
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /**
     * Returns the audio session ID
     *
     * @return The current media player audio session ID
     */
    public int getBufferPercent() {
        synchronized (this) {
            return mPlayer.getBufferPercent();
        }
    }

    public int getShuffleMode() {
        return mShuffleMode;
    }

    public int getRepeatMode() {
        return mRepeatMode;
    }

    public int getQueuePosition() {
        synchronized (this) {
            return mPlayPos;
        }
    }

    public String getPath() {
        synchronized (this) {
            Audio apiAudio = getCurrentTrack();
            if (apiAudio == null) {
                return null;
            }

            return apiAudio.getUrl();
        }
    }

    public String getAlbumName() {
        synchronized (this) {
            if (getCurrentTrack() == null) {
                return null;
            }
            return String.valueOf(getCurrentTrack().getAlbumId());
        }
    }

    /**
     * Returns the album cover
     *
     * @return url
     */
    public String getAlbumCoverBig() {
        synchronized (this) {
            if (getCurrentTrack() == null) {
                return null;
            }

            return getAlbumCover();
        }
    }

    public String getAlbumCover() {
        synchronized (this) {
            if (getCurrentTrack() == null) {
                return null;
            }

            return CoverAudio;
        }
    }

    public String getTrackName() {
        synchronized (this) {
            Audio current = getCurrentTrack();
            if (current == null) {
                return null;
            }

            return current.getTitle();
        }
    }

    public String getArtistName() {
        synchronized (this) {
            Audio current = getCurrentTrack();
            if (current == null) {
                return null;
            }

            return current.getArtist();
        }
    }

    public Audio getCurrentTrack() {
        synchronized (this) {
            if (mPlayPos >= 0) {
                return mPlayList.get(mPlayPos);
            }
        }

        return null;
    }

    public long seek(long position) {
        if (mPlayer != null && mPlayer.isInitialized()) {
            if (position < 0) {
                position = 0;
            } else if (position > mPlayer.duration()) {
                position = mPlayer.duration();
            }

            long result = mPlayer.seek(position);
            notifyChange(POSITION_CHANGED);
            return result;
        }

        return -1;
    }

    public long position() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    public long duration() {
        if (mPlayer != null && mPlayer.isInitialized()) {
            return mPlayer.duration();
        }

        return -1;
    }

    public List<Audio> getQueue() {
        synchronized (this) {
            final int len = Utils.safeCountOf(mPlayList);
            final List<Audio> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                list.add(i, mPlayList.get(i));
            }

            return list;
        }
    }

    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    /**
     * Opens a list for playback
     *
     * @param list     The list of tracks to open
     * @param position The position to start playback at
     */
    public void open(@NonNull final List<Audio> list, final int position) {
        synchronized (this) {
            final Audio oldAudio = getCurrentTrack();

            mPlayList = list;

            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mShuffler.nextInt(Utils.safeCountOf(mPlayList));
            }

            mHistory.clear();

            playCurrentTrack(true);

            notifyChange(QUEUE_CHANGED);
            if (oldAudio != getCurrentTrack()) {
                notifyChange(META_CHANGED);
            }
        }
    }


    public void stop() {
        stop(true);
    }


    public void play() {
        int status = mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (D) {
            Logger.d(TAG, "Starting playback: audio focus request status = " + status);
        }

        if (status != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }

        if (mPlayer != null && mPlayer.isInitialized()) {

            final long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000 && mPlayer.position() >= duration - 2000) {
                gotoNext(false);
            }

            mPlayer.start();
            mPlayerHandler.removeMessages(FADEDOWN);
            mPlayerHandler.sendEmptyMessage(FADEUP);

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

            cancelShutdown();
            fetchCoverAndUpdateMetadata();
        }
    }

    /**
     * Temporarily pauses playback.
     */
    public void pause() {
        if (D) Logger.d(TAG, "Pausing playback");
        synchronized (this) {
            mPlayerHandler.removeMessages(FADEUP);
            if (mPlayer != null && mIsSupposedToBePlaying) {
                mPlayer.pause();
                scheduleDelayedShutdown();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
    }

    /**
     * Changes from the current track to the next track
     */
    public void gotoNext(final boolean force) {
        if (D) Logger.d(TAG, "Going to next track");

        synchronized (this) {
            if (Utils.safeCountOf(mPlayList) <= 0) {
                if (D) Logger.d(TAG, "No play queue");

                scheduleDelayedShutdown();
                return;
            }

            final int pos = getNextPosition(force);
            Logger.d(TAG, String.valueOf(pos));

            if (pos < 0) {
                pause();
                scheduleDelayedShutdown();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }

                return;
            }

            mPlayPos = pos;
            stop(false);
            mPlayPos = pos;

            playCurrentTrack(true);

            notifyChange(META_CHANGED);
        }
    }

    /**
     * Changes from the current track to the previous played track
     */
    public void prev() {
        if (D) Logger.d(TAG, "Going to previous track");

        synchronized (this) {
            if (mShuffleMode == SHUFFLE) {
                // Go to previously-played track and remove it from the history
                final int histsize = mHistory.size();
                if (histsize == 0) {
                    return;
                }

                mPlayPos = mHistory.remove(histsize - 1);
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = Utils.safeCountOf(mPlayList) - 1;
                }
            }

            stop(false);
            playCurrentTrack(true);

            notifyChange(META_CHANGED);
        }
    }

    public void setRepeatMode(final int repeatmode) {
        synchronized (this) {
            mRepeatMode = repeatmode;
            notifyChange(REPEATMODE_CHANGED);
        }
    }

    public void setShuffleMode(final int shufflemode) {
        synchronized (this) {
            if (mShuffleMode == shufflemode && Utils.safeCountOf(mPlayList) > 0) {
                return;
            }

            mShuffleMode = shufflemode;
            notifyChange(SHUFFLEMODE_CHANGED);
        }
    }

    private void cycleRepeat() {
        switch (mRepeatMode) {
            case REPEAT_NONE:
                setRepeatMode(REPEAT_ALL);
                break;
            case REPEAT_ALL:
                setRepeatMode(REPEAT_CURRENT);
                if (mShuffleMode != SHUFFLE_NONE) {
                    setShuffleMode(SHUFFLE_NONE);
                }
                break;
            default:
                setRepeatMode(REPEAT_NONE);
                break;
        }
    }

    private void cycleShuffle() {
        switch (mShuffleMode) {
            case SHUFFLE:
                setShuffleMode(SHUFFLE_NONE);
                break;
            case SHUFFLE_NONE:
                setShuffleMode(SHUFFLE);
                if (mRepeatMode == REPEAT_CURRENT) {
                    setRepeatMode(REPEAT_ALL);
                }
                break;
        }
    }

    /**
     * Called when one of the lists should refresh or requery.
     */
    public void refresh() {
        notifyChange(REFRESH);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            handleCommandIntent(intent);
        }
    };

    private final OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(final int focusChange) {
            mPlayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    private static final class MusicPlayerHandler extends Handler {

        private final WeakReference<MusicPlaybackService> mService;
        private float mCurrentVolume = 1.0f;

        /**
         * Constructor of <code>MusicPlayerHandler</code>
         *
         * @param service The service to use.
         * @param looper  The thread to run on.
         */
        MusicPlayerHandler(final MusicPlaybackService service, final Looper looper) {
            super(looper);
            mService = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(final Message msg) {
            final MusicPlaybackService service = mService.get();
            if (service == null) {
                return;
            }

            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }

                    service.mPlayer.setVolume(mCurrentVolume);
                    break;
                case SERVER_DIED:
                    if (service.isPlaying()) {
                        service.gotoNext(true);
                    } else {
                        service.playCurrentTrack(false);
                    }
                    break;
                case TRACK_WENT_TO_NEXT:
                    //service.mPlayPos = service.mNextPlayPos;
                    service.notifyChange(META_CHANGED);
                    service.updateNotification(null);
                    break;
                case TRACK_ENDED:
                    if (service.mRepeatMode == REPEAT_CURRENT) {
                        service.seek(0);
                        service.play();
                    } else {
                        service.gotoNext(false);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    service.mWakeLock.release();
                    break;
                case FOCUSCHANGE:
                    if (D) Logger.d(TAG, "Received audio focus change event " + msg.arg1);
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            if (service.isPlaying()) {
                                service.mPausedByTransientLossOfFocus =
                                        msg.arg1 == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                            }
                            service.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            removeMessages(FADEUP);
                            sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (!service.isPlaying() && service.mPausedByTransientLossOfFocus) {
                                service.mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                service.mPlayer.setVolume(mCurrentVolume);
                                service.play();
                            } else {
                                removeMessages(FADEDOWN);
                                sendEmptyMessage(FADEUP);
                            }
                            break;
                        default:
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private static final class MultiPlayer{

        final WeakReference<MusicPlaybackService> mService;

        SimpleExoPlayer mCurrentMediaPlayer;

        Handler mHandler;

        boolean mIsInitialized;

        boolean preparing;

        final IAudioInteractor audioInteractor;

        final CompositeDisposable compositeDisposable = new CompositeDisposable();

        /**
         * Constructor of <code>MultiPlayer</code>
         */
        MultiPlayer(final MusicPlaybackService service) {
            mService = new WeakReference<>(service);
            audioInteractor = InteractorFactory.createAudioInteractor();
            mCurrentMediaPlayer = new SimpleExoPlayer.Builder(Injection.provideApplicationContext()).build();
            //mCurrentMediaPlayer.setWakeMode(mService.get(), PowerManager.PARTIAL_WAKE_LOCK);
        }

        /**
         * @param remoteUrl The path of the file, or the http/rtsp URL of the stream
         *                  you want to play
         *                  return True if the <code>player</code> has been prepared and is
         *                  ready to play, false otherwise
         */
        void setDataSource(final String remoteUrl) {
            preparing = true;
            final String url = firstNonEmptyString(remoteUrl, "https://vk.com/mp3/audio_api_unavailable.mp3");

            Proxy proxy = null;
            if (nonNull(Injection.provideProxySettings().getActiveProxy())) {
                proxy = new Proxy(Proxy.Type.HTTP, ProxyUtil.obtainAddress(Injection.provideProxySettings().getActiveProxy()));
                if (Injection.provideProxySettings().getActiveProxy().isAuthEnabled()) {
                    Authenticator authenticator = new Authenticator() {
                        public PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(Injection.provideProxySettings().getActiveProxy().getUser(), Injection.provideProxySettings().getActiveProxy().getPass().toCharArray());
                        }
                    };

                    Authenticator.setDefault(authenticator);
                } else {
                    Authenticator.setDefault(null);
                }
            }

            String userAgent = Constants.USER_AGENT(null);
            CustomHttpDataSourceFactory factory = new CustomHttpDataSourceFactory(userAgent, proxy);
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            MediaSource mediaSource = new ExtractorMediaSource.Factory(factory).setExtractorsFactory(extractorsFactory).createMediaSource(Uri.parse(url));
            mCurrentMediaPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);

            mCurrentMediaPlayer.addListener(new ExoEventAdapter() {
                @Override
                public void onPlayerStateChanged(boolean b, int i) {

                    switch (i){
                        case Player.STATE_READY:
                            if(preparing) {
                                preparing = false;
                                mIsInitialized = true;
                                mService.get().notifyChange(PREPARED);
                                mService.get().play();
                            }
                            break;
                        case Player.STATE_ENDED:
                            mService.get().gotoNext(false);
                            break;
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    long playbackPos = mCurrentMediaPlayer.getCurrentPosition();
                    mService.get().playCurrentTrack(false);
                    mCurrentMediaPlayer.seekTo(playbackPos);
                    mService.get().notifyChange(META_CHANGED);
                }
            });
            mCurrentMediaPlayer.prepare(mediaSource);

            final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
            intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mService.get().getPackageName());
            mService.get().sendBroadcast(intent);
            mService.get().notifyChange(PLAYSTATE_CHANGED);
        }

        void setDataSource(int ownerId, int audioId, String url) {
            if (isEmpty(url) || "https://vk.com/mp3/audio_api_unavailable.mp3".equals(url)) {

                compositeDisposable.add(audioInteractor.getById(Collections.singletonList(new IdPair(audioId, ownerId)))
                        .compose(RxUtils.applySingleIOToMainSchedulers())
                        .map(e -> e.get(0).getUrl())
                        .subscribe(this::setDataSource, ignored -> setDataSource(url)));
            } else {
                setDataSource(url);
            }
        }

        /**
         * Sets the handler
         *
         * @param handler The handler to use
         */
        public void setHandler(final Handler handler) {
            mHandler = handler;
        }

        boolean isInitialized() {
            return mIsInitialized;
        }

        boolean isPreparing() {
            return preparing;
        }

        public void start() {
            ExoUtil.startPlayer(mCurrentMediaPlayer);
        }

        public void stop() {
            mIsInitialized = false;
            preparing = false;
            mCurrentMediaPlayer.stop(true);
        }

        public void release() {
            stop();
            mCurrentMediaPlayer.release();
            compositeDisposable.dispose();
        }

        public void pause() {
            ExoUtil.pausePlayer(mCurrentMediaPlayer);
        }

        public long duration() {
            return mCurrentMediaPlayer.getDuration();
        }

        public long position() {
            return mCurrentMediaPlayer.getCurrentPosition();
        }

        long seek(final long whereto) {
            mCurrentMediaPlayer.seekTo((int) whereto);
            return whereto;
        }

        void setVolume(final float vol) {
            try {
                mCurrentMediaPlayer.setVolume(vol);
            } catch (IllegalStateException ignored) {
                // случается
            }
        }

        int getAudioSessionId() {
            return mCurrentMediaPlayer.getAudioSessionId();
        }

        int getBufferPercent() {
            return mCurrentMediaPlayer.getBufferedPercentage();
        }
    }

    private static final class ServiceStub extends IAudioPlayerService.Stub {

        private final WeakReference<MusicPlaybackService> mService;

        private ServiceStub(final MusicPlaybackService service) {
            mService = new WeakReference<>(service);
        }

        @Override
        public void openFile(final Audio audio) {
            mService.get().openFile(audio, true);
        }

        @Override
        public void open(final List<Audio> list, final int position) {
            mService.get().open(list, position);
        }

        @Override
        public void stop() {
            mService.get().stop();
        }

        @Override
        public void pause() {
            mService.get().pause();
        }

        @Override
        public void play() {
            mService.get().play();
        }

        @Override
        public void prev() {
            mService.get().prev();
        }

        @Override
        public void next() {
            mService.get().gotoNext(true);
        }

        @Override
        public void setShuffleMode(final int shufflemode) {
            mService.get().setShuffleMode(shufflemode);
        }

        @Override
        public void setRepeatMode(final int repeatmode) {
            mService.get().setRepeatMode(repeatmode);
        }

        @Override
        public void refresh() {
            mService.get().refresh();
        }

        @Override
        public boolean isPlaying() {
            return mService.get().isPlaying();
        }

        @Override
        public boolean isPreparing() {
            return mService.get().isPreparing();
        }

        @Override
        public boolean isInitialized() {
            return mService.get().isInitialized();
        }

        @Override
        public List<Audio> getQueue() {
            return mService.get().getQueue();
        }

        @Override
        public long duration() {
            return mService.get().duration();
        }

        @Override
        public long position() {
            return mService.get().position();
        }

        @Override
        public long seek(final long position) {
            return mService.get().seek(position);
        }

        @Override
        public Audio getCurrentAudio() {
            return mService.get().getCurrentTrack();
        }

        @Override
        public String getArtistName() {
            return mService.get().getArtistName();
        }

        @Override
        public String getTrackName() {
            return mService.get().getTrackName();
        }

        @Override
        public String getAlbumName() {
            return mService.get().getAlbumName();
        }

        @Override
        public String getAlbumCover() {
            return mService.get().getAlbumCoverBig();
        }

        @Override
        public String getPath() {
            return mService.get().getPath();
        }

        @Override
        public int getQueuePosition() {
            return mService.get().getQueuePosition();
        }

        @Override
        public int getShuffleMode() {
            return mService.get().getShuffleMode();
        }

        @Override
        public int getRepeatMode() {
            return mService.get().getRepeatMode();
        }

        @Override
        public int getAudioSessionId() {
            return mService.get().getAudioSessionId();
        }

        @Override
        public int getBufferPercent() {
            return mService.get().getBufferPercent();
        }
    }
}
