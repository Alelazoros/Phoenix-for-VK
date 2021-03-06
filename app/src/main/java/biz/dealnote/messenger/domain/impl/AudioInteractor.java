package biz.dealnote.messenger.domain.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.dealnote.messenger.api.interfaces.INetworker;
import biz.dealnote.messenger.domain.IAudioInteractor;
import biz.dealnote.messenger.model.Audio;
import biz.dealnote.messenger.model.IdPair;
import biz.dealnote.messenger.util.Objects;
import io.reactivex.Completable;
import io.reactivex.Single;
import static biz.dealnote.messenger.util.Objects.isNull;

/**
 * Created by admin on 07.10.2017.
 * Phoenix-for-VK
 */
public class AudioInteractor implements IAudioInteractor {

    private final INetworker networker;
    private final int AccountId;

    public AudioInteractor(INetworker networker, int AccountId) {
        this.networker = networker;
        this.AccountId = AccountId;
    }

    protected static String join(Collection<IdPair> audios, String delimiter) {
        if (isNull(audios)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (IdPair pair : audios) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }

            sb.append(pair.ownerId + "_" + pair.id);
        }

        return sb.toString();
    }

    @Override
    public Single<Audio> add(int accountId, Audio orig, Integer groupId, Integer albumId) {
        return networker.vkDefault(accountId)
                .audio()
                .add(orig.getId(), orig.getOwnerId(), groupId, albumId)
                .map(resultId -> {
                    final int targetOwnerId = Objects.nonNull(groupId) ? -groupId : accountId;
                    //clone
                    return new Audio()
                            .setId(resultId)
                            .setOwnerId(targetOwnerId)
                            .setAlbumId(Objects.nonNull(albumId) ? albumId : 0)
                            .setArtist(orig.getArtist())
                            .setTitle(orig.getTitle())
                            .setUrl(orig.getUrl())
                            .setLyricsId(orig.getLyricsId())
                            .setGenre(orig.getGenre())
                            .setDuration(orig.getDuration());
                });
    }

    @Override
    public Completable delete(int accountId, int audioId, int ownerId) {
        return networker.vkDefault(accountId)
                .audio()
                .delete(audioId, ownerId)
                .ignoreElement();
    }

    @Override
    public Completable restore(int accountId, int audioId, int ownerId) {
        return networker.vkDefault(accountId)
                .audio()
                .restore(audioId, ownerId)
                .ignoreElement();
    }

    @Override
    public Completable sendBroadcast(int accountId, int audioOwnerId, int audioId, Collection<Integer> targetIds) {
        return networker.vkDefault(accountId)
                .audio()
                .setBroadcast(new biz.dealnote.messenger.api.model.IdPair(audioId, audioOwnerId), targetIds)
                .ignoreElement();
    }

    @Override
    public Single<List<Audio>> get(int accountId, int ownerId, int offset) {

        return networker.vkDefault(accountId)
                .audio()
                .get(ownerId,offset).map(out-> {
                    List<Audio> ret = new ArrayList<>();
                    for(int i = 0; i < out.items.size(); i++)
                        ret.add(new Audio()
                            .setId(out.items.get(i).id)
                            .setOwnerId(out.items.get(i).owner_id)
                            .setAlbumId(Objects.nonNull(out.items.get(i).album_id) ? out.items.get(i).album_id : 0)
                            .setArtist(out.items.get(i).artist)
                            .setTitle(out.items.get(i).title)
                            .setUrl(out.items.get(i).url)
                            .setLyricsId(out.items.get(i).lyrics_id)
                            .setGenre(out.items.get(i).genre)
                            .setDuration(out.items.get(i).duration));
                    return ret;
                });
    }

    @Override
    public Single<List<Audio>> getById(List<IdPair> audios) {
        return networker.vkDefault(AccountId)
                .audio()
                .getById(join(audios, ",")).map(out-> {
                    List<Audio> ret = new ArrayList<>();
                    for(int i = 0; i < out.size(); i++)
                        ret.add(new Audio()
                                .setId(out.get(i).id)
                                .setOwnerId(out.get(i).owner_id)
                                .setAlbumId(Objects.nonNull(out.get(i).album_id) ? out.get(i).album_id : 0)
                                .setArtist(out.get(i).artist)
                                .setTitle(out.get(i).title)
                                .setUrl(out.get(i).url)
                                .setLyricsId(out.get(i).lyrics_id)
                                .setGenre(out.get(i).genre)
                                .setDuration(out.get(i).duration));
                    return ret;
                });
    }

    @Override
    public Single<List<Audio>> getPopular(int accountId, int foreign, int genre) {

        return networker.vkDefault(accountId)
                .audio()
                .getPopular(foreign,genre).map(out-> {
                    List<Audio> ret = new ArrayList<>();
                    for(int i = 0; i < out.size(); i++)
                        ret.add(new Audio()
                                .setId(out.get(i).id)
                                .setOwnerId(out.get(i).owner_id)
                                .setAlbumId(Objects.nonNull(out.get(i).album_id) ? out.get(i).album_id : 0)
                                .setArtist(out.get(i).artist)
                                .setTitle(out.get(i).title)
                                .setUrl(out.get(i).url)
                                .setLyricsId(out.get(i).lyrics_id)
                                .setGenre(out.get(i).genre)
                                .setDuration(out.get(i).duration));
                    return ret;
                });
    }

    @Override
    public Single<List<Audio>> search(int accountId, String query, boolean own, int offset) {
        return networker.vkDefault(accountId)
                .audio()
                .Search(query, own == true ? 1:0, offset).map(out-> {
                    List<Audio> ret = new ArrayList<>();
                    for(int i = 0; i < out.items.size(); i++)
                        ret.add(new Audio()
                                .setId(out.items.get(i).id)
                                .setOwnerId(out.items.get(i).owner_id)
                                .setAlbumId(Objects.nonNull(out.items.get(i).album_id) ? out.items.get(i).album_id : 0)
                                .setArtist(out.items.get(i).artist)
                                .setTitle(out.items.get(i).title)
                                .setUrl(out.items.get(i).url)
                                .setLyricsId(out.items.get(i).lyrics_id)
                                .setGenre(out.items.get(i).genre)
                                .setDuration(out.items.get(i).duration));
                    return ret;
                });
    }

    @Override
    public boolean isAudioPluginAvailable() {
        return true;
    }
}