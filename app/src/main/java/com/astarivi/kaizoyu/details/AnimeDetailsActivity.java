package com.astarivi.kaizoyu.details;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.astarivi.kaizolib.kitsu.Kitsu;
import com.astarivi.kaizolib.kitsu.model.KitsuAnime;
import com.astarivi.kaizoyu.R;
import com.astarivi.kaizoyu.core.models.Anime;
import com.astarivi.kaizoyu.core.models.base.ImageSize;
import com.astarivi.kaizoyu.core.models.base.ModelType;
import com.astarivi.kaizoyu.core.models.base.AnimeBase;
import com.astarivi.kaizoyu.core.models.local.LocalAnime;
import com.astarivi.kaizoyu.core.storage.database.data.seen.SeenAnime;
import com.astarivi.kaizoyu.core.storage.database.data.seen.SeenAnimeDao;
import com.astarivi.kaizoyu.core.theme.AppCompatActivityTheme;
import com.astarivi.kaizoyu.databinding.ActivityAnimeDetailsBinding;
import com.astarivi.kaizoyu.details.gui.AnimeEpisodesFragment;
import com.astarivi.kaizoyu.details.gui.AnimeInfoFragment;
import com.astarivi.kaizoyu.details.gui.adapters.DetailsTabAdapter;
import com.astarivi.kaizoyu.gui.adapters.BackInterceptAdapter;
import com.astarivi.kaizoyu.utils.Data;
import com.astarivi.kaizoyu.utils.Threading;
import com.astarivi.kaizoyu.utils.Utils;
import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.jetbrains.annotations.NotNull;

import io.github.tonnyl.spark.Spark;


public class AnimeDetailsActivity extends AppCompatActivityTheme {
    private ActivityAnimeDetailsBinding binding;
    private DetailsTabAdapter detailsTabAdapter;
    private Spark spark;
    private AnimeBase anime;
    private ModelType.Anime animeType;
    private SeenAnime seenAnime;

    // The bundle must contain the following keys to create this Details Activity:
    // "type" as ModelType.Anime Enum value String representation
    // "anime" as the Anime parcelable
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAnimeDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle bundle = getIntent().getExtras();

        // Can't create stuff without a bundle
        if (bundle == null) return;

        String type = bundle.getString("type");

        if (type == null || type.equals("")) {
            finish();
            return;
        }

        try {
            animeType = ModelType.Anime.valueOf(type);
        } catch(IllegalArgumentException e) {
            finish();
            return;
        }

        anime = Utils.getAnimeFromBundle(bundle, animeType);

        if (anime == null) {
            finish();
            return;
        }

        // Bundle is valid, continue

        TabLayout tabLayout = binding.informationTabLayout;

        if (savedInstanceState != null) {
            int index = savedInstanceState.getInt("index");
            TabLayout.Tab tab = tabLayout.getTabAt(index);

            if (tab != null) {
                tab.select();
            }
        }

        binding.cancelButton.setOnClickListener(v -> finish());

        setLoadingScreen();

        if (animeType == ModelType.Anime.LOCAL) {
            LocalAnime localAnime = (LocalAnime) anime;

            Threading.submitTask(Threading.TASK.INSTANT, () -> {
                Kitsu kitsu = new Kitsu(
                        Data.getUserHttpClient()
                );

                KitsuAnime ktAnime = kitsu.getAnimeById(
                        Integer.parseInt(localAnime.getKitsuAnime().id)
                );

                if (ktAnime != null) {
                    anime = new Anime(ktAnime);
                    animeType = ModelType.Anime.BASE;
                }

                binding.getRoot().post(this::initializeFavorite);
            });

            return;
        }

        initializeFavorite();
    }

    @Override
    protected void onSaveInstanceState(@NonNull @NotNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt("index", binding.informationTabLayout.getSelectedTabPosition());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void triggerFavoriteRefresh() {
        Threading.submitTask(Threading.TASK.DATABASE, () -> {
            int animeKtId = Integer.parseInt(anime.getKitsuAnime().id);
            SeenAnimeDao seenAnimeDao = Data.getRepositories().getSeenAnimeRepository().getAnimeDao();
            seenAnime = seenAnimeDao.getFromKitsuId(animeKtId);

            if (seenAnime != null && seenAnime.isFavorite())
                binding.favoriteButton.setImageResource(R.drawable.ic_favorite_active);
        });
    }

    private void initializeFavorite() {
        if (seenAnime != null) {
            if (seenAnime.isFavorite())
                binding.favoriteButton.setImageResource(R.drawable.ic_favorite_active);
            continueInitialization();
            return;
        }

        Threading.submitTask(Threading.TASK.DATABASE, () -> {
            int animeKtId = Integer.parseInt(anime.getKitsuAnime().id);

            SeenAnimeDao seenAnimeDao = Data.getRepositories().getSeenAnimeRepository().getAnimeDao();
            seenAnime = seenAnimeDao.getFromKitsuId(animeKtId);

            if (seenAnime != null && seenAnime.isFavorite()) {
                binding.getRoot().post(() ->
                    binding.favoriteButton.setImageResource(R.drawable.ic_favorite_active)
                );
            }

            binding.getRoot().post(this::continueInitialization);
        });
    }

    private void continueInitialization() {
        if (spark != null) {
            spark.stopAnimation();
            binding.loadingScreen.setVisibility(View.GONE);
            binding.posterImage.setVisibility(View.VISIBLE);
        }

        TabLayout tabLayout = binding.informationTabLayout;

        binding.backButton.setOnClickListener(v -> finish());

        String coverUrl = anime.getImageUrlFromSize(ImageSize.TINY, true);
        String posterUrl = anime.getImageUrlFromSize(ImageSize.TINY, false);

        if (coverUrl != null)
            Glide.with(this)
                    .load(coverUrl)
                    .centerCrop()
                    .into(binding.coverImage);

        if (posterUrl != null)
            Glide.with(this)
                    .load(posterUrl)
                    .placeholder(R.drawable.ic_general_placeholder)
                    .into(binding.posterImage);

        configureTabAdapter(tabLayout);

        binding.animeTitle.setText(anime.getDisplayTitle());

        // Favorite Button

        binding.favoriteButton.setOnClickListener(v -> {
            binding.favoriteButton.setEnabled(false);
            Threading.submitTask(Threading.TASK.DATABASE, () -> {
                SeenAnimeDao seenAnimeDao = Data.getRepositories().getSeenAnimeRepository().getAnimeDao();
                seenAnime = seenAnimeDao.getFromKitsuId(
                        Integer.parseInt(anime.getKitsuAnime().id)
                );

                if (seenAnime == null) {
                    seenAnime = new SeenAnime(
                            anime.toEmbeddedDatabaseObject(),
                            System.currentTimeMillis()
                    );

                    seenAnime.id = (int) seenAnimeDao.insert(
                            seenAnime
                    );
                }

                if (seenAnime.isFavorite()) {
                    Data.getRepositories()
                            .getFavoriteAnimeRepository()
                            .deleteFromRelated(seenAnime);
                    binding.getRoot().post(() ->
                            binding.favoriteButton.setImageResource(R.drawable.ic_favorite));
                } else {
                    Data.getRepositories()
                            .getFavoriteAnimeRepository()
                            .createFromRelated(seenAnime, System.currentTimeMillis());
                    binding.getRoot().post(() ->
                            binding.favoriteButton.setImageResource(R.drawable.ic_favorite_active));
                }
                binding.getRoot().post(() ->
                        binding.favoriteButton.setEnabled(true)
                );
            });
        });
    }

    private void setLoadingScreen() {
        binding.posterImage.setVisibility(View.INVISIBLE);
        binding.loadingScreen.setVisibility(View.VISIBLE);
        spark = new Spark.Builder()
                .setView(binding.loadingScreenOverlay)
                .setDuration(3000)
                .setAnimList(Spark.ANIM_BLUE_PURPLE)
                .build();
        spark.startAnimation();
    }

    private void configureTabAdapter(TabLayout tabLayout){
        ViewPager2 viewPager = binding.informationViewPager;

        Bundle bundle = new Bundle();
        bundle.putParcelable("anime", ((Anime) anime));

        detailsTabAdapter = new DetailsTabAdapter(this, bundle);
        viewPager.setAdapter(detailsTabAdapter);

        final String[] tabTitles = new String[]{
                getString(R.string.activity_details_tab1),
                getString(R.string.activity_details_tab2)
        };

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(tabTitles[position])).attach();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int tapPosition = tab.getPosition();

                if (tapPosition == 0) {
                    binding.backButton.setVisibility(View.VISIBLE);
                    binding.favoriteButton.setVisibility(View.VISIBLE);
                    binding.coverImage.setVisibility(View.VISIBLE);
                    binding.posterImage.setVisibility(View.VISIBLE);
                    binding.upperFade.setVisibility(View.VISIBLE);

                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) binding.spacerView.getLayoutParams();
                    layoutParams.topMargin = -12;
                    binding.spacerView.setLayoutParams(layoutParams);
                    return;
                }

                binding.backButton.setVisibility(View.GONE);
                binding.favoriteButton.setVisibility(View.GONE);
                binding.coverImage.setVisibility(View.GONE);
                binding.posterImage.setVisibility(View.GONE);
                binding.upperFade.setVisibility(View.GONE);

                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) binding.spacerView.getLayoutParams();
                layoutParams.topMargin = 0;
                binding.spacerView.setLayoutParams(layoutParams);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                int currentTabPosition = viewPager.getCurrentItem();
                int reselectedTabPosition = tab.getPosition();

                if (currentTabPosition != reselectedTabPosition) return;

                Fragment fragment = detailsTabAdapter.getFragment(reselectedTabPosition);

                if (fragment instanceof AnimeEpisodesFragment) {
                    ((AnimeEpisodesFragment) fragment).scrollTop();
                    return;
                }

                if (fragment instanceof AnimeInfoFragment) {
                    ((AnimeInfoFragment) fragment).scrollTop();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        int position = binding.informationViewPager.getCurrentItem();

        Fragment fragment = detailsTabAdapter.getFragment(position);

        if (fragment == null) {
            super.onBackPressed();
            return;
        }

        if (fragment instanceof BackInterceptAdapter && ((BackInterceptAdapter) fragment).shouldFragmentInterceptBack()) {
            return;
        }

        super.onBackPressed();
    }

    public void setCurrentFragment(int index) {
        binding.informationViewPager.setCurrentItem(index);
    }

    public void changePagerInteractivity(boolean value) {
        binding.informationViewPager.setUserInputEnabled(value);
    }
}