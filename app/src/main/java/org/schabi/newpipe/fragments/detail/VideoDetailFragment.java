package org.schabi.newpipe.fragments.detail;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.helper.PlayerHelper.isClearingQueueConfirmationRequired;
import static org.schabi.newpipe.player.playqueue.PlayQueueItem.RECOVERY_UNSET;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;
import static org.schabi.newpipe.util.ListHelper.getUrlAndNonTorrentStreams;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.squareup.picasso.Callback;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.EmptyFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerType;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.ui.MainPlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ReturnYouTubeDislikeUtils;
import org.schabi.newpipe.util.VideoSegment;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.SponsorBlockUtils;
import org.schabi.newpipe.util.external_communication.ShareUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener,
        View.OnLongClickListener,
        PlayerServiceExtendedEventListener,
        OnKeyDownListener {
    public static final String KEY_SWITCHING_PLAYERS = "switching_players";

    private static final float MAX_OVERLAY_ALPHA = 0.9f;
    private static final float MAX_PLAYER_HEIGHT = 0.7f;

    public static final String ACTION_SHOW_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER";
    public static final String ACTION_HIDE_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER";
    public static final String ACTION_PLAYER_STARTED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED";
    public static final String ACTION_VIDEO_FRAGMENT_RESUMED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED";
    public static final String ACTION_VIDEO_FRAGMENT_STOPPED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED";

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";
    private static final String DESCRIPTION_TAB_TAG = "DESCRIPTION TAB";
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    private static final String PICASSO_VIDEO_DETAILS_TAG = "PICASSO_VIDEO_DETAILS_TAG";

    // tabs
    private boolean showComments;
    private boolean showRelatedItems;
    private boolean showDescription;
    private String selectedTabTag;
    @AttrRes @NonNull final List<Integer> tabIcons = new ArrayList<>();
    @StringRes @NonNull final List<Integer> tabContentDescriptions = new ArrayList<>();
    private boolean tabSettingsChanged = false;
    private int lastAppBarVerticalOffset = Integer.MAX_VALUE; // prevents useless updates

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    @NonNull
    protected String title = "";
    @State
    @Nullable
    protected String url = null;
    @Nullable
    protected PlayQueue playQueue = null;
    @State
    int bottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    int lastStableBottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    protected boolean autoPlayEnabled = true;

    @Nullable
    private StreamInfo currentInfo = null;
    private Disposable currentWorker;
    @NonNull
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private Disposable positionSubscriber = null;
    @Nullable
    private Disposable videoSegmentsSubscriber = null;

    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private BottomSheetBehavior.BottomSheetCallback bottomSheetCallback;
    private BroadcastReceiver broadcastReceiver;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentVideoDetailBinding binding;

    private TabAdapter pageAdapter;

    private ContentObserver settingsContentObserver;
    @Nullable
    private PlayerService playerService;
    private Player player;
    private final PlayerHolder playerHolder = PlayerHolder.getInstance();

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    //////////////////////////////////////////////////////////////////////////*/
    @Override
    public void onServiceConnected(final Player connectedPlayer,
                                   final PlayerService connectedPlayerService,
                                   final boolean playAfterConnect) {
        player = connectedPlayer;
        playerService = connectedPlayerService;

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded();

        final Optional<MainPlayerUi> playerUi = player.UIs().get(MainPlayerUi.class);
        if (!player.videoPlayerSelected() && !playAfterConnect) {
            return;
        }

        if (DeviceUtils.isLandscape(requireContext())) {
            // If the video is playing but orientation changed
            // let's make the video in fullscreen again
            checkLandscape();
        } else if (playerUi.map(ui -> ui.isFullscreen() && !ui.isVerticalVideo()).orElse(false)
                // Tablet UI has orientation-independent fullscreen
                && !DeviceUtils.isTablet(activity)) {
            // Device is in portrait orientation after rotation but UI is in fullscreen.
            // Return back to non-fullscreen state
            playerUi.ifPresent(MainPlayerUi::toggleFullscreen);
        }

        //noinspection SimplifyOptionalCallChains
        if (playAfterConnect
                || (currentInfo != null
                && isAutoplayEnabled()
                && !playerUi.isPresent())) {
            autoPlayEnabled = true; // forcefully start playing
            openVideoPlayerAutoFullscreen();
        }
        updateOverlayPlayQueueButtonVisibility();
    }

    @Override
    public void onServiceDisconnected() {
        playerService = null;
        player = null;
        restoreDefaultBrightness();
    }


    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(final int serviceId,
                                                  @Nullable final String videoUrl,
                                                  @NonNull final String name,
                                                  @Nullable final PlayQueue queue) {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name, queue);
        return instance;
    }

    public static VideoDetailFragment getInstanceInCollapsedState() {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.updateBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);
        return instance;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true);
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true);
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true);
        selectedTabTag = prefs.getString(
                getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG);
        prefs.registerOnSharedPreferenceChangeListener(this);

        setupBroadcastReceiver();

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                if (activity != null && !globalScreenOrientationLocked(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        };
        activity.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = FragmentVideoDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        restoreDefaultBrightness();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key),
                        pageAdapter.getItemTitle(binding.viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.d(TAG, "onResume() called");
        }

        activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_RESUMED));

        updateOverlayPlayQueueButtonVisibility();

        setupBrightness();

        if (tabSettingsChanged) {
            tabSettingsChanged = false;
            initTabs();
            if (currentInfo != null) {
                updateTabs(currentInfo);
            }
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared()) {
            startLoading(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (!activity.isChangingConfigurations()) {
            activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_STOPPED));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing() && isPlayerAvailable() && player.videoPlayerSelected()) {
            playerHolder.stopService();
        } else {
            playerHolder.setListener(null);
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);
        activity.unregisterReceiver(broadcastReceiver);
        activity.getContentResolver().unregisterContentObserver(settingsContentObserver);

        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        if (videoSegmentsSubscriber != null) {
            videoSegmentsSubscriber.dispose();
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        disposables.clear();
        positionSubscriber = null;
        currentWorker = null;
        bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback);

        if (activity.isFinishing()) {
            playQueue = null;
            currentInfo = null;
            stack = new LinkedList<>();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            serviceId, url, title, null, false);
                } else {
                    Log.e(TAG, "ReCaptcha failed");
                }
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key.equals(getString(R.string.show_comments_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        } else if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedItems = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        } else if (key.equals(getString(R.string.show_description_key))) {
            showDescription = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFM() != null && currentInfo != null) {
                    disposables.add(
                            PlaylistDialog.createCorrespondingDialog(
                                    getContext(),
                                    List.of(new StreamEntity(currentInfo)),
                                    dialog -> dialog.show(getFM(), TAG)
                            )
                    );
                }
                break;
            case R.id.detail_controls_download:
                if (PermissionHelper.checkStoragePermissions(activity,
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                    this.openDownloadDialog();
                }
                break;
            case R.id.detail_controls_share:
                if (currentInfo != null) {
                    ShareUtils.shareText(requireContext(), currentInfo.getName(),
                            currentInfo.getUrl(), currentInfo.getThumbnailUrl());
                }
                break;
            case R.id.detail_controls_open_in_browser:
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getUrl());
                }
                break;
            case R.id.detail_controls_play_with_kodi:
                if (currentInfo != null) {
                    try {
                        NavigationHelper.playWithKore(
                                requireContext(), Uri.parse(currentInfo.getUrl()));
                    } catch (final Exception e) {
                        if (DEBUG) {
                            Log.i(TAG, "Failed to start kore", e);
                        }
                        KoreUtils.showInstallKoreDialog(requireContext());
                    }
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (isEmpty(currentInfo.getSubChannelUrl())) {
                    if (!isEmpty(currentInfo.getUploaderUrl())) {
                        openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                    }

                    if (DEBUG) {
                        Log.i(TAG, "Can't open sub-channel because we got no channel URL");
                    }
                } else {
                    openChannel(currentInfo.getSubChannelUrl(),
                            currentInfo.getSubChannelName());
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                // make sure not to open any player if there is nothing currently loaded!
                // FIXME removing this `if` causes the player service to start correctly, then stop,
                //  then restart badly without calling `startForeground()`, causing a crash when
                //  later closing the detail fragment
                if (currentInfo != null) {
                    autoPlayEnabled = true; // forcefully start playing
                    // FIXME Workaround #7427
                    if (isPlayerAvailable()) {
                        player.setRecovery();
                    }
                    openVideoPlayerAutoFullscreen();
                }
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndSecondaryControls();
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
            case R.id.overlay_buttons_layout:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;
            case R.id.overlay_play_queue_button:
                NavigationHelper.openPlayQueue(getContext());
                break;
            case R.id.overlay_play_pause_button:
                if (playerIsNotStopped()) {
                    player.playPause();
                    player.UIs().get(VideoPlayerUi.class).ifPresent(ui -> ui.hideControls(0, 0));
                    showSystemUi();
                } else {
                    autoPlayEnabled = true; // forcefully start playing
                    openVideoPlayer(false);
                }

                setOverlayPlayPauseImage(isPlayerAvailable() && player.isPlaying());
                break;
            case R.id.overlay_close_button:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
        }
    }

    private void openChannel(final String subChannelUrl, final String subChannelName) {
        try {
            NavigationHelper.openChannelFragment(getFM(), currentInfo.getServiceId(),
                    subChannelUrl, subChannelName);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(this, "Opening channel fragment", e);
        }
    }

    @Override
    public boolean onLongClick(final View v) {
        if (isLoading.get() || currentInfo == null) {
            return false;
        }

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
            case R.id.detail_controls_download:
                NavigationHelper.openDownloads(activity);
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
                openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                break;
            case R.id.detail_uploader_root_layout:
                if (isEmpty(currentInfo.getSubChannelUrl())) {
                    Log.w(TAG,
                            "Can't open parent channel because we got no parent channel URL");
                } else {
                    openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                }
                break;
            case R.id.detail_title_root_layout:
                ShareUtils.copyToClipboard(requireContext(),
                        binding.detailVideoTitleView.getText().toString());
                break;
        }

        return true;
    }

    private void toggleTitleAndSecondaryControls() {
        if (binding.detailSecondaryControlPanel.getVisibility() == View.GONE) {
            binding.detailVideoTitleView.setMaxLines(10);
            animateRotation(binding.detailToggleSecondaryControlsView,
                    VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 180);
            binding.detailSecondaryControlPanel.setVisibility(View.VISIBLE);
        } else {
            binding.detailVideoTitleView.setMaxLines(1);
            animateRotation(binding.detailToggleSecondaryControlsView,
                    VideoPlayerUi.DEFAULT_CONTROLS_DURATION, 0);
            binding.detailSecondaryControlPanel.setVisibility(View.GONE);
        }
        // view pager height has changed, update the tab layout
        updateTabLayoutVisibility();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
    }

    @Override // called from onViewCreated in {@link BaseFragment#onViewCreated}
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        pageAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(pageAdapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);

        binding.detailThumbnailRootLayout.requestFocus();

        binding.detailControlsPlayWithKodi.setVisibility(
                KoreUtils.shouldShowPlayWithKodi(requireContext(), serviceId)
                        ? View.VISIBLE
                        : View.GONE
        );
        binding.detailControlsCrashThePlayer.setVisibility(
                DEBUG && PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(getString(R.string.show_crash_the_player_key), false)
                        ? View.VISIBLE
                        : View.GONE
        );

        if (DeviceUtils.isTv(getContext())) {
            // remove ripple effects from detail controls
            final int transparent = ContextCompat.getColor(requireContext(),
                    R.color.transparent_background_color);
            binding.detailControlsPlaylistAppend.setBackgroundColor(transparent);
            binding.detailControlsBackground.setBackgroundColor(transparent);
            binding.detailControlsPopup.setBackgroundColor(transparent);
            binding.detailControlsDownload.setBackgroundColor(transparent);
            binding.detailControlsShare.setBackgroundColor(transparent);
            binding.detailControlsOpenInBrowser.setBackgroundColor(transparent);
            binding.detailControlsPlayWithKodi.setBackgroundColor(transparent);
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        binding.detailTitleRootLayout.setOnClickListener(this);
        binding.detailTitleRootLayout.setOnLongClickListener(this);
        binding.detailUploaderRootLayout.setOnClickListener(this);
        binding.detailUploaderRootLayout.setOnLongClickListener(this);
        binding.detailThumbnailRootLayout.setOnClickListener(this);

        binding.detailControlsBackground.setOnClickListener(this);
        binding.detailControlsBackground.setOnLongClickListener(this);
        binding.detailControlsPopup.setOnClickListener(this);
        binding.detailControlsPopup.setOnLongClickListener(this);
        binding.detailControlsPlaylistAppend.setOnClickListener(this);
        binding.detailControlsDownload.setOnClickListener(this);
        binding.detailControlsDownload.setOnLongClickListener(this);
        binding.detailControlsShare.setOnClickListener(this);
        binding.detailControlsOpenInBrowser.setOnClickListener(this);
        binding.detailControlsPlayWithKodi.setOnClickListener(this);
        if (DEBUG) {
            binding.detailControlsCrashThePlayer.setOnClickListener(
                    v -> VideoDetailPlayerCrasher.onCrashThePlayer(
                            this.getContext(),
                            this.player)
            );
        }

        binding.overlayThumbnail.setOnClickListener(this);
        binding.overlayThumbnail.setOnLongClickListener(this);
        binding.overlayMetadataLayout.setOnClickListener(this);
        binding.overlayMetadataLayout.setOnLongClickListener(this);
        binding.overlayButtonsLayout.setOnClickListener(this);
        binding.overlayPlayQueueButton.setOnClickListener(this);
        binding.overlayCloseButton.setOnClickListener(this);
        binding.overlayPlayPauseButton.setOnClickListener(this);

        binding.detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        binding.detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());

        binding.appBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
            // prevent useless updates to tab layout visibility if nothing changed
            if (verticalOffset != lastAppBarVerticalOffset) {
                lastAppBarVerticalOffset = verticalOffset;
                // the view was scrolled
                updateTabLayoutVisibility();
            }
        });

        setupBottomPlayer();
        if (!playerHolder.isBound()) {
            setHeightThumbnail();
        } else {
            playerHolder.startService(false, this);
        }
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (view, motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                animate(binding.touchAppendDetail, true, 250, AnimationType.ALPHA,
                        0, () ->
                        animate(binding.touchAppendDetail, false, 1500,
                                AnimationType.ALPHA, 1000));
            }
            return false;
        };
    }

    private void initThumbnailViews(@NonNull final StreamInfo info) {
        PicassoHelper.loadDetailsThumbnail(info.getThumbnailUrl()).tag(PICASSO_VIDEO_DETAILS_TAG)
                .into(binding.detailThumbnailImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        // nothing to do, the image was loaded correctly into the thumbnail
                    }

                    @Override
                    public void onError(final Exception e) {
                        showSnackBarError(new ErrorInfo(e, UserAction.LOAD_IMAGE,
                                info.getThumbnailUrl(), info));
                    }
                });

        PicassoHelper.loadAvatar(info.getSubChannelAvatarUrl()).tag(PICASSO_VIDEO_DETAILS_TAG)
                .into(binding.detailSubChannelThumbnailView);
        PicassoHelper.loadAvatar(info.getUploaderAvatarUrl()).tag(PICASSO_VIDEO_DETAILS_TAG)
                .into(binding.detailUploaderThumbnailView);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private static LinkedList<StackItem> stack = new LinkedList<>();

    @Override
    public boolean onKeyDown(final int keyCode) {
        return isPlayerAvailable()
                && player.UIs().get(VideoPlayerUi.class)
                .map(playerUi -> playerUi.onKeyDown(keyCode)).orElse(false);
    }

    @Override
    public boolean onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (isFullscreen()) {
            if (!DeviceUtils.isTablet(activity)) {
                player.pause();
            }
            restoreDefaultOrientation();
            setAutoPlay(false);
            return true;
        }

        // If we have something in history of played items we replay it here
        if (isPlayerAvailable()
                && player.getPlayQueue() != null
                && player.videoPlayerSelected()
                && player.getPlayQueue().previous()) {
            return true; // no code here, as previous() was used in the if
        }

        // That means that we are on the start of the stack,
        if (stack.size() <= 1) {
            restoreDefaultOrientation();
            return false; // let MainActivity handle the onBack (e.g. to minimize the mini player)
        }

        // Remove top
        stack.pop();
        // Get stack item from the new top
        setupFromHistoryItem(Objects.requireNonNull(stack.peek()));

        return true;
    }

    private void setupFromHistoryItem(final StackItem item) {
        setAutoPlay(false);
        hideMainPlayerOnLoadingNewStream();

        setInitialData(item.getServiceId(), item.getUrl(),
                item.getTitle() == null ? "" : item.getTitle(), item.getPlayQueue());
        startLoading(false);

        // Maybe an item was deleted in background activity
        if (item.getPlayQueue().getItem() == null) {
            return;
        }

        final PlayQueueItem playQueueItem = item.getPlayQueue().getItem();
        // Update title, url, uploader from the last item in the stack (it's current now)
        final boolean isPlayerStopped = !isPlayerAvailable() || player.isStopped();
        if (playQueueItem != null && isPlayerStopped) {
            updateOverlayData(playQueueItem.getTitle(),
                    playQueueItem.getUploader(), playQueueItem.getThumbnailUrl());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (wasCleared()) {
            return;
        }

        if (currentInfo == null) {
            prepareAndLoadInfo();
        } else {
            prepareAndHandleInfoIfNeededAfterDelay(currentInfo, false, 50);
        }
    }

    public void selectAndLoadVideo(final int newServiceId,
                                   @Nullable final String newUrl,
                                   @NonNull final String newTitle,
                                   @Nullable final PlayQueue newQueue) {
        if (isPlayerAvailable() && newQueue != null && playQueue != null
                && playQueue.getItem() != null && !playQueue.getItem().getUrl().equals(newUrl)) {
            // Preloading can be disabled since playback is surely being replaced.
            player.disablePreloadingOfCurrentTrack();
        }

        setInitialData(newServiceId, newUrl, newTitle, newQueue);
        startLoading(false, true);
    }

    private void prepareAndHandleInfoIfNeededAfterDelay(final StreamInfo info,
                                                        final boolean scrollToTop,
                                                        final long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity == null) {
                return;
            }
            // Data can already be drawn, don't spend time twice
            if (info.getName().equals(binding.detailVideoTitleView.getText().toString())) {
                return;
            }
            prepareAndHandleInfo(info, scrollToTop);
        }, delay);
    }

    private void prepareAndHandleInfo(final StreamInfo info, final boolean scrollToTop) {
        if (DEBUG) {
            Log.d(TAG, "prepareAndHandleInfo() called with: "
                    + "info = [" + info + "], scrollToTop = [" + scrollToTop + "]");
        }

        showLoading();
        initTabs();

        if (scrollToTop) {
            scrollToTop();
        }
        handleResult(info);
        showContent();

    }

    protected void prepareAndLoadInfo() {
        scrollToTop();
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, stack.isEmpty());
    }

    private void startLoading(final boolean forceLoad, final boolean addToBackStack) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, addToBackStack);
    }

    private void runWorker(final boolean forceLoad, final boolean addToBackStack) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    isLoading.set(false);
                    hideMainPlayerOnLoadingNewStream();
                    if (result.getAgeLimit() != NO_AGE_LIMIT && !prefs.getBoolean(
                            getString(R.string.show_age_restricted_content), false)) {
                        hideAgeRestrictedContent();
                    } else {
                        handleResult(result);
                        showContent();
                        if (addToBackStack) {
                            if (playQueue == null) {
                                playQueue = new SinglePlayQueue(result);
                            }
                            if (stack.isEmpty() || !stack.peek().getPlayQueue().equals(playQueue)) {
                                stack.push(new StackItem(serviceId, url, title, playQueue));
                            }
                        }

                        if (isAutoplayEnabled()) {
                            openVideoPlayerAutoFullscreen();
                        }
                    }
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                        url == null ? "no url" : url, serviceId)));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void initTabs() {
        if (pageAdapter.getCount() != 0) {
            selectedTabTag = pageAdapter.getItemTitle(binding.viewPager.getCurrentItem());
        }
        pageAdapter.clearAllItems();
        tabIcons.clear();
        tabContentDescriptions.clear();

        if (shouldShowComments()) {
            pageAdapter.addFragment(
                    CommentsFragment.getInstance(serviceId, url, title), COMMENTS_TAB_TAG);
            tabIcons.add(R.drawable.ic_comment);
            tabContentDescriptions.add(R.string.comments_tab_description);
        }

        if (showRelatedItems && binding.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), RELATED_TAB_TAG);
            tabIcons.add(R.drawable.ic_art_track);
            tabContentDescriptions.add(R.string.related_items_tab_description);
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(EmptyFragment.newInstance(false), DESCRIPTION_TAB_TAG);
            tabIcons.add(R.drawable.ic_description);
            tabContentDescriptions.add(R.string.description_tab_description);
        }

        if (pageAdapter.getCount() == 0) {
            pageAdapter.addFragment(EmptyFragment.newInstance(true), EMPTY_TAB_TAG);
        }
        pageAdapter.notifyDataSetUpdate();

        if (pageAdapter.getCount() >= 2) {
            final int position = pageAdapter.getItemPositionByTitle(selectedTabTag);
            if (position != -1) {
                binding.viewPager.setCurrentItem(position);
            }
            updateTabIconsAndContentDescriptions();
        }
        // the page adapter now contains tabs: show the tab layout
        updateTabLayoutVisibility();
    }

    /**
     * To be called whenever {@link #pageAdapter} is modified, since that triggers a refresh in
     * {@link FragmentVideoDetailBinding#tabLayout} resetting all tab's icons and content
     * descriptions. This reads icons from {@link #tabIcons} and content descriptions from
     * {@link #tabContentDescriptions}, which are all set in {@link #initTabs()}.
     */
    private void updateTabIconsAndContentDescriptions() {
        for (int i = 0; i < tabIcons.size(); ++i) {
            final TabLayout.Tab tab = binding.tabLayout.getTabAt(i);
            if (tab != null) {
                tab.setIcon(tabIcons.get(i));
                tab.setContentDescription(tabContentDescriptions.get(i));
            }
        }
    }

    private void updateTabs(@NonNull final StreamInfo info) {
        if (showRelatedItems) {
            if (binding.relatedItemsLayout == null) { // phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedItemsFragment.getInstance(info));
            } else { // tablet + TV
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.relatedItemsLayout, RelatedItemsFragment.getInstance(info))
                        .commitAllowingStateLoss();
                binding.relatedItemsLayout.setVisibility(isFullscreen() ? View.GONE : View.VISIBLE);
            }
        }

        if (showDescription) {
            pageAdapter.updateItem(DESCRIPTION_TAB_TAG, new DescriptionFragment(info));
        }

        binding.viewPager.setVisibility(View.VISIBLE);
        // make sure the tab layout is visible
        updateTabLayoutVisibility();
        pageAdapter.notifyDataSetUpdate();
        updateTabIconsAndContentDescriptions();
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(COMMENTS);
        } catch (final ExtractionException e) {
            return false;
        }
    }

    public void updateTabLayoutVisibility() {

        if (binding == null) {
            //If binding is null we do not need to and should not do anything with its object(s)
            return;
        }

        if (pageAdapter.getCount() < 2 || binding.viewPager.getVisibility() != View.VISIBLE) {
            // hide tab layout if there is only one tab or if the view pager is also hidden
            binding.tabLayout.setVisibility(View.GONE);
        } else {
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            binding.tabLayout.post(() -> {
                final var activity = getActivity();
                if (activity != null) {
                    final Rect pagerHitRect = new Rect();
                    binding.viewPager.getHitRect(pagerHitRect);

                    final int height = DeviceUtils.getWindowHeight(activity.getWindowManager());
                    final int viewPagerVisibleHeight = height - pagerHitRect.top;
                    // see TabLayout.DEFAULT_HEIGHT, which is equal to 48dp
                    final float tabLayoutHeight = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

                    if (viewPagerVisibleHeight > tabLayoutHeight * 2) {
                        // no translation at all when viewPagerVisibleHeight > tabLayout.height * 3
                        binding.tabLayout.setTranslationY(
                                Math.max(0, tabLayoutHeight * 3 - viewPagerVisibleHeight));
                        binding.tabLayout.setVisibility(View.VISIBLE);
                    } else {
                        // view pager is not visible enough
                        binding.tabLayout.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    public void scrollToTop() {
        binding.appBarLayout.setExpanded(true, true);
        // notify tab layout of scrolling
        updateTabLayoutVisibility();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void toggleFullscreenIfInFullscreenMode() {
        // If a user watched video inside fullscreen mode and than chose another player
        // return to non-fullscreen mode
        if (isPlayerAvailable()) {
            player.UIs().get(MainPlayerUi.class).ifPresent(playerUi -> {
                if (playerUi.isFullscreen()) {
                    playerUi.toggleFullscreen();
                }
            });
        }
    }

    private void openBackgroundPlayer(final boolean append) {
        final boolean useExternalAudioPlayer = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        toggleFullscreenIfInFullscreenMode();

        if (isPlayerAvailable()) {
            // FIXME Workaround #7427
            player.setRecovery();
        }

        if (!useExternalAudioPlayer) {
            openNormalBackgroundPlayer(append);
        } else {
            final List<AudioStream> audioStreams = getUrlAndNonTorrentStreams(
                    currentInfo.getAudioStreams());
            final int index = ListHelper.getDefaultAudioFormat(activity, audioStreams);

            if (index == -1) {
                Toast.makeText(activity, R.string.no_audio_streams_available_for_external_players,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            startOnExternalPlayer(activity, currentInfo, audioStreams.get(index));
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this);
        } else {
            // FIXME Workaround #7427
            player.setRecovery();
        }

        toggleFullscreenIfInFullscreenMode();

        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) { //resumePlayback: false
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.POPUP);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper
                    .playOnPopupPlayer(activity, queue, true));
        }
    }

    /**
     * Opens the video player, in fullscreen if needed. In order to open fullscreen, the activity
     * is toggled to landscape orientation (which will then cause fullscreen mode).
     *
     * @param directlyFullscreenIfApplicable whether to open fullscreen if we are not already
     *                                       in landscape and screen orientation is locked
     */
    public void openVideoPlayer(final boolean directlyFullscreenIfApplicable) {
        if (directlyFullscreenIfApplicable
                && !DeviceUtils.isLandscape(requireContext())
                && PlayerHelper.globalScreenOrientationLocked(requireContext())) {
            // Make sure the bottom sheet turns out expanded. When this code kicks in the bottom
            // sheet could not have fully expanded yet, and thus be in the STATE_SETTLING state.
            // When the activity is rotated, and its state is saved and then restored, the bottom
            // sheet would forget what it was doing, since even if STATE_SETTLING is restored, it
            // doesn't tell which state it was settling to, and thus the bottom sheet settles to
            // STATE_COLLAPSED. This can be solved by manually setting the state that will be
            // restored (i.e. bottomSheetState) to STATE_EXPANDED.
            updateBottomSheetState(BottomSheetBehavior.STATE_EXPANDED);
            // toggle landscape in order to open directly in fullscreen
            onScreenRotationButtonClicked();
        }

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            showExternalPlaybackDialog();
        } else {
            replaceQueueIfUserConfirms(this::openMainPlayer);
        }
    }

    /**
     * If the option to start directly fullscreen is enabled, calls
     * {@link #openVideoPlayer(boolean)} with {@code directlyFullscreenIfApplicable = true}, so that
     * if the user is not already in landscape and he has screen orientation locked the activity
     * rotates and fullscreen starts. Otherwise, if the option to start directly fullscreen is
     * disabled, calls {@link #openVideoPlayer(boolean)} with {@code directlyFullscreenIfApplicable
     * = false}, hence preventing it from going directly fullscreen.
     */
    public void openVideoPlayerAutoFullscreen() {
        openVideoPlayer(PlayerHelper.isStartMainPlayerFullscreenEnabled(requireContext()));
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        // See UI changes while remote playQueue changes
        if (!isPlayerAvailable()) {
            playerHolder.startService(false, this);
        }

        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnPlayer(activity, queue, PlayerType.AUDIO);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper
                    .playOnBackgroundPlayer(activity, queue, true));
        }
    }

    private void openMainPlayer() {
        if (!isPlayerServiceAvailable()) {
            playerHolder.startService(autoPlayEnabled, this);
            return;
        }
        if (currentInfo == null) {
            return;
        }

        final PlayQueue queue = setupPlayQueueForIntent(false);
        tryAddVideoPlayerView();

        final Intent playerIntent = NavigationHelper.getPlayerIntent(requireContext(),
                PlayerService.class, queue, true, autoPlayEnabled);
        ContextCompat.startForegroundService(activity, playerIntent);
    }

    /**
     * When the video detail fragment is already showing details for a video and the user opens a
     * new one, the video detail fragment changes all of its old data to the new stream, so if there
     * is a video player currently open it should be hidden. This method does exactly that. If
     * autoplay is enabled, the underlying player is not stopped completely, since it is going to
     * be reused in a few milliseconds and the flickering would be annoying.
     */
    private void hideMainPlayerOnLoadingNewStream() {
        //noinspection SimplifyOptionalCallChains
        if (!isPlayerServiceAvailable() || !getRoot().isPresent()
                || !player.videoPlayerSelected()) {
            return;
        }

        removeVideoPlayerView();
        if (isAutoplayEnabled()) {
            playerService.stopForImmediateReusing();
            getRoot().ifPresent(view -> view.setVisibility(View.GONE));
        } else {
            playerHolder.stopService();
        }
    }

    private PlayQueue setupPlayQueueForIntent(final boolean append) {
        if (append) {
            return new SinglePlayQueue(currentInfo);
        }

        PlayQueue queue = playQueue;
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty()) {
            queue = new SinglePlayQueue(currentInfo);
        }

        return queue;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoPlay(final boolean autoPlay) {
        this.autoPlayEnabled = autoPlay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getSubChannelName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> { /* successful */ },
                        error -> Log.e(TAG, "Register view failure: ", error)
                ));
    }

    private boolean isExternalPlayerEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.use_external_video_player_key), false);
    }

    // This method overrides default behaviour when setAutoPlay() is called.
    // Don't auto play if the user selected an external player or disabled it in settings
    private boolean isAutoplayEnabled() {
        return autoPlayEnabled
                && !isExternalPlayerEnabled()
                && (!isPlayerAvailable() || player.videoPlayerSelected())
                && bottomSheetState != BottomSheetBehavior.STATE_HIDDEN
                && PlayerHelper.isAutoplayAllowedByUser(requireContext());
    }

    private void tryAddVideoPlayerView() {
        if (isPlayerAvailable() && getView() != null) {
            // Setup the surface view height, so that it fits the video correctly; this is done also
            // here, and not only in the Handler, to avoid a choppy fullscreen rotation animation.
            setHeightThumbnail();
        }

        // do all the null checks in the posted lambda, too, since the player, the binding and the
        // view could be set or unset before the lambda gets executed on the next main thread cycle
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isPlayerAvailable() || getView() == null) {
                return;
            }

            // setup the surface view height, so that it fits the video correctly
            setHeightThumbnail();

            player.UIs().get(MainPlayerUi.class).ifPresent(playerUi -> {
                // sometimes binding would be null here, even though getView() != null above u.u
                if (binding != null) {
                    // prevent from re-adding a view multiple times
                    playerUi.removeViewFromParent();
                    binding.playerPlaceholder.addView(playerUi.getBinding().getRoot());
                    playerUi.setupVideoSurfaceIfNeeded();
                }
            });
        });
    }

    private void removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder();

        if (player != null) {
            player.UIs().get(VideoPlayerUi.class).ifPresent(VideoPlayerUi::removeViewFromParent);
        }
    }

    private void makeDefaultHeightForVideoPlaceholder() {
        if (getView() == null) {
            return;
        }

        binding.playerPlaceholder.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT;
        binding.playerPlaceholder.requestLayout();
    }

    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    final DisplayMetrics metrics = getResources().getDisplayMetrics();

                    if (getView() != null) {
                        final int height = (DeviceUtils.isInMultiWindow(activity)
                                ? requireView()
                                : activity.getWindow().getDecorView()).getHeight();
                        setHeightThumbnail(height, metrics);
                        getView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                    }
                    return false;
                }
            };

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * {@link #MAX_PLAYER_HEIGHT})
     */
    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);

        if (isFullscreen()) {
            final int height = (DeviceUtils.isInMultiWindow(activity)
                    ? requireView()
                    : activity.getWindow().getDecorView()).getHeight();
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics);
            } else {
                requireView().getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            }
        } else {
            final int height = (int) (isPortrait
                    ? metrics.widthPixels / (16.0f / 9.0f)
                    : metrics.heightPixels / 2.0f);
            setHeightThumbnail(height, metrics);
        }
    }

    private void setHeightThumbnail(final int newHeight, final DisplayMetrics metrics) {
        binding.detailThumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, newHeight));
        binding.detailThumbnailImageView.setMinimumHeight(newHeight);
        if (isPlayerAvailable()) {
            final int maxHeight = (int) (metrics.heightPixels * MAX_PLAYER_HEIGHT);
            player.UIs().get(VideoPlayerUi.class).ifPresent(ui ->
                    ui.getBinding().surfaceView.setHeights(newHeight,
                            ui.isFullscreen() ? newHeight : maxHeight));
        }
    }

    private void showContent() {
        binding.detailContentRootHiding.setVisibility(View.VISIBLE);
    }

    protected void setInitialData(final int newServiceId,
                                  @Nullable final String newUrl,
                                  @NonNull final String newTitle,
                                  @Nullable final PlayQueue newPlayQueue) {
        this.serviceId = newServiceId;
        this.url = newUrl;
        this.title = newTitle;
        this.playQueue = newPlayQueue;
    }

    private void setErrorImage(final int imageResource) {
        if (binding == null || activity == null) {
            return;
        }

        binding.detailThumbnailImageView.setImageDrawable(
                AppCompatResources.getDrawable(requireContext(), imageResource));
        animate(binding.detailThumbnailImageView, false, 0, AnimationType.ALPHA,
                0, () -> animate(binding.detailThumbnailImageView, true, 500));
    }

    @Override
    public void handleError() {
        super.handleError();
        setErrorImage(R.drawable.not_available_monkey);

        if (binding.relatedItemsLayout != null) { // hide related streams for tablets
            binding.relatedItemsLayout.setVisibility(View.INVISIBLE);
        }

        // hide comments / related streams / description tabs
        binding.viewPager.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.GONE);
    }

    private void hideAgeRestrictedContent() {
        showTextError(getString(R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)));
    }

    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                switch (intent.getAction()) {
                    case ACTION_SHOW_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;
                    case ACTION_HIDE_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                        break;
                    case ACTION_PLAYER_STARTED:
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!playerHolder.isBound()) {
                            playerHolder.startService(
                                    false, VideoDetailFragment.this);
                        }
                        break;
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER);
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER);
        intentFilter.addAction(ACTION_PLAYER_STARTED);
        activity.registerReceiver(broadcastReceiver, intentFilter);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    //////////////////////////////////////////////////////////////////////////*/

    private void restoreDefaultOrientation() {
        if (isPlayerAvailable() && player.videoPlayerSelected()) {
            toggleFullscreenIfInFullscreenMode();
        }

        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        if (activity != null && !DeviceUtils.isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {

        super.showLoading();

        //if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if (!ExtractorHelper.isCached(serviceId, url, InfoItem.InfoType.STREAM)) {
            binding.detailContentRootHiding.setVisibility(View.INVISIBLE);
        }

        animate(binding.detailThumbnailPlayButton, false, 50);
        animate(binding.detailDurationView, false, 100);
        animate(binding.detailPositionView, false, 100);
        animate(binding.positionView, false, 50);

        binding.detailVideoTitleView.setText(title);
        binding.detailVideoTitleView.setMaxLines(1);
        animate(binding.detailVideoTitleView, true, 0);

        binding.detailToggleSecondaryControlsView.setVisibility(View.GONE);
        binding.detailTitleRootLayout.setClickable(false);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        if (binding.relatedItemsLayout != null) {
            if (showRelatedItems) {
                binding.relatedItemsLayout.setVisibility(
                        isFullscreen() ? View.GONE : View.INVISIBLE);
            } else {
                binding.relatedItemsLayout.setVisibility(View.GONE);
            }
        }

        PicassoHelper.cancelTag(PICASSO_VIDEO_DETAILS_TAG);
        binding.detailThumbnailImageView.setImageBitmap(null);
        binding.detailSubChannelThumbnailView.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull final StreamInfo info) {
        super.handleResult(info);

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName(), playQueue);

        updateTabs(info);

        animate(binding.detailThumbnailPlayButton, true, 200);
        binding.detailVideoTitleView.setText(title);

        binding.detailSubChannelThumbnailView.setVisibility(View.GONE);

        if (!isEmpty(info.getSubChannelName())) {
            displayBothUploaderAndSubChannel(info);
        } else if (!isEmpty(info.getUploaderName())) {
            displayUploaderAsSubChannel(info);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
            binding.detailUploaderThumbnailView.setVisibility(View.GONE);
        }

        final Drawable buddyDrawable =
                AppCompatResources.getDrawable(activity, R.drawable.placeholder_person);
        binding.detailSubChannelThumbnailView.setImageDrawable(buddyDrawable);
        binding.detailUploaderThumbnailView.setImageDrawable(buddyDrawable);

        if (info.getViewCount() >= 0) {
            if (info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization.listeningCount(activity,
                        info.getViewCount()));
            } else if (info.getStreamType().equals(StreamType.LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization
                        .localizeWatchingCount(activity, info.getViewCount()));
            } else {
                binding.detailViewCountView.setText(Localization
                        .localizeViewCount(activity, info.getViewCount()));
            }
            binding.detailViewCountView.setVisibility(View.VISIBLE);
        } else {
            binding.detailViewCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            binding.detailThumbsDownImgView.setVisibility(View.VISIBLE);
            binding.detailThumbsUpImgView.setVisibility(View.VISIBLE);
            binding.detailThumbsUpCountView.setVisibility(View.GONE);
            binding.detailThumbsDownCountView.setVisibility(View.GONE);

            binding.detailThumbsDisabledView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() == -1) {
                new Thread(() -> {
                    info.setDislikeCount(ReturnYouTubeDislikeUtils.getDislikes(getContext(), info));
                    if (info.getDislikeCount() >= 0) {
                        activity.runOnUiThread(() -> {
                            binding.detailThumbsDownCountView.setText(Localization
                                    .shortCount(activity, info.getDislikeCount()));
                            binding.detailThumbsDownCountView.setVisibility(View.VISIBLE);
                            binding.detailThumbsDownImgView.setVisibility(View.VISIBLE);
                        });
                    }
                }).start();
            }
            if (info.getDislikeCount() >= 0) {
                binding.detailThumbsDownCountView.setText(Localization
                        .shortCount(activity, info.getDislikeCount()));
                binding.detailThumbsDownCountView.setVisibility(View.VISIBLE);
                binding.detailThumbsDownImgView.setVisibility(View.VISIBLE);
            } else {
                binding.detailThumbsDownCountView.setVisibility(View.GONE);
                binding.detailThumbsDownImgView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                binding.detailThumbsUpCountView.setText(Localization.shortCount(activity,
                        info.getLikeCount()));
                binding.detailThumbsUpCountView.setVisibility(View.VISIBLE);
                binding.detailThumbsUpImgView.setVisibility(View.VISIBLE);
            } else {
                binding.detailThumbsUpCountView.setVisibility(View.GONE);
                binding.detailThumbsUpImgView.setVisibility(View.GONE);
            }
            binding.detailThumbsDisabledView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            binding.detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            binding.detailDurationView.setText(R.string.duration_live);
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else {
            binding.detailDurationView.setVisibility(View.GONE);
        }

        binding.detailTitleRootLayout.setClickable(true);
        binding.detailToggleSecondaryControlsView.setRotation(0);
        binding.detailToggleSecondaryControlsView.setVisibility(View.VISIBLE);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        updateProgressInfo(info);
        initThumbnailViews(info);
        showMetaInfoInTextView(info.getMetaInfo(), binding.detailMetaInfoTextView,
                binding.detailMetaInfoSeparator, disposables);

        if (!isPlayerAvailable() || player.isStopped()) {
            updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());
        }

        if (!info.getErrors().isEmpty()) {
            // Bandcamp fan pages are not yet supported and thus a ContentNotAvailableException is
            // thrown. This is not an error and thus should not be shown to the user.
            for (final Throwable throwable : info.getErrors()) {
                if (throwable instanceof ContentNotSupportedException
                        && "Fan pages are not supported".equals(throwable.getMessage())) {
                    info.getErrors().remove(throwable);
                }
            }

            if (!info.getErrors().isEmpty()) {
                showSnackBarError(new ErrorInfo(info.getErrors(),
                        UserAction.REQUESTED_STREAM, info.getUrl(), info));
            }
        }

        binding.detailControlsDownload.setVisibility(
                StreamTypeUtil.isLiveStream(info.getStreamType()) ? View.GONE : View.VISIBLE);
        binding.detailControlsBackground.setVisibility(info.getAudioStreams().isEmpty()
                ? View.GONE : View.VISIBLE);

        final boolean noVideoStreams =
                info.getVideoStreams().isEmpty() && info.getVideoOnlyStreams().isEmpty();
        binding.detailControlsPopup.setVisibility(noVideoStreams ? View.GONE : View.VISIBLE);
        binding.detailThumbnailPlayButton.setImageResource(
                noVideoStreams ? R.drawable.ic_headset_shadow : R.drawable.ic_play_arrow_shadow);
    }

    private void displayUploaderAsSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getUploaderName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);
        binding.detailUploaderTextView.setVisibility(View.GONE);
    }

    private void displayBothUploaderAndSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getSubChannelName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);

        binding.detailSubChannelThumbnailView.setVisibility(View.VISIBLE);

        if (!isEmpty(info.getUploaderName())) {
            binding.detailUploaderTextView.setText(
                    String.format(getString(R.string.video_detail_by), info.getUploaderName()));
            binding.detailUploaderTextView.setVisibility(View.VISIBLE);
            binding.detailUploaderTextView.setSelected(true);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
        }
    }

    public void openDownloadDialog() {
        if (currentInfo == null) {
            return;
        }

        videoSegmentsSubscriber = Single.fromCallable(() -> {
            VideoSegment[] videoSegments = null;

            try {
                videoSegments =
                        SponsorBlockUtils.getYouTubeVideoSegments(getContext(), currentInfo);
            } catch (final Exception e) {
                // TODO: handle?
            }

            return videoSegments == null
                    ? new VideoSegment[0]
                    : videoSegments;
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(videoSegments -> {
            try {
                final DownloadDialog downloadDialog = new DownloadDialog(activity, currentInfo);
                downloadDialog.setVideoSegments(videoSegments);
                downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
            } catch (final Exception e) {
                ErrorUtil.showSnackbar(activity, new ErrorInfo(e, UserAction.DOWNLOAD_OPEN_DIALOG,
                        "Showing download dialog", currentInfo));
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    private void updateProgressInfo(@NonNull final StreamInfo info) {
        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean playbackResumeEnabled = prefs
                .getBoolean(activity.getString(R.string.enable_watch_history_key), true)
                && prefs.getBoolean(activity.getString(R.string.enable_playback_resume_key), true);
        final boolean showPlaybackPosition = prefs.getBoolean(
                activity.getString(R.string.enable_playback_state_lists_key), true);
        if (!playbackResumeEnabled) {
            if (playQueue == null || playQueue.getStreams().isEmpty()
                    || playQueue.getItem().getRecoveryPosition() == RECOVERY_UNSET
                    || !showPlaybackPosition) {
                binding.positionView.setVisibility(View.INVISIBLE);
                binding.detailPositionView.setVisibility(View.GONE);
                // TODO: Remove this check when separation of concerns is done.
                //  (live streams weren't getting updated because they are mixed)
                if (!StreamTypeUtil.isLiveStream(info.getStreamType())) {
                    return;
                }
            } else {
                // Show saved position from backStack if user allows it
                showPlaybackProgress(playQueue.getItem().getRecoveryPosition(),
                        playQueue.getItem().getDuration() * 1000);
                animate(binding.positionView, true, 500);
                animate(binding.detailPositionView, true, 500);
            }
            return;
        }
        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());

        // TODO: Separate concerns when updating database data.
        //  (move the updating part to when the loading happens)
        positionSubscriber = recordManager.loadStreamState(info)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    showPlaybackProgress(state.getProgressMillis(), info.getDuration() * 1000);
                    animate(binding.positionView, true, 500);
                    animate(binding.detailPositionView, true, 500);
                }, e -> {
                    if (DEBUG) {
                        e.printStackTrace();
                    }
                }, () -> {
                    binding.positionView.setVisibility(View.GONE);
                    binding.detailPositionView.setVisibility(View.GONE);
                });
    }

    private void showPlaybackProgress(final long progress, final long duration) {
        final int progressSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(progress);
        final int durationSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
        // If the old and the new progress values have a big difference then use
        // animation. Otherwise don't because it affects CPU
        final boolean shouldAnimate = Math.abs(binding.positionView.getProgress()
                - progressSeconds) > 2;
        binding.positionView.setMax(durationSeconds);
        if (shouldAnimate) {
            binding.positionView.setProgressAnimated(progressSeconds);
        } else {
            binding.positionView.setProgress(progressSeconds);
        }
        final String position = Localization.getDurationString(progressSeconds);
        if (position != binding.detailPositionView.getText()) {
            binding.detailPositionView.setText(position);
        }
        if (binding.positionView.getVisibility() != View.VISIBLE) {
            animate(binding.positionView, true, 100);
            animate(binding.detailPositionView, true, 100);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onViewCreated() {
        tryAddVideoPlayerView();
    }

    @Override
    public void onQueueUpdate(final PlayQueue queue) {
        playQueue = queue;
        if (DEBUG) {
            Log.d(TAG, "onQueueUpdate() called with: serviceId = ["
                    + serviceId + "], videoUrl = [" + url + "], name = ["
                    + title + "], playQueue = [" + playQueue + "]");
        }

        // Register broadcast receiver to listen to playQueue changes
        // and hide the overlayPlayQueueButton when the playQueue is empty / destroyed.
        if (playQueue != null && playQueue.getBroadcastReceiver() != null) {
            playQueue.getBroadcastReceiver().subscribe(
                    event -> updateOverlayPlayQueueButtonVisibility()
            );
        }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        @Nullable final StackItem stackPeek = stack.peek();
        if (stackPeek != null && !stackPeek.getPlayQueue().equals(queue)) {
            @Nullable final PlayQueueItem playQueueItem = queue.getItem();
            if (playQueueItem != null) {
                stack.push(new StackItem(playQueueItem.getServiceId(), playQueueItem.getUrl(),
                        playQueueItem.getTitle(), queue));
                return;
            } // else continue below
        }

        @Nullable final StackItem stackWithQueue = findQueueInStack(queue);
        if (stackWithQueue != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and
            // no longer able to track progress. That's why we update our cached disposed
            // queue with the new one that is active and have the same history.
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.setPlayQueue(queue);
        }
    }

    @Override
    public void onPlaybackUpdate(final int state,
                                 final int repeatMode,
                                 final boolean shuffled,
                                 final PlaybackParameters parameters) {
        setOverlayPlayPauseImage(player != null && player.isPlaying());

        switch (state) {
            case Player.STATE_PLAYING:
                if (binding.positionView.getAlpha() != 1.0f
                        && player.getPlayQueue() != null
                        && player.getPlayQueue().getItem() != null
                        && player.getPlayQueue().getItem().getUrl().equals(url)) {
                    animate(binding.positionView, true, 100);
                    animate(binding.detailPositionView, true, 100);
                }
                break;
        }
    }

    @Override
    public void onProgressUpdate(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
        // Progress updates every second even if media is paused. It's useless until playing
        if (!player.isPlaying() || playQueue == null) {
            return;
        }

        if (player.getPlayQueue().getItem().getUrl().equals(url)) {
            showPlaybackProgress(currentProgress, duration);
        }
    }

    @Override
    public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
        final StackItem item = findQueueInStack(queue);
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.setTitle(info.getName());
            item.setUrl(info.getUrl());
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (!queue.equals(playQueue)) {
            return;
        }

        updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());
        if (currentInfo != null && info.getUrl().equals(currentInfo.getUrl())) {
            return;
        }

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), queue);
        setAutoPlay(false);
        // Delay execution just because it freezes the main thread, and while playing
        // next/previous video you see visual glitches
        // (when non-vertical video goes after vertical video)
        prepareAndHandleInfoIfNeededAfterDelay(info, true, 200);
    }

    @Override
    public void onPlayerError(final PlaybackException error, final boolean isCatchableException) {
        if (!isCatchableException) {
            // Properly exit from fullscreen
            toggleFullscreenIfInFullscreenMode();
            hideMainPlayerOnLoadingNewStream();
        }
    }

    @Override
    public void onServiceStopped() {
        setOverlayPlayPauseImage(false);
        if (currentInfo != null) {
            updateOverlayData(currentInfo.getName(),
                    currentInfo.getUploaderName(),
                    currentInfo.getThumbnailUrl());
        }
        updateOverlayPlayQueueButtonVisibility();
    }

    @Override
    public void onFullscreenStateChanged(final boolean fullscreen) {
        setupBrightness();
        //noinspection SimplifyOptionalCallChains
        if (!isPlayerAndPlayerServiceAvailable()
                || !player.UIs().get(MainPlayerUi.class).isPresent()
                || getRoot().map(View::getParent).orElse(null) == null) {
            return;
        }

        if (fullscreen) {
            hideSystemUiIfNeeded();
            binding.overlayPlayPauseButton.requestFocus();
        } else {
            showSystemUi();
        }

        if (binding.relatedItemsLayout != null) {
            binding.relatedItemsLayout.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }
        scrollToTop();

        tryAddVideoPlayerView();
    }

    @Override
    public void onScreenRotationButtonClicked() {
        // In tablet user experience will be better if screen will not be rotated
        // from landscape to portrait every time.
        // Just turn on fullscreen mode in landscape orientation
        // or portrait & unlocked global orientation
        final boolean isLandscape = DeviceUtils.isLandscape(requireContext());
        if (DeviceUtils.isTablet(activity)
                && (!globalScreenOrientationLocked(activity) || isLandscape)) {
            player.UIs().get(MainPlayerUi.class).ifPresent(MainPlayerUi::toggleFullscreen);
            return;
        }

        final int newOrientation = isLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

        activity.setRequestedOrientation(newOrientation);
    }

    /*
     * Will scroll down to description view after long click on moreOptionsButton
     * */
    @Override
    public void onMoreOptionsLongClicked() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        final ValueAnimator valueAnimator = ValueAnimator
                .ofInt(0, -binding.playerPlaceholder.getHeight());
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            behavior.setTopAndBottomOffset((int) animation.getAnimatedValue());
            binding.appBarLayout.requestLayout();
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(500);
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "showSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setStatusBarColor(ThemeHelper.resolveColorFromAttr(
                requireContext(), android.R.attr.colorPrimary));
    }

    private void hideSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "hideSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        // In multiWindow mode status bar is not transparent for devices with cutout
        // if I include this flag. So without it is better in this case
        final boolean isInMultiWindow = DeviceUtils.isInMultiWindow(activity);
        if (!isInMultiWindow) {
            visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(visibility);

        if (isInMultiWindow || isFullscreen()) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    // Listener implementation
    @Override
    public void hideSystemUiIfNeeded() {
        if (isFullscreen()
                && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            hideSystemUi();
        }
    }

    private boolean isFullscreen() {
        return isPlayerAvailable() && player.UIs().get(VideoPlayerUi.class)
                .map(VideoPlayerUi::isFullscreen).orElse(false);
    }

    private boolean playerIsNotStopped() {
        return isPlayerAvailable() && !player.isStopped();
    }

    private void restoreDefaultBrightness() {
        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (lp.screenBrightness == -1) {
            return;
        }

        // Restore the old  brightness when fragment.onPause() called or
        // when a player is in portrait
        lp.screenBrightness = -1;
        activity.getWindow().setAttributes(lp);
    }

    private void setupBrightness() {
        if (activity == null) {
            return;
        }

        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (!isFullscreen() || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            // Apply system brightness when the player is not in fullscreen
            restoreDefaultBrightness();
        } else {
            // Do not restore if user has disabled brightness gesture
            if (!PlayerHelper.isBrightnessGestureEnabled(activity)) {
                return;
            }
            // Restore already saved brightness level
            final float brightnessLevel = PlayerHelper.getScreenBrightness(activity);
            if (brightnessLevel == lp.screenBrightness) {
                return;
            }
            lp.screenBrightness = brightnessLevel;
            activity.getWindow().setAttributes(lp);
        }
    }

    private void checkLandscape() {
        if ((!player.isPlaying() && player.getPlayQueue() != playQueue)
                || player.getPlayQueue() == null) {
            setAutoPlay(true);
        }

        player.UIs().get(MainPlayerUi.class).ifPresent(MainPlayerUi::checkLandscape);
        // Let's give a user time to look at video information page if video is not playing
        if (globalScreenOrientationLocked(activity) && !player.isPlaying()) {
            player.play();
        }
    }

    /*
     * Means that the player fragment was swiped away via BottomSheetLayout
     * and is empty but ready for any new actions. See cleanUp()
     * */
    private boolean wasCleared() {
        return url == null;
    }

    @Nullable
    private StackItem findQueueInStack(final PlayQueue queue) {
        StackItem item = null;
        final Iterator<StackItem> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            final StackItem next = iterator.next();
            if (next.getPlayQueue().equals(queue)) {
                item = next;
                break;
            }
        }
        return item;
    }

    private void replaceQueueIfUserConfirms(final Runnable onAllow) {
        @Nullable final PlayQueue activeQueue = isPlayerAvailable() ? player.getPlayQueue() : null;

        // Player will have STATE_IDLE when a user pressed back button
        if (isClearingQueueConfirmationRequired(activity)
                && playerIsNotStopped()
                && activeQueue != null
                && !activeQueue.equals(playQueue)) {
            showClearingQueueConfirmation(onAllow);
        } else {
            onAllow.run();
        }
    }

    private void showClearingQueueConfirmation(final Runnable onAllow) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.clear_queue_confirmation_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    onAllow.run();
                    dialog.dismiss();
                }).show();
    }

    private void showExternalPlaybackDialog() {
        if (currentInfo == null) {
            return;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.select_quality_external_players);
        builder.setNeutralButton(R.string.open_in_browser, (dialog, i) ->
                ShareUtils.openUrlInBrowser(requireActivity(), url));

        final List<VideoStream> videoStreamsForExternalPlayers =
                ListHelper.getSortedStreamVideosList(
                        activity,
                        getUrlAndNonTorrentStreams(currentInfo.getVideoStreams()),
                        getUrlAndNonTorrentStreams(currentInfo.getVideoOnlyStreams()),
                        false,
                        false
                );

        if (videoStreamsForExternalPlayers.isEmpty()) {
            builder.setMessage(R.string.no_video_streams_available_for_external_players);
            builder.setPositiveButton(R.string.ok, null);

        } else {
            final int selectedVideoStreamIndexForExternalPlayers =
                    ListHelper.getDefaultResolutionIndex(activity, videoStreamsForExternalPlayers);
            final CharSequence[] resolutions = videoStreamsForExternalPlayers.stream()
                    .map(VideoStream::getResolution).toArray(CharSequence[]::new);

            builder.setSingleChoiceItems(resolutions, selectedVideoStreamIndexForExternalPlayers,
                    null);
            builder.setNegativeButton(R.string.cancel, null);
            builder.setPositiveButton(R.string.ok, (dialog, i) -> {
                final int index = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                // We don't have to manage the index validity because if there is no stream
                // available for external players, this code will be not executed and if there is
                // no stream which matches the default resolution, 0 is returned by
                // ListHelper.getDefaultResolutionIndex.
                // The index cannot be outside the bounds of the list as its always between 0 and
                // the list size - 1, .
                startOnExternalPlayer(activity, currentInfo,
                        videoStreamsForExternalPlayers.get(index));
            });
        }
        builder.show();
    }

    /*
     * Remove unneeded information while waiting for a next task
     * */
    private void cleanUp() {
        // New beginning
        stack.clear();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        playerHolder.stopService();
        setInitialData(0, null, "", null);
        currentInfo = null;
        updateOverlayData(null, null, null);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Bottom mini player
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * That's for Android TV support. Move focus from main fragment to the player or back
     * based on what is currently selected
     *
     * @param toMain if true than the main fragment will be focused or the player otherwise
     */
    private void moveFocusToMainFragment(final boolean toMain) {
        setupBrightness();
        final ViewGroup mainFragment = requireActivity().findViewById(R.id.fragment_holder);
        // Hamburger button steels a focus even under bottomSheet
        final Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        final int afterDescendants = ViewGroup.FOCUS_AFTER_DESCENDANTS;
        final int blockDescendants = ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        if (toMain) {
            mainFragment.setDescendantFocusability(afterDescendants);
            toolbar.setDescendantFocusability(afterDescendants);
            ((ViewGroup) requireView()).setDescendantFocusability(blockDescendants);
            // Only focus the mainFragment if the mainFragment (e.g. search-results)
            // or the toolbar (e.g. Textfield for search) don't have focus.
            // This was done to fix problems with the keyboard input, see also #7490
            if (!mainFragment.hasFocus() && !toolbar.hasFocus()) {
                mainFragment.requestFocus();
            }
        } else {
            mainFragment.setDescendantFocusability(blockDescendants);
            toolbar.setDescendantFocusability(blockDescendants);
            ((ViewGroup) requireView()).setDescendantFocusability(afterDescendants);
            // Only focus the player if it not already has focus
            if (!binding.getRoot().hasFocus()) {
                binding.detailThumbnailRootLayout.requestFocus();
            }
        }
    }

    /**
     * When the mini player exists the view underneath it is not touchable.
     * Bottom padding should be equal to the mini player's height in this case
     *
     * @param showMore whether main fragment should be expanded or not
     */
    private void manageSpaceAtTheBottom(final boolean showMore) {
        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        final ViewGroup holder = requireActivity().findViewById(R.id.fragment_holder);
        final int newBottomPadding;
        if (showMore) {
            newBottomPadding = 0;
        } else {
            newBottomPadding = peekHeight;
        }
        if (holder.getPaddingBottom() == newBottomPadding) {
            return;
        }
        holder.setPadding(holder.getPaddingLeft(),
                holder.getPaddingTop(),
                holder.getPaddingRight(),
                newBottomPadding);
    }

    private void setupBottomPlayer() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();

        final FrameLayout bottomSheetLayout = activity.findViewById(R.id.fragment_player_holder);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetBehavior.setState(lastStableBottomSheetState);
        updateBottomSheetState(lastStableBottomSheetState);

        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false);
            bottomSheetBehavior.setPeekHeight(peekHeight);
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.overlayLayout.setAlpha(MAX_OVERLAY_ALPHA);
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                binding.overlayLayout.setAlpha(0);
                setOverlayElementsClickable(false);
            }
        }

        bottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                updateBottomSheetState(newState);

                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(true);

                        bottomSheetBehavior.setPeekHeight(0);
                        cleanUp();
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        moveFocusToMainFragment(false);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);
                        // Disable click because overlay buttons located on top of buttons
                        // from the player
                        setOverlayElementsClickable(false);
                        hideSystemUiIfNeeded();
                        // Conditions when the player should be expanded to fullscreen
                        if (DeviceUtils.isLandscape(requireContext())
                                && isPlayerAvailable()
                                && player.isPlaying()
                                && !isFullscreen()
                                && !DeviceUtils.isTablet(activity)) {
                            player.UIs().get(MainPlayerUi.class)
                                    .ifPresent(MainPlayerUi::toggleFullscreen);
                        }
                        setOverlayLook(binding.appBarLayout, behavior, 1);
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);

                        // Re-enable clicks
                        setOverlayElementsClickable(true);
                        if (isPlayerAvailable()) {
                            player.UIs().get(MainPlayerUi.class)
                                    .ifPresent(MainPlayerUi::closeItemsList);
                        }
                        setOverlayLook(binding.appBarLayout, behavior, 0);
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                    case BottomSheetBehavior.STATE_SETTLING:
                        if (isFullscreen()) {
                            showSystemUi();
                        }
                        if (isPlayerAvailable()) {
                            player.UIs().get(MainPlayerUi.class).ifPresent(ui -> {
                                if (ui.isControlsVisible()) {
                                    ui.hideControls(0, 0);
                                }
                            });
                        }
                        break;
                    case BottomSheetBehavior.STATE_HALF_EXPANDED:
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                setOverlayLook(binding.appBarLayout, behavior, slideOffset);
            }
        };

        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);

        // User opened a new page and the player will hide itself
        activity.getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    private void updateOverlayPlayQueueButtonVisibility() {
        final boolean isPlayQueueEmpty =
                player == null // no player => no play queue :)
                        || player.getPlayQueue() == null
                        || player.getPlayQueue().isEmpty();
        if (binding != null) {
            // binding is null when rotating the device...
            binding.overlayPlayQueueButton.setVisibility(
                    isPlayQueueEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void updateOverlayData(@Nullable final String overlayTitle,
                                   @Nullable final String uploader,
                                   @Nullable final String thumbnailUrl) {
        binding.overlayTitleTextView.setText(isEmpty(overlayTitle) ? "" : overlayTitle);
        binding.overlayChannelTextView.setText(isEmpty(uploader) ? "" : uploader);
        binding.overlayThumbnail.setImageDrawable(null);
        PicassoHelper.loadDetailsThumbnail(thumbnailUrl).tag(PICASSO_VIDEO_DETAILS_TAG)
                .into(binding.overlayThumbnail);
    }

    private void setOverlayPlayPauseImage(final boolean playerIsPlaying) {
        final int drawable = playerIsPlaying
                ? R.drawable.ic_pause
                : R.drawable.ic_play_arrow;
        binding.overlayPlayPauseButton.setImageResource(drawable);
    }

    private void setOverlayLook(final AppBarLayout appBar,
                                final AppBarLayout.Behavior behavior,
                                final float slideOffset) {
        // SlideOffset < 0 when mini player is about to close via swipe.
        // Stop animation in this case
        if (behavior == null || slideOffset < 0) {
            return;
        }
        binding.overlayLayout.setAlpha(Math.min(MAX_OVERLAY_ALPHA, 1 - slideOffset));
        // These numbers are not special. They just do a cool transition
        behavior.setTopAndBottomOffset(
                (int) (-binding.detailThumbnailImageView.getHeight() * 2 * (1 - slideOffset) / 3));
        appBar.requestLayout();
    }

    private void setOverlayElementsClickable(final boolean enable) {
        binding.overlayThumbnail.setClickable(enable);
        binding.overlayThumbnail.setLongClickable(enable);
        binding.overlayMetadataLayout.setClickable(enable);
        binding.overlayMetadataLayout.setLongClickable(enable);
        binding.overlayButtonsLayout.setClickable(enable);
        binding.overlayPlayQueueButton.setClickable(enable);
        binding.overlayPlayPauseButton.setClickable(enable);
        binding.overlayCloseButton.setClickable(enable);
    }

    // helpers to check the state of player and playerService
    boolean isPlayerAvailable() {
        return (player != null);
    }

    boolean isPlayerServiceAvailable() {
        return (playerService != null);
    }

    boolean isPlayerAndPlayerServiceAvailable() {
        return (player != null && playerService != null);
    }

    public Optional<View> getRoot() {
        if (player == null) {
            return Optional.empty();
        }

        return player.UIs().get(VideoPlayerUi.class)
                .map(playerUi -> playerUi.getBinding().getRoot());
    }

    private void updateBottomSheetState(final int newState) {
        bottomSheetState = newState;
        if (newState != BottomSheetBehavior.STATE_DRAGGING
                && newState != BottomSheetBehavior.STATE_SETTLING) {
            lastStableBottomSheetState = newState;
        }
    }
}
