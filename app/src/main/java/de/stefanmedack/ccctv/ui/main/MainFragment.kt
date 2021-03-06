/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package de.stefanmedack.ccctv.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v17.leanback.app.BrowseFragment
import android.support.v17.leanback.widget.*
import android.support.v4.app.ActivityOptionsCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import de.stefanmedack.ccctv.C3TVApp
import de.stefanmedack.ccctv.R
import de.stefanmedack.ccctv.ui.BrowseErrorActivity
import de.stefanmedack.ccctv.ui.details.DetailsActivity
import de.stefanmedack.ccctv.util.EVENT
import de.stefanmedack.ccctv.util.applySchedulers
import info.metadude.kotlin.library.c3media.RxC3MediaService
import info.metadude.kotlin.library.c3media.models.Conference
import info.metadude.kotlin.library.c3media.models.Event
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseFragment() {

    val TAG = "MainFragment"

    val GRID_ITEM_WIDTH = 200
    val GRID_ITEM_HEIGHT = 200

    @Inject
    lateinit var c3MediaService: RxC3MediaService

    private lateinit var mRowsAdapter: ArrayObjectAdapter

    // TODO move into BaseFragment
    lateinit var mDisposables: CompositeDisposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        C3TVApp.graph.inject(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)
        setupUIElements()
        loadConferencesAsync()
        setupEventListeners()
    }

    override fun onDestroy() {
        mDisposables.clear()
        super.onDestroy()
    }

    private fun setupUIElements() {
        title = getString(R.string.browse_title)
        // over title
        headersState = BrowseFragment.HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true

        // set fastLane (or headers) background color
        brandColor = ContextCompat.getColor(activity, R.color.fastlane_background)
        // set search icon color
        searchAffordanceColor = ContextCompat.getColor(activity, R.color.search_opaque)
    }

    private fun showRows(conferences: List<Conference>) {
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        val cardPresenter = CardPresenter()

        for ((index, conference) in conferences.withIndex()) {
            val listRowAdapter = ArrayObjectAdapter(cardPresenter)
            listRowAdapter.addAll(0, conference.events)
            val header = HeaderItem(index.toLong(), conference.title ?: "")
            mRowsAdapter.add(ListRow(header, listRowAdapter))
        }

        val gridHeader = HeaderItem(conferences.size.toLong(), "PREFERENCES")

        val mGridPresenter = GridItemPresenter()
        val gridRowAdapter = ArrayObjectAdapter(mGridPresenter)
        gridRowAdapter.add(resources.getString(R.string.grid_view))
        gridRowAdapter.add(getString(R.string.error_fragment))
        gridRowAdapter.add(resources.getString(R.string.personal_settings))
        mRowsAdapter.add(ListRow(gridHeader, gridRowAdapter))

        adapter = mRowsAdapter
    }

    private fun setupEventListeners() {
        setOnSearchClickedListener {
            Toast.makeText(activity, "Implement your own in-app search", Toast.LENGTH_LONG)
                    .show()
        }

        onItemViewClickedListener = ItemViewClickedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(itemViewHolder: Presenter.ViewHolder, item: Any,
                                   rowViewHolder: RowPresenter.ViewHolder, row: Row) {

            if (item is Event) {
                Log.d(TAG, "Item: " + item.toString())
                val intent = Intent(activity, DetailsActivity::class.java)
                intent.putExtra(EVENT, item)

                val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity,
                        (itemViewHolder.view as ImageCardView).mainImageView,
                        DetailsActivity.SHARED_ELEMENT_NAME).toBundle()
                activity.startActivity(intent, bundle)
            } else if (item is String) {
                if (item.contains(getString(R.string.error_fragment))) {
                    val intent = Intent(activity, BrowseErrorActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(activity, item, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(activity, R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return Presenter.ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
            (viewHolder.view as TextView).text = item as String
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}
    }

    // *********************************************
    // TODO encapsulate (MVP/MVVM/MVI)
    // *********************************************

    private fun loadConferencesAsync() {
        val loadConferencesSingle = c3MediaService.getConferences()
                .applySchedulers()
                .map { it.conferences ?: listOf() }
                .flattenAsObservable { it }
                .map { it.url?.substringAfterLast('/')?.toInt() ?: -1 }
                .filter { it > 0 }
                .flatMap {
                    c3MediaService.getConference(it)
                            .applySchedulers()
                            .toObservable()
                }

                .toSortedList(compareBy(Conference::title))
        // TODO use this for grouping conferences in the future
        //                .groupBy { it.type() }
        //                .flatMap { it.toList().toObservable() }
        //                .toMap { it[0].type() }

        mDisposables = CompositeDisposable()
        mDisposables.add(loadConferencesSingle
                .subscribeBy(// named arguments for lambda Subscribers
                        onSuccess = { showRows(it) },
                        // TODO proper error handling
                        onError = { it.printStackTrace() }
                ))
    }
}