package clockvapor.telegram.redditmarkov

import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import java.util.*

object RedditScraper {
    private lateinit var clientId: String
    private lateinit var clientSecret: String
    private lateinit var appId: String
    private lateinit var appVersion: String
    private lateinit var username: String
    private var fetchAmount: Int? = null
    private lateinit var dataPath: String

    fun run(clientId: String, clientSecret: String, appId: String, appVersion: String, username: String,
            fetchAmount: Int, fetchIntervalMs: Long, dataPath: String) {

        RedditScraper.clientId = clientId
        RedditScraper.clientSecret = clientSecret
        RedditScraper.appId = appId
        RedditScraper.appVersion = appVersion
        RedditScraper.username = username
        RedditScraper.fetchAmount = fetchAmount
        RedditScraper.dataPath = dataPath

        while (true) {
            tryOrLog { scrape(clientId, clientSecret, appId, appVersion, username, fetchAmount, dataPath) }
            Thread.sleep(fetchIntervalMs)
        }
    }

    fun scrape(subreddit: String) {
        val reddit =
            buildReddit(clientId, clientSecret, appId, appVersion, username)
        val subreddits = tryOrNull {
            Main.readSubredditsList().toMutableSet()
        } ?: mutableSetOf()
        subreddits += subreddit
        Main.writeSubredditsList(subreddits)
        tryOrLog { scrape(dataPath, reddit, subreddit, fetchAmount!!) }
    }

    private fun scrape(clientId: String, clientSecret: String, appId: String,
                       appVersion: String, username: String, fetchAmount: Int, dataPath: String) {

        val reddit =
            buildReddit(clientId, clientSecret, appId, appVersion, username)
        tryOrNull { Main.readSubredditsList() }
            ?.let { subreddits ->
            for (subreddit in subreddits) {
                tryOrLog { scrape(dataPath, reddit, subreddit, fetchAmount) }
            }
        }
    }

    private fun scrape(dataPath: String, reddit: RedditClient, subreddit: String, fetchAmount: Int) {
        val markov = tryOrNull { Main.readMarkov(dataPath, subreddit) }
            ?: RedditMarkovChain()
        var i = 0
        var new = 0
        listing@ for (listing in reddit.subreddit(subreddit).comments().limit(fetchAmount).build()) {
            log("Fetched ${listing.size} comments from $subreddit")
            for (comment in listing) {
                i++
                if (markov.add(comment)) new++
                if (i >= fetchAmount) break@listing
            }
        }
        log("$new/$i comments fetched from $subreddit were added")
        Main.writeMarkov(dataPath, subreddit, markov)
    }

    private fun buildReddit(clientId: String, clientSecret: String, appId: String, appVersion: String,
                            username: String): RedditClient {

        val userAgent = UserAgent(System.getProperty("os.name"), appId, appVersion, username)
        val networkAdapter = OkHttpNetworkAdapter(userAgent)
        val credentials = Credentials.userless(clientId, clientSecret, UUID.randomUUID())
        return OAuthHelper.automatic(networkAdapter, credentials)
    }
}
