package protect.card_locker;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.BarcodeFormat;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import protect.card_locker.preferences.Settings;

public class LoyaltyCardViewActivity extends CatimaAppCompatActivity
{
    private static final String TAG = "Catima";

    TextView cardIdFieldView;
    BottomSheetBehavior behavior;
    View bottomSheet;
    View bottomSheetContentWrapper;
    ImageView bottomSheetButton;
    TextView noteView;
    TextView groupsView;
    TextView balanceView;
    TextView expiryView;
    AppCompatTextView storeName;
    ImageButton maximizeButton;
    ImageView mainImage;
    ImageButton minimizeButton;
    ImageButton previousImageButton;
    ImageButton nextImageButton;
    View collapsingToolbarLayout;
    AppBarLayout appBarLayout;
    int loyaltyCardId;
    LoyaltyCard loyaltyCard;
    boolean rotationEnabled;
    DBHelper db;
    ImportURIHelper importURIHelper;
    Settings settings;

    String cardIdString;
    String barcodeIdString;
    BarcodeFormat format;

    FloatingActionButton editButton;

    Guideline centerGuideline;
    SeekBar barcodeScaler;

    Bitmap frontImageBitmap;
    Bitmap backImageBitmap;

    boolean starred;
    boolean backgroundNeedsDarkIcons;
    boolean isFullscreen = false;
    int mainImageIndex = 0;
    List<ImageType> imageTypes;
    boolean isBarcodeSupported = true;

    static final String STATE_IMAGEINDEX = "imageIndex";
    static final String STATE_FULLSCREEN = "isFullscreen";

    enum ImageType {
        NONE,
        BARCODE,
        IMAGE_FRONT,
        IMAGE_BACK
    }

    private void extractIntentFields(Intent intent)
    {
        final Bundle b = intent.getExtras();
        loyaltyCardId = b != null ? b.getInt("id") : 0;
        Log.d(TAG, "View activity: id=" + loyaltyCardId);
    }

    private Drawable getIcon(int icon, boolean dark)
    {
        Drawable unwrappedIcon = AppCompatResources.getDrawable(this, icon);
        assert unwrappedIcon != null;
        Drawable wrappedIcon = DrawableCompat.wrap(unwrappedIcon);
        if(dark)
        {
            DrawableCompat.setTint(wrappedIcon, Color.BLACK);
        }
        else
        {
            DrawableCompat.setTintList(wrappedIcon, null);
        }

        return wrappedIcon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mainImageIndex = savedInstanceState.getInt(STATE_IMAGEINDEX);
            isFullscreen = savedInstanceState.getBoolean(STATE_FULLSCREEN);
        }

        settings = new Settings(this);

        extractIntentFields(getIntent());

        setContentView(R.layout.loyalty_card_view_layout);

        db = new DBHelper(this);
        importURIHelper = new ImportURIHelper(this);

        cardIdFieldView = findViewById(R.id.cardIdView);
        bottomSheet = findViewById(R.id.bottom_sheet);
        bottomSheetContentWrapper = findViewById(R.id.bottomSheetContentWrapper);
        bottomSheetButton = findViewById(R.id.bottomSheetButton);
        noteView = findViewById(R.id.noteView);
        groupsView = findViewById(R.id.groupsView);
        balanceView = findViewById(R.id.balanceView);
        expiryView = findViewById(R.id.expiryView);
        storeName = findViewById(R.id.storeName);
        maximizeButton = findViewById(R.id.maximizeButton);
        mainImage = findViewById(R.id.mainImage);
        minimizeButton = findViewById(R.id.minimizeButton);
        previousImageButton = findViewById(R.id.previousImageButton);
        nextImageButton = findViewById(R.id.nextImageButton);
        collapsingToolbarLayout = findViewById(R.id.collapsingToolbarLayout);
        appBarLayout = findViewById(R.id.app_bar_layout);

        centerGuideline = findViewById(R.id.centerGuideline);
        centerGuideline.setGuidelinePercent(0.5f);
        barcodeScaler = findViewById(R.id.barcodeScaler);
        barcodeScaler.setProgress(100);
        maximizeButton.setBackgroundColor(getThemeColor());
        minimizeButton.setBackgroundColor(getThemeColor());
        previousImageButton.setBackgroundColor(getThemeColor());
        nextImageButton.setBackgroundColor(getThemeColor());
        bottomSheetButton.setBackgroundColor(getThemeColor());
        barcodeScaler.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                Log.d(TAG, "Progress is " + progress);
                Log.d(TAG, "Max is " + barcodeScaler.getMax());
                float scale = (float) progress / (float) barcodeScaler.getMax();
                Log.d(TAG, "Scaling to " + scale);

                if (imageTypes.get(mainImageIndex) == ImageType.BARCODE) {
                    redrawBarcodeAfterResize();
                }
                centerGuideline.setGuidelinePercent(0.5f * scale);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        rotationEnabled = true;

        // Allow making barcode fullscreen on tap
        maximizeButton.setOnClickListener(v -> setFullscreen(true));
        mainImage.setOnClickListener(v -> setFullscreen(!isFullscreen));
        minimizeButton.setOnClickListener(v -> setFullscreen(false));

        previousImageButton.setOnClickListener(v -> setMainImage(false));
        nextImageButton.setOnClickListener(v -> setMainImage(true));

        editButton = findViewById(R.id.fabEdit);
        editButton.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), LoyaltyCardEditActivity.class);
            Bundle bundle = new Bundle();
            bundle.putInt("id", loyaltyCardId);
            bundle.putBoolean("update", true);
            intent.putExtras(bundle);
            startActivity(intent);
            finish();
        });
        editButton.bringToFront();

        behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    editButton.hide();
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetButton.setImageResource(R.drawable.ic_baseline_arrow_drop_down_24);
                    editButton.hide();
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetButton.setImageResource(R.drawable.ic_baseline_arrow_drop_up_24);
                    if (!isFullscreen) {
                        editButton.show();
                    }

                    // Scroll bottomsheet content back to top
                    bottomSheetContentWrapper.setScrollY(0);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) { }
        });

        bottomSheetButton.setOnClickListener(v -> {
            if (behavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            } else {
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // Fix bottom sheet content sizing
        ViewTreeObserver viewTreeObserver = bottomSheet.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                bottomSheet.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int height = displayMetrics.heightPixels;
                int maxHeight = height - appBarLayout.getHeight() - bottomSheetButton.getHeight();
                Log.d(TAG, "Button sheet should be " + maxHeight + " pixels high");
                bottomSheetContentWrapper.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                maxHeight
                        )
                );
            }
        });
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);

        Log.i(TAG, "Received new intent");
        extractIntentFields(intent);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(STATE_IMAGEINDEX, mainImageIndex);
        savedInstanceState.putBoolean(STATE_FULLSCREEN, isFullscreen);
        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public void onResume()
    {
        super.onResume();

        Log.i(TAG, "To view card: " + loyaltyCardId);

        // The brightness value is on a scale from [0, ..., 1], where
        // '1' is the brightest. We attempt to maximize the brightness
        // to help barcode readers scan the barcode.
        Window window = getWindow();
        if(window != null)
        {
            WindowManager.LayoutParams attributes = window.getAttributes();

            if (settings.useMaxBrightnessDisplayingBarcode())
            {
                attributes.screenBrightness = 1F;
            }

            if (settings.getKeepScreenOn()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            if (settings.getDisableLockscreenWhileViewingCard()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD|
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }

            window.setAttributes(attributes);
        }

        loyaltyCard = db.getLoyaltyCard(loyaltyCardId);
        if(loyaltyCard == null)
        {
            Log.w(TAG, "Could not lookup loyalty card " + loyaltyCardId);
            Toast.makeText(this, R.string.noCardExistsError, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupOrientation();

        format = loyaltyCard.barcodeType;
        cardIdString = loyaltyCard.cardId;
        barcodeIdString = loyaltyCard.barcodeId;

        cardIdFieldView.setText(loyaltyCard.cardId);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(cardIdFieldView,
                settings.getFontSizeMin(settings.getLargeFont()), settings.getFontSizeMax(settings.getLargeFont()),
                1, TypedValue.COMPLEX_UNIT_SP);

        if(loyaltyCard.note.length() > 0)
        {
            noteView.setVisibility(View.VISIBLE);
            noteView.setText(loyaltyCard.note);
            noteView.setTextSize(settings.getFontSizeMax(settings.getMediumFont()));
        }
        else
        {
            noteView.setVisibility(View.GONE);
        }

        List<Group> loyaltyCardGroups = db.getLoyaltyCardGroups(loyaltyCardId);

        if(loyaltyCardGroups.size() > 0) {
            List<String> groupNames = new ArrayList<>();
            for (Group group : loyaltyCardGroups) {
                groupNames.add(group._id);
            }

            groupsView.setVisibility(View.VISIBLE);
            groupsView.setText(getString(R.string.groupsList, TextUtils.join(", ", groupNames)));
            groupsView.setTextSize(settings.getFontSizeMax(settings.getMediumFont()));
        }
        else
        {
            groupsView.setVisibility(View.GONE);
        }

        if(!loyaltyCard.balance.equals(new BigDecimal(0))) {
            balanceView.setVisibility(View.VISIBLE);
            balanceView.setText(getString(R.string.balanceSentence, Utils.formatBalance(this, loyaltyCard.balance, loyaltyCard.balanceType)));
            balanceView.setTextSize(settings.getFontSizeMax(settings.getMediumFont()));
        }
        else
        {
            balanceView.setVisibility(View.GONE);
        }

        if(loyaltyCard.expiry != null) {
            expiryView.setVisibility(View.VISIBLE);

            int expiryString = R.string.expiryStateSentence;
            if(Utils.hasExpired(loyaltyCard.expiry)) {
                expiryString = R.string.expiryStateSentenceExpired;
                expiryView.setTextColor(getResources().getColor(R.color.alert));
            }
            expiryView.setText(getString(expiryString, DateFormat.getDateInstance(DateFormat.LONG).format(loyaltyCard.expiry)));
            expiryView.setTextSize(settings.getFontSizeMax(settings.getMediumFont()));
        }
        else
        {
            expiryView.setVisibility(View.GONE);
        }
        expiryView.setTag(loyaltyCard.expiry);

        if (!isFullscreen) {
            makeBottomSheetVisibleIfUseful();
        }

        storeName.setText(loyaltyCard.store);
        storeName.setTextSize(settings.getFontSizeMax(settings.getLargeFont()));
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                storeName,
                settings.getFontSizeMin(settings.getLargeFont()),
                settings.getFontSizeMax(settings.getLargeFont()),
                1,
                TypedValue.COMPLEX_UNIT_DIP);

        int backgroundHeaderColor;
        if(loyaltyCard.headerColor != null)
        {
            backgroundHeaderColor = loyaltyCard.headerColor;
        }
        else
        {
            backgroundHeaderColor = LetterBitmap.getDefaultColor(this, loyaltyCard.store);
        }

        collapsingToolbarLayout.setBackgroundColor(backgroundHeaderColor);
        appBarLayout.setBackgroundColor(backgroundHeaderColor);

        int textColor;
        if(Utils.needsDarkForeground(backgroundHeaderColor))
        {
            textColor = Color.BLACK;
        }
        else
        {
            textColor = Color.WHITE;
        }
        storeName.setTextColor(textColor);
        ((Toolbar) findViewById(R.id.toolbar_landscape)).setTitleTextColor(textColor);

        // If the background is very bright, we should use dark icons
        backgroundNeedsDarkIcons = Utils.needsDarkForeground(backgroundHeaderColor);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setHomeAsUpIndicator(getIcon(R.drawable.ic_arrow_back_white, backgroundNeedsDarkIcons));
        }

        // Make notification area light if dark icons are needed
        if(Build.VERSION.SDK_INT >= 23)
        {
            window.getDecorView().setSystemUiVisibility(backgroundNeedsDarkIcons ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        }
        if(Build.VERSION.SDK_INT >= 21)
        {
            window.setStatusBarColor(Color.TRANSPARENT);
        }

        // Set shadow colour of store text so even same color on same color would be readable
        storeName.setShadowLayer(1, 1, 1, backgroundNeedsDarkIcons ? Color.BLACK : Color.WHITE);

        if (format != null && !BarcodeSelectorActivity.SUPPORTED_BARCODE_TYPES.contains(format.name())) {
            isBarcodeSupported = false;

            Toast.makeText(this, getString(R.string.unsupportedBarcodeType), Toast.LENGTH_LONG).show();
        } else if (format == null) {
            isBarcodeSupported = false;
        }

        imageTypes = new ArrayList<>();

        if (isBarcodeSupported) {
            imageTypes.add(ImageType.BARCODE);
        }

        frontImageBitmap = Utils.retrieveCardImage(this, loyaltyCard.id, true);
        backImageBitmap = Utils.retrieveCardImage(this, loyaltyCard.id, false);

        if (frontImageBitmap != null) {
            imageTypes.add(ImageType.IMAGE_FRONT);
        }

        if (backImageBitmap != null) {
            imageTypes.add(ImageType.IMAGE_BACK);
        }

        setFullscreen(isFullscreen);
    }

    @Override
    public void onBackPressed() {
        if (isFullscreen)
        {
            setFullscreen(false);
            return;
        }

        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.card_view_menu, menu);

        // Always calculate lockscreen icon, it may need a black color
        boolean lockBarcodeScreenOrientation = settings.getLockBarcodeScreenOrientation();
        MenuItem item = menu.findItem(R.id.action_lock_unlock);
        setOrientatonLock(item, lockBarcodeScreenOrientation);
        if(lockBarcodeScreenOrientation)
        {
            item.setVisible(false);
        }

        loyaltyCard = db.getLoyaltyCard(loyaltyCardId);
        starred = loyaltyCard.starStatus != 0;

        menu.findItem(R.id.action_share).setIcon(getIcon(R.drawable.ic_share_white, backgroundNeedsDarkIcons));

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (starred) {
            menu.findItem(R.id.action_star_unstar).setIcon(getIcon(R.drawable.ic_starred_white, backgroundNeedsDarkIcons));
            menu.findItem(R.id.action_star_unstar).setTitle(R.string.unstar);
        }
        else {
            menu.findItem(R.id.action_star_unstar).setIcon(getIcon(R.drawable.ic_unstarred_white, backgroundNeedsDarkIcons));
            menu.findItem(R.id.action_star_unstar).setTitle(R.string.star);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        switch(id)
        {
            case android.R.id.home:
                finish();
                break;

            case R.id.action_share:
                try {
                    importURIHelper.startShareIntent(Arrays.asList(loyaltyCard));
                } catch (UnsupportedEncodingException e) {
                    Toast.makeText(LoyaltyCardViewActivity.this, R.string.failedGeneratingShareURL, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
                return true;

            case R.id.action_lock_unlock:
                if(rotationEnabled)
                {
                    setOrientatonLock(item, true);
                }
                else
                {
                    setOrientatonLock(item, false);
                }
                rotationEnabled = !rotationEnabled;
                return true;

            case R.id.action_star_unstar:
                starred = !starred;
                db.updateLoyaltyCardStarStatus(loyaltyCardId, starred ? 1 : 0);
                invalidateOptionsMenu();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupOrientation()
    {
        Toolbar portraitToolbar = findViewById(R.id.toolbar);
        Toolbar landscapeToolbar = findViewById(R.id.toolbar_landscape);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "Detected landscape mode");

            setTitle(loyaltyCard.store);

            collapsingToolbarLayout.setVisibility(View.GONE);
            portraitToolbar.setVisibility(View.GONE);
            landscapeToolbar.setVisibility(View.VISIBLE);

            setSupportActionBar(landscapeToolbar);
        } else {
            Log.d(TAG, "Detected portrait mode");

            setTitle("");

            collapsingToolbarLayout.setVisibility(View.VISIBLE);
            portraitToolbar.setVisibility(View.VISIBLE);
            landscapeToolbar.setVisibility(View.GONE);

            setSupportActionBar(portraitToolbar);
        }

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
    private void setOrientatonLock(MenuItem item, boolean lock)
    {
        if(lock)
        {

            item.setIcon(getIcon(R.drawable.ic_lock_outline_white_24dp, backgroundNeedsDarkIcons));
            item.setTitle(R.string.unlockScreen);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
        else
        {
            item.setIcon(getIcon(R.drawable.ic_lock_open_white_24dp, backgroundNeedsDarkIcons));
            item.setTitle(R.string.lockScreen);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void makeBottomSheetVisibleIfUseful()
    {
        if (noteView.getVisibility() == View.VISIBLE || groupsView.getVisibility() == View.VISIBLE || balanceView.getVisibility() == View.VISIBLE || expiryView.getVisibility() == View.VISIBLE) {
            bottomSheet.setVisibility(View.VISIBLE);
        }
        else
        {
            bottomSheet.setVisibility(View.GONE);
        }
    }

    private void drawBarcode() {
        if (format != null) {
            new BarcodeImageWriterTask(
                    getApplicationContext(),
                    mainImage,
                    barcodeIdString != null ? barcodeIdString : cardIdString,
                    format,
                    null,
                    false,
                    null)
                    .execute();
        }
    }

    private void redrawBarcodeAfterResize() {
        if (format != null) {
            mainImage.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            mainImage.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                            Log.d(TAG, "ImageView size now known");
                            drawBarcode();
                        }
                    });
        };
    }

    private void drawMainImage(Integer index, boolean waitForResize) {
        if (index == null) {
            mainImage.setVisibility(View.GONE);
            return;
        }

        ImageType wantedImageType = imageTypes.get(index);

        if (wantedImageType == ImageType.BARCODE) {
            if (waitForResize) {
                redrawBarcodeAfterResize();
            } else {
                drawBarcode();
            }
            mainImage.setBackgroundColor(Color.WHITE);
        } else if (wantedImageType == ImageType.IMAGE_FRONT) {
            mainImage.setImageBitmap(frontImageBitmap);
            mainImage.setBackgroundColor(Color.TRANSPARENT);
        } else if (wantedImageType == ImageType.IMAGE_BACK) {
            mainImage.setImageBitmap(backImageBitmap);
            mainImage.setBackgroundColor(Color.TRANSPARENT);
        } else {
            throw new IllegalArgumentException("Unknown image type: " + wantedImageType);
        }

        mainImage.setVisibility(View.VISIBLE);
    }

    private void setMainImage(boolean next) {
        int newIndex = mainImageIndex + (next ? 1 : -1);

        Log.e(TAG, String.valueOf(mainImageIndex));
        Log.e(TAG, String.valueOf(newIndex));

        if (newIndex == -1 || newIndex >= imageTypes.size()) {
            return;
        }

        mainImageIndex = newIndex;

        previousImageButton.setEnabled(newIndex != 0);
        nextImageButton.setEnabled(newIndex < imageTypes.size());

        Log.e(TAG, String.valueOf(previousImageButton.isEnabled()));
        Log.e(TAG, String.valueOf(nextImageButton.isEnabled()));

        drawMainImage(newIndex, false);
    }

    /**
     * When enabled, hides the status bar and moves the barcode to the top of the screen.
     *
     * The purpose of this function is to make sure the barcode can be scanned from the phone
     * by machines which offer no space to insert the complete device.
     */
    private void setFullscreen(boolean enabled) {
        ActionBar actionBar = getSupportActionBar();

        if (enabled && !imageTypes.isEmpty()) {
            Log.d(TAG, "Move into fullscreen");

            drawMainImage(mainImageIndex, true);

            // Hide maximize and show minimize button and scaler
            maximizeButton.setVisibility(View.GONE);
            previousImageButton.setVisibility(View.GONE);
            nextImageButton.setVisibility(View.GONE);
            minimizeButton.setVisibility(View.VISIBLE);
            barcodeScaler.setVisibility(View.VISIBLE);

            // Hide actionbar
            if(actionBar != null)
            {
                actionBar.hide();
            }

            // Hide toolbars
            //
            // Appbar needs to be invisible and have padding removed
            // Or the barcode will be centered instead of on top of the screen
            // Don't ask me why...
            appBarLayout.setVisibility(View.INVISIBLE);
            collapsingToolbarLayout.setVisibility(View.GONE);
            findViewById(R.id.toolbar_landscape).setVisibility(View.GONE);

            // Hide other UI elements
            cardIdFieldView.setVisibility(View.GONE);
            bottomSheet.setVisibility(View.GONE);
            behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            editButton.hide();

            // Set Android to fullscreen mode
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
        else
        {
            Log.d(TAG, "Move out of fullscreen");

            // Reset center guideline
            barcodeScaler.setProgress(100);

            drawMainImage(mainImageIndex, true);

            // Show maximize and hide minimize button and scaler
            maximizeButton.setVisibility(imageTypes.isEmpty() ? View.GONE : View.VISIBLE);
            previousImageButton.setVisibility(imageTypes.size() >= 2 ? View.VISIBLE : View.INVISIBLE);
            nextImageButton.setVisibility(imageTypes.size() >= 2 ? View.VISIBLE : View.INVISIBLE);

            minimizeButton.setVisibility(View.GONE);
            barcodeScaler.setVisibility(View.GONE);

            // Show actionbar
            if(actionBar != null)
            {
                actionBar.show();
            }

            // Show appropriate toolbar
            // And restore 24dp paddingTop for appBarLayout
            appBarLayout.setVisibility(View.VISIBLE);
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            setupOrientation();

            // Show other UI elements
            cardIdFieldView.setVisibility(View.VISIBLE);
            makeBottomSheetVisibleIfUseful();
            editButton.show();

            // Unset fullscreen mode
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility()
                            & ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            & ~View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }

        isFullscreen = enabled;
    }
}
