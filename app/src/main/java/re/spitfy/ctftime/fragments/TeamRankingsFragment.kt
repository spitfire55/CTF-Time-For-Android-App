package re.spitfy.ctftime.fragments

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.pwittchen.infinitescroll.library.InfiniteScrollListener

import com.google.firebase.firestore.*
import re.spitfy.ctftime.R
import re.spitfy.ctftime.adapters.RankingsFirestoreAdapter
import re.spitfy.ctftime.data.Ranking

class TeamRankingsFragment :
        android.support.v4.app.Fragment(),
        AdapterView.OnItemSelectedListener
{
    private lateinit var year: String
    private lateinit var rankingsYear : String
    private var userClick = false
    private lateinit var adapter : RankingsFirestoreAdapter
    private var rankingList : MutableList<Ranking?> = ArrayList()
    private lateinit var db : FirebaseFirestore
    private var isLoading = false
    private var totalItemCount = 0
    private var lastVisibleItemPosition = 0
    // must be Long because Firestore api...
    private var currentPage : Long = 0 // used for startAt math (currentPage * itemsInPage)

    companion object
    {
        val TAG = "TeamRankingsFragment"
        val PAGE_LENGTH : Long = 20
        fun newInstance(year: String): TeamRankingsFragment
        {
            val args = Bundle()
            args.putString("YEAR", year)
            val fragment = TeamRankingsFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        val yearArg = arguments?.getString("YEAR")
        if (yearArg != null) {
            year = yearArg
            rankingsYear = "${year}_Rankings"
        } else {
            Log.d(TAG, "No arguments. Did you create " +
                    "TeamRankingsFragment instance with newInstance method?")
        }
        //TODO: Check Internet connectivity

        // Initialize database connection
        db = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View?
    {
        val rootView = inflater.inflate(
                R.layout.fragment_rankings,
                container,
                false)
        rootView?.tag = TAG + year

        // Rankings RecyclerView instantiation
        val recyclerView = rootView?.
                findViewById<RecyclerView>(R.id.team_ranking_recyclerview)

        // Ranking spinner instantiation
        val yearSpinner = rootView.findViewById<Spinner>(R.id.rankings_spinner)
        spinnerInitiate(yearSpinner)

        // Rankings layout instantiation
        val rankingLayoutManager = LinearLayoutManager(activity)
        rankingLayoutManager.orientation = LinearLayoutManager.VERTICAL
        recyclerView?.setHasFixedSize(true)
        recyclerView?.setItemViewCacheSize(40)
        recyclerView?.isDrawingCacheEnabled = true
        recyclerView?.layoutManager = rankingLayoutManager

        adapter = RankingsFirestoreAdapter()
        recyclerView?.adapter = adapter

        getRankings(null)

        //TODO: Use InfiniteScrollListener to make pagination logic easier
        recyclerView?.addOnScrollListener(object : InfiniteScrollListener(PAGE_LENGTH.toInt(), rankingLayoutManager) {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                totalItemCount = rankingLayoutManager.itemCount
                lastVisibleItemPosition = rankingLayoutManager.findLastVisibleItemPosition()

                if (!isLoading && totalItemCount <= (lastVisibleItemPosition + PAGE_LENGTH)) {
                    getRankings(adapter.getLastRank() + 1)
                    isLoading = true
                    //TODO: Display loading view
                }
            }
        })

        // Rankings data instantiation
        return rootView ?: throw IllegalStateException(
                "LayoutInflater is null in onCreateView. "
                + "Unable to inflate view.")
    }

    fun getRankings(startRank : Int?) {
        val query : Query?
        if (startRank == null) {
            query =  db.collection(rankingsYear)
                    .orderBy("Rank", Query.Direction.ASCENDING)
                    .limit(PAGE_LENGTH)
        } else {
            query = db.collection(rankingsYear)
                    .orderBy("Rank", Query.Direction.ASCENDING)
                    .startAt(startRank)
                    .limit(PAGE_LENGTH)
        }
        query.addSnapshotListener(object : EventListener<QuerySnapshot> {
            override fun onEvent(querySnapshot: QuerySnapshot?, e: FirebaseFirestoreException?) {
                val newRankings : MutableList<Ranking> = ArrayList()
                if (querySnapshot != null) {
                    querySnapshot.documents.forEach {
                        newRankings.add(it.toObject(Ranking::class.java))
                    }
                    adapter.appendRankings(newRankings)
                    isLoading = false
                    //TODO: Hide loading icon at bottom
                    //TODO: If startRank == null, hide initial large loading view
                }
            }
        })
    }

    private fun initializeScrollListener(rankingLayoutManager : LinearLayoutManager) : InfiniteScrollListener {
        return object: InfiniteScrollListener(PAGE_LENGTH.toInt(), rankingLayoutManager) {
            override fun onScrolledToEnd(firstVisibleItemPosition: Int) {
                //TODO: Implement logic
            }
        }
    }

    private fun spinnerInitiate(spinner: Spinner) {
        val rankingsArray = activity?.resources?.getStringArray(R.array.ranking_years)
        val yearSpinnerAdapter = ArrayAdapter<String>(activity, R.layout.spinner_head, rankingsArray)
        yearSpinnerAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = yearSpinnerAdapter
        val yearPosition = yearSpinnerAdapter.getPosition(year)
        spinner.setSelection(yearPosition, false)
        spinner.onItemSelectedListener = this
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
        if (userClick) {
            val newYear = p0?.getItemAtPosition(p2).toString()
            val savedFragment = activity?.supportFragmentManager?.findFragmentByTag(newYear)
            /*TODO: Test backstack logic to make sure it isn't:
                 a) adding duplicates to backstack and
                 b) finding fragments by tag correctly
            */
            if(savedFragment != null) {
                activity?.supportFragmentManager
                        ?.beginTransaction()
                        ?.replace(R.id.container, savedFragment, newYear)
                        ?.addToBackStack(year)
                        ?.commit()
            } else {
                activity?.supportFragmentManager
                        ?.beginTransaction()
                        ?.replace(R.id.container, TeamRankingsFragment.newInstance(newYear), newYear)
                        ?.addToBackStack(year)
                        ?.commit()
        }
        else {
            userClick = true
        }
    }
    override fun onNothingSelected(p0: AdapterView<*>?) {
        //Do nothing
    }
}