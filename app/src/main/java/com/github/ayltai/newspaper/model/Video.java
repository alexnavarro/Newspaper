package com.github.ayltai.newspaper.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class Video extends RealmObject implements Parcelable {
    //region Fields

    private String videoUrl;
    private String thumbnailUrl;

    //endregion

    //region Constructors

    public Video() {
    }

    public Video(@NonNull final String videoUrl, @NonNull final String thumbnailUrl) {
        this.videoUrl     = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    //endregion

    //region Properties

    @NonNull
    public String getVideoUrl() {
        return this.videoUrl;
    }

    @NonNull
    public String getThumbnailUrl() {
        return this.thumbnailUrl;
    }

    //endregion

    //region Parcelable

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeString(this.videoUrl);
        dest.writeString(this.thumbnailUrl);
    }

    protected Video(@NonNull final Parcel in) {
        this.videoUrl     = in.readString();
        this.thumbnailUrl = in.readString();
    }

    public static final Parcelable.Creator<Video> CREATOR = new Parcelable.Creator<Video>() {
        @NonNull
        @Override
        public Video createFromParcel(@NonNull final Parcel source) {
            return new Video(source);
        }

        @NonNull
        @Override
        public Video[] newArray(final int size) {
            return new Video[size];
        }
    };

    //endregion
}
