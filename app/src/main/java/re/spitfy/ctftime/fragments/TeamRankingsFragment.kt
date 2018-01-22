package re.spitfy.ctftime.fragments

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.*
import android.widget.*
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView

import com.google.firebase.firestore.*
import kotlinx.android.synthetic.main.appbar_main.*
import re.spitfy.ctftime.R
import re.spitfy.ctftime.adapters.RankingsFirestoreAdapter
import re.spitfy.ctftime.data.Team
import re.spitfy.ctftime.utils.RankingsRecyclerViewScrollListener

class TeamRankingsFragment : android.support.v4.app.Fragment()
{
    private lateinit var year: String
    private lateinit var rankingsYear : String
    private lateinit var adapter : RankingsFirestoreAdapter
    private lateinit var db : FirebaseFirestore
    private lateinit var collectionRef : CollectionReference
    private lateinit var progressBarLoadingRankings : CardView
    private lateinit var progressBarLoadingRankingsBig : ProgressBar
    private lateinit var recyclerView : RecyclerView
    private lateinit var rankingsRecyclerViewScrollListener : RankingsRecyclerViewScrollListener
    private lateinit var listenerRegistration : ListenerRegistration
    private lateinit var lastDocument : DocumentSnapshot
    private var rankingsList : MutableList<Team> = ArrayList()
    private var page = 0
    private var moreData = true

    companion object
    {
        const val TAG = "TeamRankingsFragment"
        const val PAGE_LENGTH : Long = 50
        val years = listOf("2017", "2016", "2015", "2014", "2013", "2012", "2011")
        fun newInstance(year: String): TeamRankingsFragment {
            val args = Bundle()
            args.putString("YEAR", year)
            val fragment = TeamRankingsFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val yearArg = arguments?.getString("YEAR")
        if (yearArg != null) {
            year = yearArg
            rankingsYear = "${year}_Rankings"
        } else {
            Log.d(
                    TAG,
                    "No arguments. Did you create TeamRankingsFragment instance" +
                            "with newInstance method?"
            )
        }
        //TODO: Check Internet connectivity

        // Initialize database connection
        db = FirebaseFirestore.getInstance()
        collectionRef = db.collection("Teams")
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
                R.layout.fragment_rankings,
                container,
                false
        )
        rootView.tag = TAG + year

        setHasOptionsMenu(true)

        return rootView
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.rankings_spinner, menu)
        val spinner = menu?.findItem(R.id.rankings_spinner)?.actionView as Spinner
        val spinnerAdapter = ArrayAdapter<String>(context, R.layout.spinner_head_item, years)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
        spinner.setSelection(spinnerAdapter.getPosition(year))
        spinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                val newYear = p0?.getItemAtPosition(p2).toString()
                if (year != newYear) {
                    activity?.supportFragmentManager
                            ?.beginTransaction()
                            ?.replace(
                                    R.id.container,
                                    TeamRankingsFragment.newInstance(newYear),
                                    newYear
                            )
                            ?.commit()
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                // Do nothing
            }
        }
        val firstTimePreference = activity?.getSharedPreferences("pref", Context.MODE_PRIVATE)?.getBoolean("rankingsFirstTime", true)
        if (firstTimePreference != null && firstTimePreference) {
            showSpinnerFeatureDiscovery()
            activity?.getSharedPreferences("pref", Context.MODE_PRIVATE)?.edit()?.putBoolean("rankingsFirstTime", false)?.apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.setOnMenuItemClickListener({
            it -> Log.d(TAG, it.toString())
            true
        })
        return super.onOptionsItemSelected(item)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.toolbar?.title = "Team Rankings"
        // Rankings RecyclerView instantiation
        recyclerView = view.findViewById(R.id.recyclerView_rankings_content)

        progressBarLoadingRankings = view.findViewById(R.id.progressBarRankings)
        progressBarLoadingRankingsBig = view.findViewById(R.id.progressBarRankingsBig)
        progressBarLoadingRankings.visibility = View.GONE
        progressBarLoadingRankingsBig.visibility = View.GONE

        adapter = RankingsFirestoreAdapter(rankingsList, year)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        // Rankings layout instantiation
        val rankingLayoutManager = LinearLayoutManager(activity)
        rankingLayoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView.layoutManager = rankingLayoutManager

        rankingsRecyclerViewScrollListener = object : RankingsRecyclerViewScrollListener(rankingLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {
                if (moreData) {
                    getRankings()
                }
            }
        }
        recyclerView.addOnScrollListener(rankingsRecyclerViewScrollListener)

        getFirstRankings() // initial query
    }

    override fun onPause() {
        super.onPause()
        detachCollectionSnapshotListener()
    }

    private fun getFirstRankings() {
        progressBarLoadingRankingsBig.visibility = View.VISIBLE
        rankingsRecyclerViewScrollListener.resetState()
        attachCollectionSnapshotListener()
    }

    fun getRankings() {

        progressBarLoadingRankings.visibility = View.VISIBLE
        collectionRef.orderBy("Scores.$year", Query.Direction.DESCENDING)
                .whereGreaterThan("Scores.$year", 0)
                .limit(PAGE_LENGTH)
                .startAfter(lastDocument)
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val querySnapshot = task.result
                        if (!querySnapshot.isEmpty) {
                            if (querySnapshot.documents.size < 50) {
                                moreData = false
                            }
                            for (document in querySnapshot.documents) {
                                val team = document.toObject(Team::class.java)
                                rankingsList.add(team)
                                lastDocument = document
                            }
                            Log.d(TAG, "Loaded more data for page $page")
                            page++
                            progressBarLoadingRankings.visibility = View.GONE
                            adapter.notifyDataSetChanged()
                        }
                        else {
                            progressBarLoadingRankings.visibility = View.GONE
                            moreData = false
                        }
                    } else {
                        progressBarLoadingRankings.visibility = View.GONE
                        moreData = false
                    }
                }
    }

    private fun attachCollectionSnapshotListener() {
        val eventListener = object : EventListener<QuerySnapshot> {
            override fun onEvent(snapshot: QuerySnapshot?, e: FirebaseFirestoreException?) {
                if (snapshot != null) {
                    if (!snapshot.isEmpty) {
                        rankingsList.clear()
                        for (document in snapshot.documents) {
                            val ranking = document.toObject(Team::class.java)
                            rankingsList.add(ranking)
                            lastDocument = document
                        }
                        progressBarLoadingRankingsBig.visibility = View.GONE
                        adapter.notifyDataSetChanged()
                    } else {
                        //TODO: Small text view as bottom row saying no more teams available
                    }
                } else {
                    //TODO: Large text view saying error retrieving contents
                }
            }
        }
        listenerRegistration = collectionRef
                .orderBy("Scores.$year", Query.Direction.DESCENDING)
                .whereGreaterThan("Scores.$year", 0)
                .limit(PAGE_LENGTH)
                .addSnapshotListener(eventListener)
    }

    private fun detachCollectionSnapshotListener() {
        listenerRegistration.remove()
    }

    private fun showSpinnerFeatureDiscovery() {
        val spinnerDescription = resources.getString(R.string.rankings_spinner_hint)
        val spinnerItem = activity?.toolbar?.menu?.findItem(R.id.rankings_spinner)?.actionView
        if (spinnerItem != null) {
            TapTargetView.showFor(
                    activity,
                    TapTarget.forView(
                            spinnerItem,
                            "Select the Year",
                            spinnerDescription
                    )
                            .cancelable(true)
                            .drawShadow(true)
                            .textColor(android.R.color.black)
                            .targetCircleColor(R.color.colorPrimary)
                            .targetRadius(0)
                            .titleTextDimen(R.dimen.feature_tap_title)
                            .tintTarget(false),
                    object : TapTargetView.Listener() {
                        override fun onOuterCircleClick(view: TapTargetView?) {
                            view?.dismiss(true)
                        }
                    })
        }
    }
}