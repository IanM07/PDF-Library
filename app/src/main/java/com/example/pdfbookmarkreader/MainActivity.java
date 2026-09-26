package com.example.pdfbookmarkreader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int OPEN_PDF_REQUEST = 42;

    private static final String PREFS = "reader_state";
    private static final String LAST_URI = "last_uri";
    private static final String LIBRARY_URIS = "library_uris";
    private static final String PAGE_PREFIX = "page_";
    private static final String TITLE_PREFIX = "title_";
    private static final String LAST_OPENED_PREFIX = "last_opened_";

    private static final int BG = Color.rgb(19, 22, 28);
    private static final int SURFACE = Color.rgb(28, 33, 41);
    private static final int SURFACE_ALT = Color.rgb(36, 43, 54);
    private static final int CARD = Color.rgb(34, 40, 50);
    private static final int BORDER = Color.rgb(63, 76, 95);
    private static final int ACCENT = Color.rgb(61, 139, 255);
    private static final int ACCENT_DARK = Color.rgb(38, 111, 219);
    private static final int TEXT = Color.rgb(240, 244, 250);
    private static final int TEXT_MUTED = Color.rgb(168, 180, 198);

    private PdfRenderer renderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor descriptor;
    private Uri currentUri;
    private int pageIndex = 0;

    private FrameLayout screenHost;
    private LinearLayout readerScreen;
    private LinearLayout libraryScreen;

    private PdfPageView pageView;
    private TextView titleLabel;
    private TextView pageLabel;
    private Button prevButton;
    private Button nextButton;
    private Button libraryButton;

    private GridLayout libraryGrid;
    private TextView libraryEmptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        restoreLastDocument();
    }

    private void buildUi() {
        screenHost = new FrameLayout(this);
        screenHost.setBackgroundColor(BG);

        buildReaderScreen();
        buildLibraryScreen();

        screenHost.addView(readerScreen);
        screenHost.addView(libraryScreen);
        setContentView(screenHost);
    }

    private void buildReaderScreen() {
        readerScreen = new LinearLayout(this);
        readerScreen.setOrientation(LinearLayout.VERTICAL);
        readerScreen.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(12));
        header.setBackground(makePanelDrawable(SURFACE, BORDER));

        titleLabel = new TextView(this);
        titleLabel.setTextColor(TEXT);
        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        titleLabel.setSingleLine(true);
        titleLabel.setEllipsize(TextUtils.TruncateAt.END);
        titleLabel.setText("PDF Library");

        pageLabel = new TextView(this);
        pageLabel.setTextColor(TEXT_MUTED);
        pageLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        pageLabel.setText("Open your library to start reading");

        header.addView(titleLabel);
        header.addView(pageLabel);

        pageView = new PdfPageView();

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(12), dp(10), dp(12), dp(14));
        footer.setBackground(makePanelDrawable(SURFACE, BORDER));

        prevButton = makePrimaryButton("Previous");
        libraryButton = makeSecondaryButton("Open Library");
        nextButton = makePrimaryButton("Next");

        footer.addView(prevButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        addSpacer(footer, dp(10));
        footer.addView(libraryButton, new LinearLayout.LayoutParams(0, dp(52), 1.25f));
        addSpacer(footer, dp(10));
        footer.addView(nextButton, new LinearLayout.LayoutParams(0, dp(52), 1f));

        readerScreen.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        readerScreen.addView(pageView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        readerScreen.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        prevButton.setOnClickListener(v -> showPage(pageIndex - 1));
        nextButton.setOnClickListener(v -> showPage(pageIndex + 1));
        libraryButton.setOnClickListener(v -> showLibraryScreen());
        updateControls();
    }

    private void buildLibraryScreen() {
        libraryScreen = new LinearLayout(this);
        libraryScreen.setOrientation(LinearLayout.VERTICAL);
        libraryScreen.setBackgroundColor(BG);
        libraryScreen.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(16), dp(16), dp(12));
        header.setBackground(makePanelDrawable(SURFACE, BORDER));

        TextView title = new TextView(this);
        title.setText("Your Library");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("Select a PDF by its cover. Your place is saved for every file.");
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);

        header.addView(title);
        header.addView(subtitle);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout libraryContent = new LinearLayout(this);
        libraryContent.setOrientation(LinearLayout.VERTICAL);
        libraryContent.setPadding(dp(12), dp(12), dp(12), dp(12));

        libraryEmptyText = new TextView(this);
        libraryEmptyText.setText("No PDFs loaded yet.");
        libraryEmptyText.setTextColor(TEXT_MUTED);
        libraryEmptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        libraryEmptyText.setPadding(dp(8), dp(12), dp(8), dp(12));

        libraryGrid = new GridLayout(this);
        libraryGrid.setColumnCount(2);
        libraryGrid.setUseDefaultMargins(false);

        libraryContent.addView(libraryEmptyText);
        libraryContent.addView(libraryGrid);
        scrollView.addView(libraryContent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(12), dp(10), dp(12), dp(14));
        footer.setBackground(makePanelDrawable(SURFACE, BORDER));

        Button loadPdfButton = makePrimaryButton("Load PDF");
        footer.addView(loadPdfButton, new LinearLayout.LayoutParams(0, dp(52), 1f));
        loadPdfButton.setOnClickListener(v -> openPicker());

        libraryScreen.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        libraryScreen.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        libraryScreen.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void restoreLastDocument() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(LAST_URI, null);
        if (saved != null) {
            try {
                openDocument(Uri.parse(saved), false);
                return;
            } catch (Exception ignored) {
                clearLastDocument();
            }
        }
        showLibraryScreen();
    }

    private void showReaderScreen() {
        libraryScreen.setVisibility(View.GONE);
        readerScreen.setVisibility(View.VISIBLE);
    }

    private void showLibraryScreen() {
        rebuildLibraryGrid();
        readerScreen.setVisibility(View.GONE);
        libraryScreen.setVisibility(View.VISIBLE);
    }

    private Button makePrimaryButton(String text) {
        Button button = makeBaseButton(text);
        button.setBackground(makeButtonDrawable(ACCENT, ACCENT_DARK));
        button.setTextColor(Color.WHITE);
        return button;
    }

    private Button makeSecondaryButton(String text) {
        Button button = makeBaseButton(text);
        button.setBackground(makePanelDrawable(SURFACE_ALT, BORDER));
        button.setTextColor(TEXT);
        return button;
    }

    private Button makeBaseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private GradientDrawable makePanelDrawable(int fillColor, int borderColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(0));
        drawable.setStroke(dp(1), borderColor);
        return drawable;
    }

    private GradientDrawable makeCardDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(CARD);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private GradientDrawable makeButtonDrawable(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void addSpacer(LinearLayout parent, int width) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(width, 1));
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_PDF_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_PDF_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                final int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Some document providers do not support persisted permissions.
            }

            addUriToLibrary(uri);
            openDocument(uri, true);
        }
    }

    private void addUriToLibrary(Uri uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> rawSet = prefs.getStringSet(LIBRARY_URIS, Collections.emptySet());
        LinkedHashSet<String> updated = new LinkedHashSet<>(rawSet);
        updated.remove(uri.toString());
        updated.add(uri.toString());

        prefs.edit()
                .putStringSet(LIBRARY_URIS, updated)
                .putString(TITLE_PREFIX + uri.toString(), resolveDisplayName(uri))
                .apply();
    }

    private List<LibraryEntry> loadLibraryEntries() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> rawSet = prefs.getStringSet(LIBRARY_URIS, Collections.emptySet());
        List<LibraryEntry> entries = new ArrayList<>();
        for (String value : rawSet) {
            Uri uri = Uri.parse(value);
            String title = prefs.getString(TITLE_PREFIX + value, resolveDisplayName(uri));
            long lastOpened = prefs.getLong(LAST_OPENED_PREFIX + value, 0L);
            int savedPage = prefs.getInt(PAGE_PREFIX + value, 0);
            entries.add(new LibraryEntry(uri, title, savedPage, lastOpened));
        }
        entries.sort((a, b) -> Long.compare(b.lastOpened, a.lastOpened));
        return entries;
    }

    private void rebuildLibraryGrid() {
        libraryGrid.removeAllViews();
        List<LibraryEntry> entries = loadLibraryEntries();
        libraryEmptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.max(dp(140), (screenWidth - dp(36)) / 2);

        for (int i = 0; i < entries.size(); i++) {
            LibraryEntry entry = entries.get(i);
            View card = createLibraryCard(entry, cardWidth);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardWidth;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            card.setLayoutParams(params);
            libraryGrid.addView(card);
        }
    }

    private View createLibraryCard(LibraryEntry entry, int cardWidth) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setBackground(makeCardDrawable());
        card.setClickable(true);
        card.setFocusable(true);

        ImageView coverView = new ImageView(this);
        coverView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        coverView.setBackgroundColor(Color.WHITE);

        int coverHeight = Math.round(cardWidth * 1.35f);
        Bitmap cover = renderThumbnail(entry.uri, cardWidth - dp(20), coverHeight);
        if (cover != null) {
            coverView.setImageBitmap(cover);
        } else {
            coverView.setImageBitmap(makePlaceholderCover(cardWidth - dp(20), coverHeight, entry.title));
        }

        TextView title = new TextView(this);
        title.setText(entry.title);
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(10), 0, 0);

        TextView subtitle = new TextView(this);
        subtitle.setText("Resume on page " + (entry.savedPage + 1));
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setPadding(0, dp(4), 0, 0);

        card.addView(coverView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, coverHeight));
        card.addView(title);
        card.addView(subtitle);

        card.setOnClickListener(v -> openDocument(entry.uri, true));
        return card;
    }

    private Bitmap makePlaceholderCover(int width, int height, String title) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(width, 1), Math.max(height, 1), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(ACCENT);
        canvas.drawRect(0, 0, bitmap.getWidth(), dp(24), barPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(35, 40, 48));
        titlePaint.setTextSize(dp(10));
        titlePaint.setFakeBoldText(true);

        int x = dp(12);
        int y = dp(50);
        String[] words = title.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (titlePaint.measureText(candidate) > bitmap.getWidth() - dp(24)) {
                canvas.drawText(line.toString(), x, y, titlePaint);
                y += dp(18);
                line = new StringBuilder(word);
                if (y > bitmap.getHeight() - dp(24)) break;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (y <= bitmap.getHeight() - dp(24) && line.length() > 0) {
            canvas.drawText(line.toString(), x, y, titlePaint);
        }
        return bitmap;
    }

    private Bitmap renderThumbnail(Uri uri, int maxWidth, int maxHeight) {
        ParcelFileDescriptor thumbDescriptor = null;
        PdfRenderer thumbRenderer = null;
        PdfRenderer.Page thumbPage = null;
        try {
            thumbDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (thumbDescriptor == null) return null;
            thumbRenderer = new PdfRenderer(thumbDescriptor);
            if (thumbRenderer.getPageCount() == 0) return null;
            thumbPage = thumbRenderer.openPage(0);

            float ratio = (float) thumbPage.getWidth() / thumbPage.getHeight();
            int bmpWidth = maxWidth;
            int bmpHeight = Math.max(1, Math.round(bmpWidth / ratio));
            if (bmpHeight > maxHeight) {
                bmpHeight = maxHeight;
                bmpWidth = Math.max(1, Math.round(bmpHeight * ratio));
            }

            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, bmpWidth), Math.max(1, bmpHeight), Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            thumbPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (thumbPage != null) thumbPage.close();
            if (thumbRenderer != null) thumbRenderer.close();
            if (thumbDescriptor != null) {
                try { thumbDescriptor.close(); } catch (IOException ignored) {}
            }
        }
    }

    private String resolveDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "Untitled PDF";
    }

    private void openDocument(Uri uri, boolean showError) {
        closeDocument();
        try {
            ContentResolver resolver = getContentResolver();
            descriptor = resolver.openFileDescriptor(uri, "r");
            if (descriptor == null) throw new IOException("Unable to open file");
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() == 0) throw new IOException("PDF contains no pages");

            currentUri = uri;
            addUriToLibrary(uri);

            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            prefs.edit()
                    .putString(LAST_URI, uri.toString())
                    .putLong(LAST_OPENED_PREFIX + uri.toString(), System.currentTimeMillis())
                    .apply();

            int savedPage = prefs.getInt(PAGE_PREFIX + uri.toString(), 0);
            pageIndex = Math.max(0, Math.min(savedPage, renderer.getPageCount() - 1));
            showReaderScreen();
            showPage(pageIndex);
        } catch (Exception e) {
            closeDocument();
            if (showError) Toast.makeText(this, "Could not open this PDF", Toast.LENGTH_LONG).show();
            showLibraryScreen();
        }
    }

    private void showPage(int newIndex) {
        if (renderer == null || newIndex < 0 || newIndex >= renderer.getPageCount()) return;

        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }

        pageIndex = newIndex;
        currentPage = renderer.openPage(pageIndex);
        renderCurrentPage();
        saveProgress();
        updateControls();
    }

    private void renderCurrentPage() {
        if (currentPage == null) return;
        if (pageView.getWidth() <= 0 || pageView.getHeight() <= 0) {
            pageView.post(this::renderCurrentPage);
            return;
        }

        int viewW = pageView.getWidth();
        int viewH = pageView.getHeight();
        float pageRatio = (float) currentPage.getWidth() / currentPage.getHeight();
        float viewRatio = (float) viewW / viewH;

        int bmpW;
        int bmpH;
        if (pageRatio > viewRatio) {
            bmpW = Math.max(1, viewW * 2);
            bmpH = Math.max(1, Math.round(bmpW / pageRatio));
        } else {
            bmpH = Math.max(1, viewH * 2);
            bmpW = Math.max(1, Math.round(bmpH * pageRatio));
        }

        Bitmap bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        pageView.setBitmap(bitmap);
    }

    private void saveProgress() {
        if (currentUri == null) return;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(PAGE_PREFIX + currentUri.toString(), pageIndex)
                .apply();
    }

    private void updateControls() {
        boolean open = renderer != null;
        int count = open ? renderer.getPageCount() : 0;
        prevButton.setEnabled(open && pageIndex > 0);
        nextButton.setEnabled(open && pageIndex < count - 1);

        if (open && currentUri != null) {
            titleLabel.setText(resolveStoredTitle(currentUri));
            pageLabel.setText("Page " + (pageIndex + 1) + " of " + count);
        } else {
            titleLabel.setText("PDF Library");
            pageLabel.setText("Open your library to start reading");
        }
    }

    private String resolveStoredTitle(Uri uri) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getString(TITLE_PREFIX + uri.toString(), resolveDisplayName(uri));
    }

    private void clearLastDocument() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(LAST_URI).apply();
    }

    private void closeDocument() {
        pageView.setBitmap(null);
        if (currentPage != null) {
            currentPage.close();
            currentPage = null;
        }
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) {}
            descriptor = null;
        }
        currentUri = null;
        updateControls();
    }

    @Override
    protected void onDestroy() {
        saveProgress();
        closeDocument();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (currentPage != null) pageView.post(this::renderCurrentPage);
        rebuildLibraryGrid();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class LibraryEntry {
        final Uri uri;
        final String title;
        final int savedPage;
        final long lastOpened;

        LibraryEntry(Uri uri, String title, int savedPage, long lastOpened) {
            this.uri = uri;
            this.title = title;
            this.savedPage = savedPage;
            this.lastOpened = lastOpened;
        }
    }

    private class PdfPageView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private Bitmap bitmap;
        private float scale = 1f;
        private float translateX = 0f;
        private float translateY = 0f;
        private float lastX;
        private float lastY;
        private boolean dragging;

        PdfPageView() {
            super(MainActivity.this);
            setBackgroundColor(BG);

            scaleDetector = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float oldScale = scale;
                    scale = Math.max(1f, Math.min(5f, scale * detector.getScaleFactor()));
                    float factor = scale / oldScale;
                    float focusX = detector.getFocusX();
                    float focusY = detector.getFocusY();
                    translateX = focusX - (focusX - translateX) * factor;
                    translateY = focusY - (focusY - translateY) * factor;
                    clampTranslation();
                    invalidate();
                    return true;
                }
            });

            gestureDetector = new GestureDetector(MainActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) { return true; }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.05f) {
                        scale = 1f;
                        translateX = translateY = 0f;
                    } else {
                        scale = 2f;
                        translateX = getWidth() / 2f - e.getX() * 2f;
                        translateY = getHeight() / 2f - e.getY() * 2f;
                        clampTranslation();
                    }
                    invalidate();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (scale > 1.05f || e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    float dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy)) {
                        if (dx < 0) showPage(pageIndex + 1);
                        else showPage(pageIndex - 1);
                        return true;
                    }
                    return false;
                }
            });
        }

        void setBitmap(Bitmap newBitmap) {
            if (bitmap != null && bitmap != newBitmap && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = newBitmap;
            scale = 1f;
            translateX = translateY = 0f;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) {
                paint.setColor(TEXT_MUTED);
                paint.setTextSize(dp(18));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Open a book from your library", getWidth() / 2f, getHeight() / 2f, paint);
                return;
            }

            float baseScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float baseX = (getWidth() - bitmap.getWidth() * baseScale) / 2f;
            float baseY = (getHeight() - bitmap.getHeight() * baseScale) / 2f;

            matrix.reset();
            matrix.postScale(baseScale * scale, baseScale * scale);
            matrix.postTranslate(baseX + translateX, baseY + translateY);
            canvas.drawBitmap(bitmap, matrix, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            if (event.getPointerCount() == 1 && scale > 1.05f && !scaleDetector.isInProgress()) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = event.getX();
                        lastY = event.getY();
                        dragging = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging) {
                            float x = event.getX();
                            float y = event.getY();
                            translateX += x - lastX;
                            translateY += y - lastY;
                            lastX = x;
                            lastY = y;
                            clampTranslation();
                            invalidate();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        break;
                }
            }
            return true;
        }

        private void clampTranslation() {
            if (bitmap == null) return;
            float baseScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float normalW = bitmap.getWidth() * baseScale;
            float normalH = bitmap.getHeight() * baseScale;
            float scaledW = normalW * scale;
            float scaledH = normalH * scale;
            float maxX = Math.max(0f, (scaledW - normalW) / 2f);
            float maxY = Math.max(0f, (scaledH - normalH) / 2f);
            translateX = Math.max(-maxX, Math.min(maxX, translateX));
            translateY = Math.max(-maxY, Math.min(maxY, translateY));
        }
    }
}
