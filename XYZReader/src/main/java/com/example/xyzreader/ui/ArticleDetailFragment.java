package com.example.xyzreader.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.transition.Slide;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;
    private ConstraintLayout mMetaBar;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;
    private android.support.v7.widget.RecyclerView RecyclerView;
    private AnimatedVectorDrawable downToUp;
    private AnimatedVectorDrawable upToDown;
    private boolean up = true;
    private static boolean showBackUp = false;
    private static final long longSlideDuration = 800;
    private static final long slideDuration = 450;


    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);

        mMetaBar = mRootView.findViewById(R.id.meta_bar);
        mPhotoView = mRootView.findViewById(R.id.photo);
        final TextView titleText = mRootView.findViewById(R.id.article_title);
        final TextView byLineTextView = mRootView.findViewById(R.id.article_byline);

        mDrawInsetsFrameLayout = mRootView.findViewById(R.id.draw_insets_frame_layout);
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });

        mScrollView = mRootView.findViewById(R.id.scrollview);
        mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();
            }
        });

        RecyclerView = mRootView.findViewById(R.id.rv_body_text);
        final ImageButton  backToTopImagebutton= mRootView.findViewById(R.id.back_up);
        backToTopImagebutton.animate().setDuration(longSlideDuration);

        RecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!recyclerView.canScrollVertically(-1)) {
                    backToTopImagebutton.animate().alpha(0);
                    showBackUp = false;

                }else if (!showBackUp) {
                    backToTopImagebutton.animate().alpha(1);
                    showBackUp = true;
                }

            }
        });

        backToTopImagebutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backToTopImagebutton.animate().alpha(0);
                showBackUp = false;

                RecyclerView.smoothScrollToPosition(0);
            }
        });

        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);
        mStatusBarColorDrawable = new ColorDrawable(0);

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });


        downToUp = (AnimatedVectorDrawable) getResources().getDrawable(R.drawable.avd_down_to_up);
        upToDown = (AnimatedVectorDrawable) getResources().getDrawable(R.drawable.avd_up_to_down);
        final ImageButton upDownButton = mRootView.findViewById(R.id.upside_down_button);
        upDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DisplayMetrics displayMetrics = new DisplayMetrics();
                getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int height = displayMetrics.heightPixels;
                RecyclerView.setMinimumHeight(height);

                long sd =   mPhotoView.getHeight();

                if(up){
                    AnimatedVectorDrawable drawable = upToDown;
                    upDownButton.setImageDrawable(drawable);
                    drawable.start();

                    Slide slide = new Slide();
                    slide.setDuration(longSlideDuration);
                    slide.setSlideEdge(Gravity.TOP);

                    ViewGroup root = mRootView.findViewById(R.id.scene_root);
                    TransitionManager.beginDelayedTransition(root, slide);
                    mPhotoView.setVisibility(View.INVISIBLE);

                    byLineTextView.setVisibility(View.INVISIBLE);
                    titleText.setVisibility(View.INVISIBLE);

                    mMetaBar.animate().translationY(-sd).setDuration(slideDuration);
                    RecyclerView.animate().translationY(-sd).setDuration(slideDuration);

                    byLineTextView.setVisibility(View.GONE);
                    titleText.setVisibility(View.GONE);
                    up = false;

                }else{
                    AnimatedVectorDrawable drawable = downToUp;
                    upDownButton.setImageDrawable(drawable);
                    drawable.start();

                    Slide slide = new Slide();
                    slide.setDuration(longSlideDuration);
                    slide.setSlideEdge(Gravity.TOP);

                    ViewGroup root = mRootView.findViewById(R.id.scene_root);
                    TransitionManager.beginDelayedTransition(root, slide);
                    mPhotoView.setVisibility(View.VISIBLE);
                    mMetaBar.animate().translationY(0).setDuration(slideDuration);
                    RecyclerView.animate().translationY(0).setDuration(slideDuration);

                    byLineTextView.setVisibility(View.VISIBLE);
                    titleText.setVisibility(View.VISIBLE);

                    up = true;
                }

                }
            });

        bindViews();
        updateStatusBar();
        return mRootView;
    }


    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private Date parsePublishedDate() {
        try {
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        TextView titleView = (TextView) mRootView.findViewById(R.id.article_title);
        TextView bylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        RecyclerView = mRootView.findViewById(R.id.rv_body_text);
        bylineView.setMovementMethod(new LinkMovementMethod());
        //TextView bodyView = (TextView) mRootView.findViewById(R.id.article_body);


        //bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                bylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            } else {
                // If date is before 1902, just show the string
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            }

            Iterable<String> chunks = Splitter.fixedLength(200).split(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)));
            RecyclerView.setLayoutManager(new LinearLayoutManager(mRootView.getContext()));
            Adapter textAdapter = new Adapter(chunks);
            RecyclerView.setAdapter(textAdapter);

            //bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY).replaceAll("(\r\n|\n)", "<br />")));
            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {

                                Palette p = new Palette.Builder(bitmap).generate();
                                mMutedColor = p.getDarkMutedColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
                                mRootView.findViewById(R.id.meta_bar)
                                        .setBackgroundColor(mMutedColor);

                                updateStatusBar();
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });

        } else {
            mRootView.setVisibility(View.GONE);
            titleView.setText("N/A");
            bylineView.setText("N/A" );
            //bodyView.setText("N/A");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    private class Adapter extends RecyclerView.Adapter<ArticleDetailFragment.ViewHolder> {
        private List<String> Text;

        Adapter(Iterable<String> text) {
            Text = Lists.newArrayList(text);
        }

        @NonNull
        @Override
        public ArticleDetailFragment.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view = inflater.inflate(R.layout.detail_item_body, parent, false);

            return new ArticleDetailFragment.ViewHolder(view);
        }


        @Override
        public void onBindViewHolder(@NonNull final ArticleDetailFragment.ViewHolder holder, final int position) {
            holder.bodyTextView.setText(Text.get(position));
        }

        @Override
        public int getItemCount() {
            return Text.size();
        }
    }


    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView bodyTextView;

        ViewHolder(View view) {
            super(view);
            bodyTextView = view.findViewById(R.id.body_text);
        }
    }
}
