package co.kaush.msusf.movies

import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import co.kaush.msusf.MSActivity
import co.kaush.msusf.R
import co.kaush.msusf.movies.MSMainVm.MSMainVmFactory
import co.kaush.msusf.movies.MSMovieEvent.AddToHistoryEvent
import co.kaush.msusf.movies.MSMovieEvent.RestoreFromHistoryEvent
import co.kaush.msusf.movies.MSMovieEvent.ScreenLoadEvent
import co.kaush.msusf.movies.MSMovieEvent.SearchMovieEvent
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class MSMovieActivity : MSActivity() {

    @Inject
    lateinit var movieRepo: MSMovieRepository

    private lateinit var viewModel: MSMainVm
    private lateinit var listAdapter: MSMovieSearchHistoryAdapter

    private var disposables: CompositeDisposable = CompositeDisposable()
    private val historyItemClick: PublishSubject<MSMovie> = PublishSubject.create()

    private val spinner: CircularProgressDrawable by lazy {
        val circularProgressDrawable = CircularProgressDrawable(this)
        circularProgressDrawable.strokeWidth = 5f
        circularProgressDrawable.centerRadius = 30f
        circularProgressDrawable.start()
        circularProgressDrawable
    }

    override fun inject(activity: MSActivity) {
        app.appComponent.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupListView()

        viewModel = ViewModelProviders.of(
            this,
            MSMainVmFactory(app, movieRepo)
        ).get(MSMainVm::class.java)
    }

    override fun onResume() {
        super.onResume()

        val screenLoadEvents: Observable<ScreenLoadEvent> = Observable.just(ScreenLoadEvent)
        val searchMovieEvents: Observable<SearchMovieEvent> = RxView.clicks(ms_mainScreen_searchBtn)
            .map { SearchMovieEvent(ms_mainScreen_searchText.text.toString()) }
        val addToHistoryEvents: Observable<AddToHistoryEvent> = RxView.clicks(ms_mainScreen_poster)
            .map {
                ms_mainScreen_poster.growShrink()
                AddToHistoryEvent(MSMovie(title = "todo", result = true), emptyList())
            }
        val restoreFromHistoryEvents: Observable<RestoreFromHistoryEvent> = historyItemClick
            .map { RestoreFromHistoryEvent(it) }

        disposables.add(
            Observable.merge(
                screenLoadEvents,
                searchMovieEvents,
                addToHistoryEvents,
                restoreFromHistoryEvents
            )
                .subscribe(
                    { viewModel.processInput(it) },
                    { Timber.e(it, "error processing input ")}
                )
        )

        disposables.add(
            viewModel
                .viewState
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { vs ->

                        vs.searchBoxText?.let {
                            ms_mainScreen_searchText.setText(it)
                        }
                        ms_mainScreen_title.text = vs.searchedMovieTitle
                        ms_mainScreen_rating.text = vs.searchedMovieRating

                        vs.searchedMoviePoster
                            .takeIf { it.isNotBlank() }
                            ?.let {
                                Glide.with(ctx)
                                    .load(vs.searchedMoviePoster)
                                    .apply {
                                        RequestOptions.fitCenterTransform().placeholder(spinner)
                                    }
                                    .into(ms_mainScreen_poster)
                            } ?: run {
                            ms_mainScreen_poster.setImageResource(0)
                        }

                        listAdapter.submitList(vs.adapterList)
                    },
                    { Timber.w(it, "something went terribly wrong processing view state") }
                )
        )

        disposables.add(
            viewModel
                .viewEffects
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        when (it) {
                            is MSMovieViewEffect.AddedToHistoryToastEffect -> {
                                Toast.makeText(this, "added to history", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    { Timber.w(it, "something went terribly wrong processing view effects") }
                )
        )
    }

    override fun onPause() {
        super.onPause()

        disposables.clear()
    }

    private fun setupListView() {
        val layoutManager = LinearLayoutManager(this, HORIZONTAL, false)
        ms_mainScreen_searchHistory.layoutManager = layoutManager

        val dividerItemDecoration = DividerItemDecoration(this, HORIZONTAL)
        dividerItemDecoration.setDrawable(
            ContextCompat.getDrawable(this, R.drawable.ms_list_divider_space)!!
        )
        ms_mainScreen_searchHistory.addItemDecoration(dividerItemDecoration)

        listAdapter = MSMovieSearchHistoryAdapter { historyItemClick.onNext(it) }
        ms_mainScreen_searchHistory.adapter = listAdapter
    }
}
