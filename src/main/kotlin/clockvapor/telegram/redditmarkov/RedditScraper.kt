package clockvapor.telegram.redditmarkov

import clockvapor.telegram.log
import clockvapor.telegram.tryOrLog
import clockvapor.telegram.tryOrNull
import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import java.util.*

class RedditScraper(private val dataPath: String,
                    private val clientId: String,
                    private val clientSecret: String,
                    private val appId: String,
                    private val appVersion: String,
                    private val username: String,
                    private val fetchAmount: Int,
                    val fetchInterval: Long) {

    fun scrape(subreddit: String) {
        val reddit = buildReddit(clientId, clientSecret, appId, appVersion, username)
        tryOrLog { scrape(dataPath, reddit, subreddit, fetchAmount) }
    }

    private fun scrape(dataPath: String, reddit: RedditClient, subreddit: String, fetchAmount: Int) {
        val markov = tryOrNull(reportException = false) { Main.readMarkov(dataPath, subreddit) } ?: RedditMarkovChain()
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
