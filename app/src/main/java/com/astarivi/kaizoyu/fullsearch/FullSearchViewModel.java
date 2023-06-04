package com.astarivi.kaizoyu.fullsearch;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.astarivi.kaizoyu.R;
import com.astarivi.kaizoyu.core.models.Result;
import com.astarivi.kaizoyu.core.search.IndependentResultSearcher;
import com.astarivi.kaizoyu.databinding.ActivityFullsearchBinding;
import com.astarivi.kaizoyu.utils.Data;
import com.astarivi.kaizoyu.utils.Threading;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.Future;


public class FullSearchViewModel extends ViewModel {
    private final MutableLiveData<ArrayList<Result>> results = new MutableLiveData<>();
    private Future searchingFuture = null;

    public MutableLiveData<ArrayList<Result>> getResults() {
        return results;
    }

    public boolean hasSearch() {
        return results.getValue() != null;
    }

    public void searchAnime(
            @NotNull String search,
            @NotNull ActivityFullsearchBinding binding,
            @NotNull Context context
    ) {
        if (searchingFuture != null && !searchingFuture.isDone()) {
            Toast.makeText(
                    context,
                    context.getString(R.string.anime_busy),
                    Toast.LENGTH_SHORT
            ).show(
            );
            return;
        }

        binding.searchResults.setVisibility(View.INVISIBLE);
        binding.noResultsPrompt.setVisibility(View.GONE);
        binding.loadingBar.setVisibility(View.VISIBLE);

        searchingFuture = Threading.submitTask(
                Threading.TASK.INSTANT,
                () -> results.postValue(
                    new IndependentResultSearcher(
                            Data.getUserHttpClient()
                    ).searchEpisode(
                            search
                    )
                )
        );
    }
}