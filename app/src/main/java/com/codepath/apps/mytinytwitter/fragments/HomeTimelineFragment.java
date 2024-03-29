package com.codepath.apps.mytinytwitter.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.activeandroid.ActiveAndroid;
import com.codepath.apps.mytinytwitter.listeners.EndlessRecyclerViewScrollListener;
import com.codepath.apps.mytinytwitter.models.Tweet;
import com.codepath.apps.mytinytwitter.utils.MyGson;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;

public class HomeTimelineFragment extends TweetsListFragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        /********************** SwipeRefreshLayout **********************/
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the network request has completed successfully.
                fetchTimelineAsync();
            }
        });
        /********************** end of SwipeRefreshLayout **********************/

        /********************** RecyclerView **********************/
        // Set layout manager to position the items
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        rvTweets.setLayoutManager(linearLayoutManager);
        // Add the scroll listener
        rvTweets.addOnScrollListener(new EndlessRecyclerViewScrollListener(linearLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount) {
                Tweet lastTweet = mTweetList.get(mTweetList.size() - 1);
                nextMaxId = lastTweet.getIdStr();
                populateTimeline(tweetsCount, nextMaxId);
            }
        });
        /********************** end of RecyclerView **********************/

        // first request
        // from dev.twitter.com:
        // To use max_id correctly, an application’s first request to a timeline endpoint should only specify a count.
        populateTimeline(tweetsCount, null);

        return view;
    }

    private void fetchTimelineAsync() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                populateTimeline(tweetsCount, null);
                swipeContainer.setRefreshing(false);
            }
        }, 1000);
    }

    // 1. send an API request to get the timeline json
    // 2. fill the RecyclerView by creating the tweet objects from the json
    private void populateTimeline(int count, String maxId) {
        if (maxId == null || maxId.trim().isEmpty()) {
            clearAdapter();
        }

        twitterClient.getHomeTimeline(count, maxId, new JsonHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                if (statusCode == 429) {
                    // 429 Too Many Requests
                    Snackbar.make(getView(),
                            "Sent too many requests exceeding Twitter Rate limit! Try again later",
                            Snackbar.LENGTH_LONG).show();
                } else {
                    Snackbar.make(getView(),
                            "Problem with loading timeline... Try again later",
                            Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                String responseString = response.toString();
                Type collectionType = new TypeToken<List<Tweet>>() {
                }.getType();
                Gson gson = MyGson.getMyGson();
                List<Tweet> loadedTweetList = gson.fromJson(responseString, collectionType);
                // if the size of loadedTweetList is 1, it implies that there is no more tweets to be loaded.
                if (loadedTweetList.size() != 1) {
                    addAllToAdapter(loadedTweetList);

                    // use Transaction to save multiple Tweets at once
                    ActiveAndroid.beginTransaction();
                    try {
                        for (Tweet tweet : loadedTweetList) {
                            tweet.save();
                        }
                        ActiveAndroid.setTransactionSuccessful();
                    }
                    finally {
                        ActiveAndroid.endTransaction();
                    }
                }
            }
        });
    }
}