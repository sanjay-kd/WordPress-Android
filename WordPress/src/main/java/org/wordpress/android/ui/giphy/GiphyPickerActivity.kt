package org.wordpress.android.ui.giphy

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.SearchView.OnQueryTextListener
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import kotlinx.android.synthetic.main.media_picker_activity.*
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.ui.giphy.GiphyMediaViewHolder.ThumbnailViewDimensions
import org.wordpress.android.ui.media.MediaPreviewActivity
import org.wordpress.android.util.AniUtils
import org.wordpress.android.util.DisplayUtils
import org.wordpress.android.util.image.ImageManager
import org.wordpress.android.viewmodel.giphy.GiphyMediaViewModel
import org.wordpress.android.viewmodel.giphy.GiphyPickerViewModel
import javax.inject.Inject

/**
 * Allows searching of gifs from Giphy
 */
class GiphyPickerActivity : AppCompatActivity() {
    /**
     * Used for loading images in [GiphyMediaViewHolder]
     */
    @Inject lateinit var imageManager: ImageManager

    private lateinit var viewModel: GiphyPickerViewModel

    private val gridColumnCount: Int by lazy { if (DisplayUtils.isLandscape(this)) 4 else 3 }

    /**
     * Passed to the [GiphyMediaViewHolder] which will be used as its dimensions
     */
    private val thumbnailViewDimensions: ThumbnailViewDimensions by lazy {
        val width = DisplayUtils.getDisplayPixelWidth(this) / gridColumnCount
        ThumbnailViewDimensions(width = width, height = (width * 0.75).toInt())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as WordPress).component().inject(this)

        viewModel = ViewModelProviders.of(this).get(GiphyPickerViewModel::class.java)

        // We are intentionally reusing this layout since the UI is very similar.
        setContentView(R.layout.media_picker_activity)

        initializeToolbar()
        initializeRecyclerView()
        initializeSearchView()
        initializeSelectionBar()
        initializePreviewHandlers()
    }

    /**
     * Show the back arrow.
     */
    private fun initializeToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Configure the RecyclerView to use [GiphyPickerPagedListAdapter] and display the items in a grid
     */
    private fun initializeRecyclerView() {
        val pagedListAdapter = GiphyPickerPagedListAdapter(
                imageManager = imageManager,
                thumbnailViewDimensions = thumbnailViewDimensions,
                onMediaViewClickListener = viewModel::toggleSelected
        )

        recycler.apply {
            layoutManager = GridLayoutManager(this@GiphyPickerActivity, gridColumnCount)
            adapter = pagedListAdapter
        }

        // Update the RecyclerView when new items arrive from the API
        viewModel.mediaViewModelPagedList.observe(this, Observer {
            pagedListAdapter.submitList(it)
        })
    }

    /**
     * Configure the search view to execute search when the keyboard's Done button is pressed.
     */
    private fun initializeSearchView() {
        search_view.queryHint = getString(R.string.giphy_search_hint)

        search_view.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search_view.clearFocus()
                viewModel.search(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return true
            }
        })
    }

    /**
     * Configure the selection bar and its labels when the [GiphyPickerViewModel] selected items change
     */
    private fun initializeSelectionBar() {
        viewModel.selectionBarIsVisible.observe(this, Observer {
            // Do nothing if the [viewModel.selectionBarIsVisible] has not been initialized with a value yet. The
            // selection bar is hidden by default anyway so we don't need to worry in this case.
            val isVisible = it ?: return@Observer
            val selectionBar: ViewGroup = container_selection_bar

            // Do nothing if the selection bar is already in the visibility state that we want it to be
            if (isVisible && selectionBar.visibility == View.VISIBLE ||
                    !isVisible && selectionBar.visibility != View.VISIBLE) {
                return@Observer
            }

            // Animate show/hide and adjust the RecyclerView layout so it is not covered by the selection bar. We
            // probably could have used a ConstraintLayout to do the layout for us.
            val recyclerViewLayoutParams = recycler.layoutParams as RelativeLayout.LayoutParams
            if (isVisible) {
                AniUtils.animateBottomBar(selectionBar, true)

                recyclerViewLayoutParams.addRule(RelativeLayout.ABOVE, R.id.container_selection_bar)
            } else {
                AniUtils.animateBottomBar(selectionBar, false)

                recyclerViewLayoutParams.addRule(RelativeLayout.ABOVE, 0)
            }
        })

        // Update the "Add" and "Preview" labels to include the number of items. For example, "Add 7" and "Preview 7".
        //
        // We do not change to labels back to the original text if the number of items go back to zero because that
        // causes a weird UX. The selection bar is animated to disappear at that time and it looks weird if the labels
        // change to just "Add" and "Preview" too.
        viewModel.selectedMediaViewModelList.observe(this, Observer {
            val selectedCount = it?.size ?: 0
            if (selectedCount > 0) {
                text_preview.text = getString(R.string.preview_count, selectedCount)
                text_add.text = getString(R.string.add_count, selectedCount)
            }
        })
    }

    /**
     * Set up listener for the Preview button
     */
    private fun initializePreviewHandlers() {
        text_preview.setOnClickListener {
            val mediaViewModels = viewModel.selectedMediaViewModelList.value?.values?.toList()
            if (mediaViewModels != null && mediaViewModels.isNotEmpty()) {
                showPreview(mediaViewModels)
            }
        }
    }

    /**
     * Show the images of the given [mediaViewModels] in [MediaPreviewActivity]
     *
     * @param mediaViewModels A non-empty list
     */
    private fun showPreview(mediaViewModels: List<GiphyMediaViewModel>) {
        check(mediaViewModels.isNotEmpty())

        val uris = mediaViewModels.map { it.previewImageUri.toString() }
        MediaPreviewActivity.showPreview(this, null, ArrayList(uris), uris.first())
    }

    /**
     * Close this Activity when the up button is pressed
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
