package com.github.ayltai.newspaper.main;

import java.io.File;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.flaviofaria.kenburnsview.KenBurnsView;
import com.flaviofaria.kenburnsview.Transition;
import com.github.ayltai.newspaper.BuildConfig;
import com.github.ayltai.newspaper.Constants;
import com.github.ayltai.newspaper.R;
import com.github.ayltai.newspaper.graphics.DaggerGraphicsComponent;
import com.github.ayltai.newspaper.graphics.GraphicsModule;
import com.github.ayltai.newspaper.setting.SettingsActivity;
import com.github.ayltai.newspaper.util.ContextUtils;
import com.github.ayltai.newspaper.util.SuppressFBWarnings;
import com.github.javiersantos.materialstyleddialogs.MaterialStyledDialog;
import com.github.javiersantos.materialstyleddialogs.enums.Duration;
import com.github.javiersantos.materialstyleddialogs.enums.Style;
import com.github.piasy.biv.loader.ImageLoader;
import com.yalantis.guillotine.animation.GuillotineAnimation;
import com.yalantis.guillotine.interfaces.GuillotineListener;

import flow.ClassKey;
import rx.Observable;
import rx.subjects.BehaviorSubject;

@SuppressLint("ViewConstructor")
public final class MainScreen extends FrameLayout implements MainPresenter.View {
    public static final class Key extends ClassKey implements Parcelable {
        public Key() {
        }

        //region Parcelable

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        }

        protected Key(@NonNull final Parcel in) {
        }

        public static final Parcelable.Creator<MainScreen.Key> CREATOR = new Parcelable.Creator<MainScreen.Key>() {
            @NonNull
            @Override
            public MainScreen.Key createFromParcel(@NonNull final Parcel source) {
                return new MainScreen.Key(source);
            }

            @NonNull
            @Override
            public MainScreen.Key[] newArray(final int size) {
                return new MainScreen.Key[size];
            }
        };

        //endregion
    }

    private static final Random RANDOM = new Random();

    //region Events

    private final BehaviorSubject<Void>    attachedToWindow   = BehaviorSubject.create();
    private final BehaviorSubject<Void>    detachedFromWindow = BehaviorSubject.create();
    private final BehaviorSubject<Integer> pageChanges        = BehaviorSubject.create();

    //endregion

    private final ImageLoader.Callback callback = new ImageLoader.Callback() {
        @Override
        public void onCacheHit(final File image) {
            // FIXME: Exception may be thrown if the bitmap dimensions are too large
            MainScreen.this.headerImage.post(() -> MainScreen.this.headerImage.setImageBitmap(BitmapFactory.decodeFile(image.getAbsolutePath())));
        }

        @SuppressWarnings("WrongThread")
        @Override
        public void onCacheMiss(final File image) {
            this.onCacheHit(image);
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onProgress(final int progress) {
        }

        @Override
        public void onFinish() {
        }
    };

    @Inject
    ImageLoader imageLoader;

    //region Components

    private CollapsingToolbarLayout toolbar;
    private ViewPager               viewPager;
    private KenBurnsView            headerImage;

    //endregion

    //region Variables

    private MainAdapter         adapter;
    private boolean             hasAttached;
    private boolean             isBound;
    private boolean             isDrawerOpened;
    private GuillotineAnimation animation;
    private List<String>        images;

    //endregion

    @Inject
    public MainScreen(@NonNull final Context context) {
        super(context);

        DaggerGraphicsComponent.builder()
            .graphicsModule(new GraphicsModule(context))
            .build()
            .inject(this);
    }

    @Override
    public void bind(@NonNull final MainAdapter adapter) {
        if (!this.isBound) {
            this.isBound = true;

            this.viewPager.setAdapter(this.adapter = adapter);

            this.pageChanges.onNext(0);
        }
    }

    @Override
    public void updateHeaderTitle(@NonNull final CharSequence title) {
        this.toolbar.setTitle(title);
    }

    @Override
    public void updateHeaderImages(@NonNull final List<String> images) {
        this.images = images;

        this.updateHeaderImages();
    }

    private void updateHeaderImages() {
        if (this.images == null || this.images.isEmpty()) {
            this.headerImage.post(() -> this.headerImage.setImageBitmap(null));
        } else {
            MainScreen.this.imageLoader.loadImage(Uri.parse(this.images.get(MainScreen.RANDOM.nextInt(this.images.size()))), this.callback);
        }
    }

    @Override
    public boolean goBack() {
        if (this.isDrawerOpened) {
            this.animation.close();

            return true;
        }

        return false;
    }

    @Override
    public Observable<Integer> pageChanges() {
        return this.pageChanges;
    }

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

        if (!this.hasAttached) {
            final View view = LayoutInflater.from(this.getContext()).inflate(R.layout.screen_main, this, false);

            this.toolbar = (CollapsingToolbarLayout)view.findViewById(R.id.collapsingToolbarLayout);

            this.headerImage = (KenBurnsView)view.findViewById(R.id.headerImage);
            this.headerImage.setTransitionListener(new KenBurnsView.TransitionListener() {
                @Override
                public void onTransitionStart(final Transition transition) {
                }

                @Override
                public void onTransitionEnd(final Transition transition) {
                    MainScreen.this.updateHeaderImages();
                }
            });

            this.viewPager = (ViewPager)view.findViewById(R.id.viewPager);
            this.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(final int position, final float positionOffset, final int positionOffsetPixels) {
                }

                @Override
                public void onPageSelected(final int position) {
                    MainScreen.this.pageChanges.onNext(position);
                }

                @Override
                public void onPageScrollStateChanged(final int state) {
                }
            });

            this.addView(view);

            this.setUpDrawerMenu(view);

            this.hasAttached = true;
        }

        this.attachedToWindow.onNext(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        this.detachedFromWindow.onNext(null);
    }

    @Override
    public void close() {
        if (this.adapter != null) {
            this.adapter.close();
            this.adapter = null;
        }
    }

    //endregion

    private void showSettings() {
        ((Activity)this.getContext()).startActivityForResult(new Intent(this.getContext(), SettingsActivity.class), Constants.REQUEST_SETTINGS);
    }

    @SuppressFBWarnings({"NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION", "PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS"})
    private void showAbout() {
        new MaterialStyledDialog.Builder(this.getContext())
            .setStyle(Style.HEADER_WITH_ICON)
            .setHeaderColor(ContextUtils.getResourceId(this.getContext(), R.attr.primaryColor))
            .setIcon(R.mipmap.ic_launcher)
            .setTitle(R.string.app_name)
            .setDescription(String.format(this.getContext().getString(R.string.app_version), BuildConfig.VERSION_NAME))
            .setPositiveText(android.R.string.ok)
            .setNegativeText(R.string.rate_app)
            .onNegative((dialog, which) -> {
                final String name = this.getContext().getPackageName();

                try {
                    this.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + name)));
                } catch (final ActivityNotFoundException e) {
                    this.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + name)));
                }
            })
            .withIconAnimation(true)
            .withDialogAnimation(true, Duration.NORMAL)
            .withDivider(true)
            .show();
    }

    private void setUpDrawerMenu(@NonNull final View view) {
        final View drawerMenu = LayoutInflater.from(this.getContext()).inflate(R.layout.view_drawer_menu, this, false);
        drawerMenu.setOnClickListener(v -> {
            // Prevent click-through
        });

        drawerMenu.findViewById(R.id.action_settings).setOnClickListener(v -> {
            this.animation.close();
            this.showSettings();
        });

        drawerMenu.findViewById(R.id.action_about).setOnClickListener(v -> {
            this.animation.close();
            this.showAbout();
        });

        this.addView(drawerMenu);

        this.animation = new GuillotineAnimation.GuillotineBuilder(drawerMenu, drawerMenu.findViewById(R.id.drawer_close), this.findViewById(R.id.drawer_open))
            .setStartDelay(Constants.DRAWER_MENU_ANIMATION_DELAY)
            .setActionBarViewForAnimation(view.findViewById(R.id.toolbar))
            .setClosedOnStart(true)
            .setGuillotineListener(new GuillotineListener() {
                @Override
                public void onGuillotineOpened() {
                    MainScreen.this.isDrawerOpened = true;
                }

                @Override
                public void onGuillotineClosed() {
                    MainScreen.this.isDrawerOpened = false;
                }
            })
            .build();
    }
}
