package com.github.ayltai.newspaper.item;

import javax.inject.Inject;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.ayltai.newspaper.BuildConfig;
import com.github.ayltai.newspaper.Constants;
import com.github.ayltai.newspaper.R;
import com.github.ayltai.newspaper.graphics.DaggerGraphicsComponent;
import com.github.ayltai.newspaper.graphics.GraphicsModule;
import com.github.ayltai.newspaper.graphics.ImageLoaderCallback;
import com.github.ayltai.newspaper.list.ListScreen;
import com.github.ayltai.newspaper.rss.Item;
import com.github.ayltai.newspaper.setting.Settings;
import com.github.ayltai.newspaper.util.ContextUtils;
import com.github.ayltai.newspaper.util.DateUtils;
import com.github.ayltai.newspaper.util.IntentUtils;
import com.github.ayltai.newspaper.util.ItemUtils;
import com.github.ayltai.newspaper.util.LogUtils;
import com.github.piasy.biv.loader.ImageLoader;
import com.gjiazhe.panoramaimageview.GyroscopeObserver;
import com.gjiazhe.panoramaimageview.PanoramaImageView;
import com.jakewharton.rxbinding.view.RxView;
import com.stfalcon.frescoimageviewer.ImageViewer;

import flow.ClassKey;
import flow.Flow;
import flow.TreeKey;
import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;
import xyz.hanks.library.SmallBang;

public final class ItemScreen extends FrameLayout implements ItemPresenter.View {
    public static final class Key extends ClassKey implements TreeKey, Parcelable {
        private final ListScreen.Key parentKey;
        private final Item           item;

        Key(@NonNull final ListScreen.Key parentKey, @NonNull final Item item) {
            this.parentKey = parentKey;
            this.item      = item;
        }

        @NonNull
        @Override
        public Object getParentKey() {
            return this.parentKey;
        }

        @NonNull
        public Item getItem() {
            return this.item;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;

            final ItemScreen.Key that = (ItemScreen.Key)o;
            return this.item.getLink().equals(that.item.getLink());

        }

        @SuppressWarnings("checkstyle:magicnumber")
        @Override
        public int hashCode() {
            final int hashCode = super.hashCode();

            return 31 * hashCode + this.item.getLink().hashCode();
        }

        //region Parcelable

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest, final int flags) {
            dest.writeParcelable(this.parentKey, flags);
            dest.writeParcelable(this.item, flags);
        }

        protected Key(@NonNull final Parcel in) {
            this.parentKey = in.readParcelable(ListScreen.Key.class.getClassLoader());
            this.item      = in.readParcelable(Item.class.getClassLoader());
        }

        public static final Parcelable.Creator<ItemScreen.Key> CREATOR = new Parcelable.Creator<ItemScreen.Key>() {
            @NonNull
            @Override
            public ItemScreen.Key createFromParcel(@NonNull final Parcel source) {
                return new ItemScreen.Key(source);
            }

            @NonNull
            @Override
            public ItemScreen.Key[] newArray(final int size) {
                return new ItemScreen.Key[size];
            }
        };

        //endregion
    }

    //region Events

    private final BehaviorSubject<Void> attachedToWindow   = BehaviorSubject.create();
    private final BehaviorSubject<Void> detachedFromWindow = BehaviorSubject.create();

    private final PublishSubject<Void>    zooms     = PublishSubject.create();
    private final PublishSubject<Boolean> bookmarks = PublishSubject.create();
    private final PublishSubject<Void>    shares    = PublishSubject.create();

    //endregion

    //region Variables

    private GyroscopeObserver observer = new GyroscopeObserver();

    @Inject
    ImageLoader imageLoader;

    private CompositeSubscription subscriptions;
    private ImageLoaderCallback   callback;
    private boolean               isBookmarked;
    private boolean               hasAttached;

    //endregion

    //region Components

    private AppBarLayout appBarLayout;
    private TextView     toolbarTitle;
    private ImageView    bookmark;
    private View         share;
    private TextView     title;
    private TextView     description;
    private TextView     source;
    private TextView     publishDate;
    private ViewGroup    thumbnailContainer;
    private ImageView    thumbnail;
    private SmallBang    smallBang;

    //endregion

    @Inject
    public ItemScreen(@NonNull final Context context) {
        super(context);

        DaggerGraphicsComponent.builder()
            .graphicsModule(new GraphicsModule())
            .build()
            .inject(this);
    }

    //region Properties

    @Override
    public void setTitle(@Nullable final String title) {
        if (this.hasAttached) {
            if (TextUtils.isEmpty(title)) {
                this.title.setVisibility(View.GONE);
                this.toolbarTitle.setText(this.getResources().getText(R.string.app_name));
            } else {
                if (BuildConfig.DEBUG) Log.d(this.getClass().getSimpleName(), "title (before) = " + title);
                final CharSequence value = ItemUtils.removeHtml(title);
                if (BuildConfig.DEBUG) Log.d(this.getClass().getSimpleName(), "title (after) = " + value);

                this.title.setVisibility(View.VISIBLE);
                this.title.setText(value);
                this.toolbarTitle.setText(this.title.getText());
            }
        }
    }

    @Override
    public void setDescription(@Nullable final String description) {
        if (this.hasAttached) {
            if (TextUtils.isEmpty(description)) {
                this.description.setVisibility(View.GONE);
            } else {
                if (BuildConfig.DEBUG) Log.d(this.getClass().getSimpleName(), "description (before) = " + description);
                final CharSequence value = ItemUtils.removeHtml(description);
                if (BuildConfig.DEBUG) Log.d(this.getClass().getSimpleName(), "description (after) = " + value);

                this.description.setVisibility(View.VISIBLE);
                this.description.setText(value);
            }
        }
    }

    @Override
    public void setSource(@Nullable final String source) {
        if (this.hasAttached) {
            if (TextUtils.isEmpty(source)) {
                this.source.setVisibility(View.GONE);
            } else {
                this.source.setVisibility(View.VISIBLE);
                this.source.setText(ItemUtils.removeHtml(source));
            }
        }
    }

    @Override
    public void setLink(@Nullable final String link) {
    }

    @Override
    public void setPublishDate(final long publishDate) {
        if (this.hasAttached) {
            if (publishDate == 0) {
                this.publishDate.setVisibility(View.GONE);
            } else {
                this.publishDate.setVisibility(View.VISIBLE);
                this.publishDate.setText(DateUtils.getTimeAgo(this.getContext(), publishDate));
            }
        }
    }

    @Override
    public void setThumbnail(@Nullable final String thumbnail, @Constants.ListViewType final int type) {
        if (this.hasAttached) {
            if (TextUtils.isEmpty(thumbnail)) {
                this.appBarLayout.setExpanded(false, false);
            } else {
                this.appBarLayout.setExpanded(true, false);

                this.imageLoader.loadImage(Uri.parse(thumbnail), this.callback);
            }
        }
    }

    @Override
    public void setIsBookmarked(final boolean isBookmarked) {
        this.isBookmarked = isBookmarked;

        final Drawable drawable = AppCompatDrawableManager.get().getDrawable(this.getContext(), this.isBookmarked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        DrawableCompat.setTint(drawable, ContextUtils.getColor(this.getContext(), R.attr.indicatorColor));

        this.bookmark.setImageDrawable(drawable);
    }

    //endregion

    //region Actions

    @Nullable
    @Override
    public Observable<Void> clicks() {
        return null;
    }

    @Nullable
    @Override
    public Observable<Void> zooms() {
        return this.zooms;
    }

    @Nullable
    @Override
    public Observable<Boolean> bookmarks() {
        return this.bookmarks;
    }

    @Nullable
    @Override
    public Observable<Void> shares() {
        return this.shares;
    }

    @Override
    public void showItem(@NonNull final ListScreen.Key parentKey, @NonNull final Item item) {
    }

    @Override
    public void showMedia(@NonNull final String url) {
        new ImageViewer.Builder(this.getContext(), new String[] { url }).show();
    }

    @Override
    public void share(@NonNull final String url) {
        IntentUtils.share(this.getContext(), url);
    }

    //endregion

    //region Lifecycle

    @NonNull
    @Override
    public Observable<Void> attachments() {
        return this.attachedToWindow;
    }

    @NonNull
    @Override
    public Observable<Void> detachments() {
        return this.detachedFromWindow;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final Activity activity = ContextUtils.getActivity(this.getContext());
        if (activity != null) this.smallBang = SmallBang.attach2Window(activity);

        if (this.hasAttached) {
            this.thumbnailContainer.removeViewAt(0);
        } else {
            this.hasAttached = true;

            final View view = LayoutInflater.from(this.getContext()).inflate(R.layout.screen_item, this, false);

            this.appBarLayout       = (AppBarLayout)view.findViewById(R.id.appBarLayout);
            this.toolbarTitle       = (TextView)view.findViewById(R.id.toolbar_title);
            this.bookmark           = (ImageView)view.findViewById(R.id.bookmark);
            this.share              = view.findViewById(R.id.share);
            this.title              = (TextView)view.findViewById(R.id.title);
            this.description        = (TextView)view.findViewById(R.id.description);
            this.source             = (TextView)view.findViewById(R.id.source);
            this.publishDate        = (TextView)view.findViewById(R.id.publishDate);
            this.thumbnailContainer = (ViewGroup)view.findViewById(R.id.thumbnailContainer);

            final Drawable drawable = AppCompatDrawableManager.get().getDrawable(this.getContext(), R.drawable.ic_arrow_back);
            DrawableCompat.setTint(drawable, ContextUtils.getColor(this.getContext(), R.attr.indicatorColor));

            final Toolbar toolbar = (Toolbar)view.findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(drawable);
            toolbar.setNavigationOnClickListener(v -> Flow.get(v).goBack());

            this.addView(view);
        }

        this.initThumbnail();

        this.attachEvents();

        if (Settings.isPanoramaEnabled(this.getContext())) this.observer.register(this.getContext());

        this.attachedToWindow.onNext(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (Settings.isPanoramaEnabled(this.getContext())) this.observer.unregister();

        this.smallBang = null;

        if (this.subscriptions != null && this.subscriptions.hasSubscriptions()) {
            this.subscriptions.unsubscribe();
            this.subscriptions = null;
        }

        this.detachedFromWindow.onNext(null);
    }

    //endregion

    private void attachEvents() {
        if (this.subscriptions == null) {
            this.subscriptions = new CompositeSubscription();

            this.subscriptions.add(RxView.clicks(this.thumbnail).subscribe(dummy -> this.zooms.onNext(null), error -> LogUtils.getInstance().e(this.getClass().getSimpleName(), error.getMessage(), error)));
            this.subscriptions.add(RxView.clicks(this.share).subscribe(dummy -> this.shares.onNext(null), error -> LogUtils.getInstance().e(this.getClass().getSimpleName(), error.getMessage(), error)));

            this.subscriptions.add(RxView.clicks(this.bookmark).subscribe(dummy -> {
                this.setIsBookmarked(!this.isBookmarked);

                if (this.smallBang != null && this.isBookmarked) this.smallBang.bang(this.bookmark);

                this.bookmarks.onNext(this.isBookmarked);
            }, error -> LogUtils.getInstance().e(this.getClass().getSimpleName(), error.getMessage(), error)));
        }
    }

    private void initThumbnail() {
        final boolean isPanoramaEnabled = Settings.isPanoramaEnabled(this.getContext());

        this.thumbnail = (ImageView)LayoutInflater.from(this.getContext()).inflate(isPanoramaEnabled ? R.layout.view_item_thumbnail_panorama : R.layout.view_item_thumbnail, this.thumbnailContainer, false);
        this.callback  = new ImageLoaderCallback(this.thumbnail);

        if (isPanoramaEnabled) ((PanoramaImageView)this.thumbnail).setGyroscopeObserver(this.observer);

        this.thumbnailContainer.addView(this.thumbnail);
    }
}
