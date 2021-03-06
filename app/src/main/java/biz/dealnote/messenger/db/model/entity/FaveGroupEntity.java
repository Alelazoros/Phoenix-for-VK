package biz.dealnote.messenger.db.model.entity;

public class FaveGroupEntity extends CommunityEntity {

    private String description;

    private String type;

    private long updateDate;

    public FaveGroupEntity(int id) {
        super(id);
    }

    public String getDescription() {
        return description;
    }

    public FaveGroupEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getFaveType() {
        return type;
    }

    public FaveGroupEntity setFaveType(String type) {
        this.type = type;
        return this;
    }

    public long getUpdateDate() {
        return updateDate;
    }

    public FaveGroupEntity setUpdateDate(long updateDate) {
        this.updateDate = updateDate;
        return this;
    }
}
