package com.nordef.voicememos.on_bording_screen;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nordef.voicememos.MainActivity;
import com.nordef.voicememos.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

public class OnBoardingScreenActivity extends AppCompatActivity {

    private ViewPager viewPager;
    private LinearLayout ll_container_dots;

    private OnBoardingSliderAdapter adapter;
    private TextView[] textViewDots;
    private Button btn_next, btn_previous;

    private int currrentPosition = 0;

    String title1, title2, title3, title4, description1, description2, description3, description4;
    private String[] slideTitles;
    private String[] slideDescription;

    boolean is_main_activity_opened = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_on_boarding_screen);

        Intent intent = getIntent();
        if (intent.hasExtra("from_fragment")) {
            is_main_activity_opened = true;
        }

        viewPager = findViewById(R.id.view_pager);
        ll_container_dots = findViewById(R.id.ll_container_dots);
        btn_next = findViewById(R.id.btn_next);
        btn_previous = findViewById(R.id.btn_previous);

        initText();

        adapter = new OnBoardingSliderAdapter(this, slideTitles, slideDescription,
                getImageParams());
        viewPager.setAdapter(adapter);

        addIndicator(0);
        btn_previous.setVisibility(View.GONE);
        viewPager.addOnPageChangeListener(listener);

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btn_next.getText().equals(getString(R.string.obs_btn_finish))) {
                    firstLaunchComplete();
                    if (!is_main_activity_opened)
                        startActivity(new Intent(OnBoardingScreenActivity.this,
                                MainActivity.class));
                    finish();
                }

                viewPager.setCurrentItem(currrentPosition + 1);
            }
        });

        btn_previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewPager.setCurrentItem(currrentPosition - 1);
            }
        });
    }

    private void firstLaunchComplete() {
        SharedPreferences.Editor editor = getSharedPreferences("info",
                Context.MODE_PRIVATE).edit();
        editor.putBoolean("haveData", true);
        editor.commit();
    }

    public void addIndicator(int position) {
        textViewDots = new TextView[4];
        ll_container_dots.removeAllViews();

        for (int i = 0; i < textViewDots.length; i++) {
            textViewDots[i] = new TextView(this);
            textViewDots[i].setText(Html.fromHtml("&#8226;"));
            textViewDots[i].setTextSize(35);
            textViewDots[i].setTextColor(Color.WHITE);

            ll_container_dots.addView(textViewDots[i]);
        }

        textViewDots[position].setTextColor(getResources().getColor(R.color.colorAccent));
    }

    ViewPager.OnPageChangeListener listener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageSelected(int position) {
            addIndicator(position);
            currrentPosition = position;
            if (position == 0) {
                btn_previous.setVisibility(View.GONE);
            } else if (position == textViewDots.length - 1) {
                btn_next.setText(getString(R.string.obs_btn_finish));
            } else {
                btn_previous.setVisibility(View.VISIBLE);
                btn_previous.setVisibility(View.VISIBLE);
                btn_next.setText(getString(R.string.obs_btn_next));
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }
    };

    private void initText() {
        title1 = getString(R.string.obs_title1);
        title2 = getString(R.string.obs_title2);
        title3 = getString(R.string.obs_title3);
        title4 = getString(R.string.obs_title4);
        slideTitles = new String[]{
                title1,
                title2,
                title3,
                title4
        };

        description1 = getString(R.string.obs_description1);
        description2 = getString(R.string.obs_description2);
        description3 = getString(R.string.obs_description3);
        description4 = getString(R.string.obs_description4);
        slideDescription = new String[]{
                description1,
                description2,
                description3,
                description4
        };
    }

    private RelativeLayout.LayoutParams getImageParams() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int height = size.y;
        height /= (2 * 1.4);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(height, height);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.setMargins(0, height / 10, 0, 0);

        return params;
    }

    public static final int MY_PERMISSIONS_REQUEST_ACCESS_CODE = 1;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_CODE: {
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * checking  permissions at Runtime.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public void checkPermissions() {
        final String[] requiredPermissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        final List<String> neededPermissions = new ArrayList<>();
        for (final String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            } else
                Toast.makeText(this, getString(R.string.permission_accepted), Toast.LENGTH_SHORT).show();
        }
        if (!neededPermissions.isEmpty()) {
            requestPermissions(neededPermissions.toArray(new String[]{}),
                    MY_PERMISSIONS_REQUEST_ACCESS_CODE);
        }
    }
}
