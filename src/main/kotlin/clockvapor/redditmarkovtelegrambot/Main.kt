package clockvapor.redditmarkovtelegrambot

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread

object Main {
    val whitespaceRegex = Regex("\\s+")

    @JvmStatic
    fun main(args: Array<String>): Unit = mainBody {
        val a = ArgParser(args).parseInto(::Args)
        val config = Config.read(a.configPath)
        thread { TelegramBot(config.telegramBotToken).run(a.dataPath) }
        thread {
            RedditScraper.run(config.redditClientId, config.redditClientSecret, config.redditAppId,
                config.redditAppVersion, config.redditUsername, config.redditFetchAmount!!,
                config.redditFetchIntervalMs!!, a.dataPath)
        }
    }

    fun readMarkov(dataPath: String, subreddit: String): RedditMarkovChain =
        synchronized(Main) { RedditMarkovChain.read(getMarkovPath(dataPath, subreddit)) }

    fun writeMarkov(dataPath: String, subreddit: String, markov: RedditMarkovChain) =
        synchronized(Main) { markov.write(getMarkovPath(dataPath, subreddit)) }

    private fun getMarkovPath(dataPath: String, subreddit: String): String =
        Paths.get(Main.createAndGetDataPath(dataPath), "${subreddit.toLowerCase(Locale.ENGLISH)}.json").toString()

    @Suppress("UNCHECKED_CAST")
    fun readSubredditsList(): Set<String> = synchronized(Main) {
        ObjectMapper(YAMLFactory()).readValue<Set<*>>(File(getSubredditsListPath()), Set::class.java)
            as Set<String>
    }

    fun writeSubredditsList(subreddits: Set<String>) = synchronized(Main) {
        ObjectMapper(YAMLFactory()).writeValue(File(getSubredditsListPath()), subreddits)
    }

    private fun getSubredditsListPath(): String =
        "subreddits.yml"

    private fun createAndGetDataPath(dataPath: String): String =
        dataPath.also { File(it).mkdirs() }

    private class Args(parser: ArgParser) {
        val configPath by parser.storing("-c", help = "Path to config YAML file")
        val dataPath by parser.storing("-d", help = "Path to data directory")
    }
}