package org.wordpress.android.ui.reader;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import org.wordpress.android.R;
import org.wordpress.android.datasets.ReaderPostTable;
import org.wordpress.android.models.ReaderPostList;
import org.wordpress.android.models.ReaderTag;
import org.wordpress.android.ui.reader.ReaderTypes.ReaderPostListType;
import org.wordpress.android.ui.reader.actions.ReaderActions;
import org.wordpress.android.ui.reader.actions.ReaderPostActions;
import org.wordpress.android.ui.reader.models.ReaderBlogIdPostId;
import org.wordpress.android.ui.reader.models.ReaderBlogIdPostIdList;
import org.wordpress.android.util.AppLog;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import javax.annotation.Nonnull;

public class ReaderPostPagerActivity extends Activity
        implements ReaderUtils.FullScreenListener {

    private ViewPager mViewPager;
    private PostPagerAdapter mPageAdapter;
    private boolean mIsFullScreen;
    private ReaderPostListType mPostListType;
    private ReaderTag mCurrentTag;

    private boolean mIsRequestingMorePosts;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (isFullScreenSupported()) {
            getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.reader_activity_post_pager);

        // remove the window background since each fragment already has a background color
        getWindow().setBackgroundDrawable(null);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mViewPager = (ViewPager) findViewById(R.id.viewpager);

        final String title;
        final long blogId;
        final long postId;
        if (savedInstanceState != null) {
            title = savedInstanceState.getString(ReaderConstants.ARG_TITLE);
            blogId = savedInstanceState.getLong(ReaderConstants.ARG_BLOG_ID);
            postId = savedInstanceState.getLong(ReaderConstants.ARG_POST_ID);
            if (savedInstanceState.containsKey(ReaderConstants.ARG_POST_LIST_TYPE)) {
                mPostListType = (ReaderPostListType) savedInstanceState.getSerializable(ReaderConstants.ARG_POST_LIST_TYPE);
            }
            mCurrentTag = (ReaderTag) savedInstanceState.getSerializable(ReaderConstants.ARG_TAG);
        } else {
            title = getIntent().getStringExtra(ReaderConstants.ARG_TITLE);
            blogId = getIntent().getLongExtra(ReaderConstants.ARG_BLOG_ID, 0);
            postId = getIntent().getLongExtra(ReaderConstants.ARG_POST_ID, 0);
            if (getIntent().hasExtra(ReaderConstants.ARG_POST_LIST_TYPE)) {
                mPostListType = (ReaderPostListType) getIntent().getSerializableExtra(ReaderConstants.ARG_POST_LIST_TYPE);
            }
            mCurrentTag = (ReaderTag) getIntent().getSerializableExtra(ReaderConstants.ARG_TAG);
        }

        if (mPostListType == null) {
            mPostListType = ReaderPostListType.TAG_FOLLOWED;
        }

        if (!TextUtils.isEmpty(title)) {
            this.setTitle(title);
        }

        mPageAdapter = new PostPagerAdapter(getFragmentManager(), blogId, postId);
        mViewPager.setAdapter(mPageAdapter);

        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onRequestFullScreen(false);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    // return from fullscreen and pause the active web view when the user
                    // starts scrolling - important because otherwise embedded content in
                    // the web view will continue to play
                    onRequestFullScreen(false);
                    ReaderPostDetailFragment fragment = getActiveDetailFragment();
                    if (fragment != null) {
                        fragment.pauseWebView();
                    }
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@Nonnull Bundle outState) {
        outState.putString(ReaderConstants.ARG_TITLE, (String) this.getTitle());
        if (hasCurrentTag()) {
            outState.putSerializable(ReaderConstants.ARG_TAG, getCurrentTag());
        }
        if (mPostListType != null) {
            outState.putSerializable(ReaderConstants.ARG_POST_LIST_TYPE, mPostListType);
        }
        if (mPageAdapter != null) {
            ReaderBlogIdPostId id = mPageAdapter.getCurrentBlogIdPostId();
            if (id != null) {
                outState.putLong(ReaderConstants.ARG_BLOG_ID, id.getBlogId());
                outState.putLong(ReaderConstants.ARG_POST_ID, id.getPostId());
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        ReaderPostDetailFragment fragment = getActiveDetailFragment();
        if (fragment != null && fragment.isCustomViewShowing()) {
            // if fullscreen video is showing, hide the custom view rather than navigate back
            fragment.hideCustomView();
        } else if (fragment != null && fragment.isAddCommentBoxShowing()) {
            // if comment reply entry is showing, hide it rather than navigate back
            fragment.hideAddCommentBox();
        } else {
            super.onBackPressed();
        }
    }

    private ReaderTag getCurrentTag() {
        return mCurrentTag;
    }

    private boolean hasCurrentTag() {
        return mCurrentTag != null;
    }

    @Override
    public boolean onRequestFullScreen(boolean enableFullScreen) {
        if (!isFullScreenSupported() || enableFullScreen == mIsFullScreen) {
            return false;
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            if (enableFullScreen) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
        }

        mIsFullScreen = enableFullScreen;
        return true;
    }

    ReaderPostListType getPostListType() {
        return mPostListType;
    }

    @Override
    public boolean isFullScreen() {
        return mIsFullScreen;
    }

    @Override
    public boolean isFullScreenSupported() {
        return true;
    }

    private ReaderPostDetailFragment getActiveDetailFragment() {
        if (mViewPager == null || mPageAdapter == null) {
            return null;
        }

        Fragment fragment = mPageAdapter.getFragmentAtPosition(mViewPager.getCurrentItem());
        if (fragment instanceof ReaderPostDetailFragment) {
            return (ReaderPostDetailFragment) fragment;
        } else {
            return null;
        }
    }

    private class PostPagerAdapter extends FragmentStatePagerAdapter {
        private final ReaderBlogIdPostIdList mIdList = new ReaderBlogIdPostIdList();
        private boolean mAllPostsLoaded;
        private final long END_ID = -1;
        private final int LOAD_MORE_OFFSET = 5;

        // this is used to retain a weak reference to created fragments so we can access them
        // in getFragmentAtPosition() - necessary because we need to pause the web view in
        // the active fragment when the user swipes away from it, but the adapter provides
        // no way to access the active fragment
        private final HashMap<String, WeakReference<Fragment>> mFragmentMap =
                new HashMap<String, WeakReference<Fragment>>();

        PostPagerAdapter(FragmentManager fm, long selectedBlogId, long selectedPostId) {
            super(fm);
            loadPosts(selectedBlogId, selectedPostId);
        }

        boolean isValidPosition(int position) {
            return (position >= 0 && position < getCount());
        }

        @Override
        public int getCount() {
            return mIdList.size();
        }

        @Override
        public Fragment getItem(int position) {
            long blogId = mIdList.get(position).getBlogId();
            long postId = mIdList.get(position).getPostId();

            Fragment fragment;
            if (blogId == END_ID && postId == END_ID) {
                fragment = PostPagerEndFragment.newInstance();
            } else {
                fragment = ReaderPostDetailFragment.newInstance(blogId, postId, getPostListType());
                if (position > (getCount() - LOAD_MORE_OFFSET) && canRequestMorePosts()) {
                    requestMorePosts();
                }
            }

            mFragmentMap.put(getItemKey(position), new WeakReference<Fragment>(fragment));

            return fragment;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            mFragmentMap.remove(getItemKey(position));
            super.destroyItem(container, position, object);
        }

        private String getItemKey(int position) {
            return mIdList.get(position).getBlogId() + ":" + mIdList.get(position).getPostId();
        }

        private Fragment getFragmentAtPosition(int position) {
            if (!isValidPosition(position)) {
                return null;
            }
            String key = getItemKey(position);
            if (!mFragmentMap.containsKey(key)) {
                return null;
            }
            return mFragmentMap.get(key).get();
        }

        private ReaderBlogIdPostId getCurrentBlogIdPostId() {
            int position = mViewPager.getCurrentItem();
            if (isValidPosition(position)) {
                return mIdList.get(position);
            } else {
                return null;
            }
        }

        private boolean canRequestMorePosts() {
            return (!mAllPostsLoaded
                    && mIdList.size() > LOAD_MORE_OFFSET
                    && mIdList.size() < ReaderConstants.READER_MAX_POSTS_TO_DISPLAY
                    && hasCurrentTag()); // TODO: support blog preview
        }

        private void loadPosts(final long blogId, final long postId) {
            final Handler handler = new Handler();
            new Thread() {
                @Override
                public void run() {
                    final ReaderPostList posts;
                    int maxPosts = ReaderConstants.READER_MAX_POSTS_TO_DISPLAY;
                    switch (getPostListType()) {
                        case TAG_FOLLOWED:
                        case TAG_PREVIEW:
                            posts = ReaderPostTable.getPostsWithTag(getCurrentTag(), maxPosts);
                            break;
                        case BLOG_PREVIEW:
                            posts = ReaderPostTable.getPostsInBlog(blogId, maxPosts);
                            break;
                        default :
                            return;
                    }

                    if (posts.size() > 0) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                mIdList.clear();
                                mIdList.addAll(posts.getBlogIdPostIdList());

                                // add a bogus entry to the end of the list so we can show PostPagerEndFragment
                                // when the user scrolls beyond the last post - note that this is only done
                                // if there's more than one post
                                if (mIdList.size() > 1 && mIdList.indexOf(END_ID, END_ID) == -1) {
                                    mIdList.add(new ReaderBlogIdPostId(END_ID, END_ID));
                                }

                                notifyDataSetChanged();

                                // select the passed post
                                int selectedIndex = mIdList.indexOf(blogId, postId);
                                if (isValidPosition(selectedIndex)) {
                                    mViewPager.setCurrentItem(selectedIndex);
                                }
                            }
                        });
                    }
                }
            }.start();
        }

        private void requestMorePosts() {
            if (mIsRequestingMorePosts) {
                return;
            }

            ReaderActions.UpdateResultAndCountListener resultListener = new ReaderActions.UpdateResultAndCountListener() {
                @Override
                public void onUpdateResult(ReaderActions.UpdateResult result, int numNewPosts) {
                    mIsRequestingMorePosts = false;
                    if (!isFinishing()) {
                        if (numNewPosts > 0) {
                            AppLog.d(AppLog.T.READER, "reader pager > older posts received");
                            ReaderBlogIdPostId id = getCurrentBlogIdPostId();
                            long blogId = (id != null ? id.getBlogId() : 0);
                            long postId = (id != null ? id.getPostId() : 0);
                            loadPosts(blogId, postId);
                        } else {
                            mAllPostsLoaded = true;
                        }
                    }
                }
            };

            mIsRequestingMorePosts = true;
            AppLog.d(AppLog.T.READER, "reader pager > requesting older posts");

            ReaderPostActions.updatePostsInTag(
                    getCurrentTag(),
                    ReaderActions.RequestDataAction.LOAD_OLDER,
                    resultListener);
        }
    }

    /*
     * fragment that appears when user scrolls beyond the last post
     */
    public static class PostPagerEndFragment extends Fragment {
        private TextView mTxtCheckmark;

        private static PostPagerEndFragment newInstance() {
            return new PostPagerEndFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.reader_fragment_end, container, false);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                }
            });

            mTxtCheckmark = (TextView) view.findViewById(R.id.text_checkmark);

            return view;
        }

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            // setUserVisibleHint wasn't available until API 15 (ICE_CREAM_SANDWICH_MR1)
            if (Build.VERSION.SDK_INT >= 15) {
                super.setUserVisibleHint(isVisibleToUser);
            }
            if (isVisibleToUser) {
                showCheckmark();
            } else {
                hideCheckmark();
            }
        }

        private void showCheckmark() {
            if (!isVisible()) {
                return;
            }

            mTxtCheckmark.setVisibility(View.VISIBLE);

            AnimatorSet set = new AnimatorSet();
            set.setDuration(750);
            set.setInterpolator(new OvershootInterpolator());
            set.playTogether(ObjectAnimator.ofFloat(mTxtCheckmark, "scaleX", 0.25f, 1f),
                             ObjectAnimator.ofFloat(mTxtCheckmark, "scaleY", 0.25f, 1f));
            set.start();
        }

        private void hideCheckmark() {
            if (isVisible()) {
                mTxtCheckmark.setVisibility(View.INVISIBLE);
            }
        }
    }
}
