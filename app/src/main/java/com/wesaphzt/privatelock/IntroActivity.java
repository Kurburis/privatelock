package com.wesaphzt.privatelock;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import static com.wesaphzt.privatelock.service.LockService.motionDetector;

public class IntroActivity extends AppIntro {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //fullscreen
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.statusBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        //slider pages
        SliderPage sliderPageOne = new SliderPage();
        sliderPageOne.setTitle(getResources().getString(R.string.slider_page_one_title));
        sliderPageOne.setDescription(getString(R.string.slider_page_one_desc));
        sliderPageOne.setImageDrawable(R.drawable.ic_intro_lock);
        sliderPageOne.setBgColor(ContextCompat.getColor(this, R.color.colorPrimary));
        addSlide(AppIntroFragment.newInstance(sliderPageOne));

        SliderPage sliderPageTwo = new SliderPage();
        sliderPageTwo.setTitle(getResources().getString(R.string.slider_page_two_title));
        sliderPageTwo.setDescription(getResources().getString(R.string.slider_page_two_desc));
        sliderPageTwo.setImageDrawable(R.drawable.ic_intro_iris);
        sliderPageTwo.setBgColor(ContextCompat.getColor(this, R.color.colorIntroGrey));
        addSlide(AppIntroFragment.newInstance(sliderPageTwo));

        SliderPage sliderPageThree = new SliderPage();
        sliderPageThree.setTitle(getResources().getString(R.string.slider_page_three_title));
        sliderPageThree.setDescription(getResources().getString(R.string.slider_page_three_desc));
        sliderPageThree.setImageDrawable(R.drawable.ic_intro_shield);
        sliderPageThree.setBgColor(ContextCompat.getColor(this, R.color.colorIntroGreen));
        addSlide(AppIntroFragment.newInstance(sliderPageThree));

        //options
        setFadeAnimation();
        showSkipButton(false);
        setProgressButtonEnabled(true);

        //setBarColor(getResources().getColor(R.color.colorPrimary));
        //setSeparatorColor(getResources().getColor(R.color.white));
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        if (motionDetector != null) {
            motionDetector.reset();
        }
        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
    }
}