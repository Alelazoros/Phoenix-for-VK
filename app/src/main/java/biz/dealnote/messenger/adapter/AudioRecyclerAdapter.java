package biz.dealnote.messenger.adapter;

import android.app.Activity;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;

import biz.dealnote.messenger.Injection;
import biz.dealnote.messenger.R;
import biz.dealnote.messenger.activity.MainActivity;
import biz.dealnote.messenger.domain.IAudioInteractor;
import biz.dealnote.messenger.domain.InteractorFactory;
import biz.dealnote.messenger.model.Audio;
import biz.dealnote.messenger.player.util.MusicUtils;
import biz.dealnote.messenger.settings.Settings;
import biz.dealnote.messenger.util.AppPerms;
import biz.dealnote.messenger.util.AppTextUtils;
import biz.dealnote.messenger.util.DownloadUtil;
import biz.dealnote.messenger.util.PhoenixToast;
import biz.dealnote.messenger.util.RxUtils;
import io.reactivex.disposables.CompositeDisposable;

public class AudioRecyclerAdapter extends RecyclerView.Adapter<AudioRecyclerAdapter.AudioHolder>{

    private Context mContext;
    private List<Audio> mData;
    private IAudioInteractor mAudioInteractor;
    private CompositeDisposable audioListDisposable = new CompositeDisposable();

    public AudioRecyclerAdapter(Context context, List<Audio> data) {
        this.mAudioInteractor = InteractorFactory.createAudioInteractor();
        this.mContext = context;
        this.mData = data;
    }

    private void delete(final int accoutnId, Audio audio) {
        audioListDisposable.add(mAudioInteractor.delete(accoutnId, audio.getId(), audio.getOwnerId()).compose(RxUtils.applyCompletableIOToMainSchedulers()).subscribe(() -> {}, ignore -> {}));
    }

    private void add(int accountId, Audio audio) {
        audioListDisposable.add(mAudioInteractor.add(accountId, audio, null, null).compose(RxUtils.applySingleIOToMainSchedulers()).subscribe(t -> {}, ignore -> {}));
    }

    @Override
    public AudioHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new AudioHolder(LayoutInflater.from(mContext).inflate(R.layout.item_audio, parent, false));
    }

    @Override
    public void onBindViewHolder(final AudioHolder holder, int position) {
        final Audio item = mData.get(position);

        holder.artist.setText(item.getArtist());
        holder.title.setText(item.getTitle());
        holder.time.setText(AppTextUtils.getDurationString(item.getDuration()));

        holder.saved.setVisibility(DownloadUtil.TrackIsDownloaded(item) ? View.VISIBLE : View.INVISIBLE);

        holder.play.setImageResource(MusicUtils.isNowPlayingOrPreparing(item) ? R.drawable.pause : R.drawable.play);

        holder.play.setOnClickListener(v -> {
            if(mClickListener != null){
                mClickListener.onClick(holder.getAdapterPosition(), item);
            }
        });
        holder.Track.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(mContext, holder.Track);
            popup.inflate(R.menu.audio_item_menu);
            popup.setOnMenuItemClickListener(item1 -> {
                switch (item1.getItemId()) {
                    case R.id.add_item_audio:
                        boolean myAudio = item.getOwnerId() == Settings.get().accounts().getCurrent();
                        if(myAudio)
                            delete(Settings.get().accounts().getCurrent(), item);
                        else
                            add(Settings.get().accounts().getCurrent(), item);
                        return true;
                    case R.id.save_item_audio:
                        if(!AppPerms.hasWriteStoragePermision(mContext)) {
                            AppPerms.requestWriteStoragePermission((Activity)mContext);
                        }
                        if(!AppPerms.hasReadStoragePermision(mContext)) {
                            AppPerms.requestReadExternalStoragePermission((Activity)mContext);
                        }
                        int ret = DownloadUtil.downloadTrack(mContext, item);
                        if(ret == 0)
                            PhoenixToast.showToast(mContext, R.string.saved_audio);
                        else if(ret == 1)
                            PhoenixToast.showToastSuccess(mContext, R.string.exist_audio);
                        else
                            PhoenixToast.showToast(mContext, R.string.error_audio);
                        return true;
                    case R.id.bitrate_item_audio:
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(item.getUrl(), new HashMap<>());
                        String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                        PhoenixToast.showToast(mContext, mContext.getResources().getString(R.string.bitrate) + " " + (Long.parseLong(bitrate) / 1000) + " bit");
                        return true;
                    default:
                        return false;
                }
            });
            if(item.getOwnerId() == Settings.get().accounts().getCurrent())
                popup.getMenu().findItem(R.id.add_item_audio).setTitle(R.string.delete);
            else
                popup.getMenu().findItem(R.id.add_item_audio).setTitle(R.string.action_add);
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setData(List<Audio> data) {
        this.mData = data;
        notifyDataSetChanged();
    }

    class AudioHolder extends RecyclerView.ViewHolder {

        TextView artist;
        TextView title;
        ImageView play;
        TextView time;
        ImageView saved;
        LinearLayout Track;

        AudioHolder(View itemView) {
            super(itemView);
            artist = itemView.findViewById(R.id.dialog_title);
            title = itemView.findViewById(R.id.dialog_message);
            play = itemView.findViewById(R.id.item_audio_play);
            time = itemView.findViewById(R.id.item_audio_time);
            saved = itemView.findViewById(R.id.saved);
            Track = itemView.findViewById(R.id.track_option);
        }
    }

    private ClickListener mClickListener;

    public void setClickListener(ClickListener clickListener) {
        this.mClickListener = clickListener;
    }

    public interface ClickListener {
        void onClick(int position, Audio audio);
    }
}
