package clockvapor.telegram.redditmarkov

import clockvapor.telegram.log
import clockvapor.telegram.tryOrLog
import clockvapor.telegram.tryOrNull
import net.dean.jraw.RedditClient
import net.dean.jraw.http.OkHttpNetworkAdapter
import net.dean.jraw.http.UserAgent
import net.dean.jraw.models.SubredditSort
import net.dean.jraw.oauth.Credentials
import net.dean.jraw.oauth.OAuthHelper
import java.util.*

class RedditScraper(private val dataPath: String,
                    private val clientId: String,
                    private val clientSecret: String,
                    private val appId: String,
                    private val appVersion: String,
                    private val username: String,
                    private val commentFetchAmount: Int,
                    private val postFetchAmount: Int,
                    val fetchInterval: Long) {

    fun scrapeComments(subreddit: String) {
        val reddit = buildReddit(clientId, clientSecret, appId, appVersion, username)
        tryOrLog { scrapeComments(dataPath, reddit, subreddit, commentFetchAmount) }
    }

    fun scrapePosts(subreddit: String) {
        val reddit = buildReddit(clientId, clientSecret, appId, appVersion, username)
        tryOrLog { scrapePosts(dataPath, reddit, subreddit, postFetchAmount) }
    }

    private fun scrapeComments(dataPath: String, reddit: RedditClient, subreddit: String, fetchAmount: Int) {
        val markov =
            tryOrNull(reportException = false) { RedditMarkovTelegramBot.readCommentMarkov(dataPath, subreddit) }
                ?: RedditCommentMarkovChain()
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
        RedditMarkovTelegramBot.writeCommentMarkov(dataPath, subreddit, markov)
    }

    private fun scrapePosts(dataPath: String, reddit: RedditClient, subreddit: String, fetchAmount: Int) {
        val markov =
            tryOrNull(reportException = false) { RedditMarkovTelegramBot.readPostMarkov(dataPath, subreddit) }
                ?: RedditPostMarkovChain()
        var i = 0
        var new = 0
        listing@ for (listing in reddit.subreddit(subreddit).posts().sorting(SubredditSort.HOT).limit(fetchAmount).build()) {
            log("Fetched ${listing.size} posts from $subreddit")
            for (post in listing) {
                i++
                if (markov.add(post)) new++
                if (i >= fetchAmount) break@listing
            }
        }
        log("$new/$i posts fetched from $subreddit were added")
        RedditMarkovTelegramBot.writePostMarkov(dataPath, subreddit, markov)
    }

    private fun buildReddit(clientId: String, clientSecret: String, appId: String, appVersion: String,
                            username: String): RedditClient {

        val userAgent = UserAgent(System.getProperty("os.name"), appId, appVersion, username)
        val networkAdapter = OkHttpNetworkAdapter(userAgent)
        val credentials = Credentials.userless(clientId, clientSecret, UUID.randomUUID())
        return OAuthHelper.automatic(networkAdapter, credentials)
    }
}
