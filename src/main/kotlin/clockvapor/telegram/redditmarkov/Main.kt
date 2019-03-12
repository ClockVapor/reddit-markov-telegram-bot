package clockvapor.telegram.redditmarkov

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import java.io.File
import java.nio.file.Paths
import java.util.*

object Main {
    @JvmStatic
    fun main(args: Array<String>): Unit = mainBody {
        val a = ArgParser(args).parseInto(::Args)
        val config = Config.read(a.configPath)
        val scraper = RedditScraper(a.dataPath, config.redditClientId, config.redditClientSecret, config.redditAppId,
            config.redditAppVersion, config.redditUsername, config.redditFetchAmount!!, config.redditFetchInterval!!)
        RedditMarkovTelegramBot(a.dataPath, config.telegramBotToken, scraper).run()
    }

    fun readMarkov(dataPath: String, subreddit: String): RedditMarkovChain =
        RedditMarkovChain.read(getMarkovPath(dataPath, subreddit))

    fun writeMarkov(dataPath: String, subreddit: String, markov: RedditMarkovChain) =
        markov.write(getMarkovPath(dataPath, subreddit))

    fun getMarkovPath(dataPath: String, subreddit: String): String =
        Paths.get(createAndGetDataPath(dataPath), "${subreddit.toLowerCase(Locale.ENGLISH)}.json").toString()

    fun createAndGetDataPath(dataPath: String): String =
        dataPath.also { File(it).mkdirs() }

    private class Args(parser: ArgParser) {
        val configPath by parser.storing("-c", "--config", help = "Path to config YAML file")
        val dataPath by parser.storing("-d", "--data", help = "Path to data directory")
    }
}
