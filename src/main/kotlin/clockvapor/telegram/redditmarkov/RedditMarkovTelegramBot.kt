package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import clockvapor.telegram.log
import clockvapor.telegram.tryOrNull
import clockvapor.telegram.whitespaceRegex
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.Update
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.nio.file.Paths
import java.util.*

class RedditMarkovTelegramBot(private val dataPath: String,
                              private val token: String,
                              private val scraper: RedditScraper) {

    companion object {
        private const val EXPECTED_SUBREDDIT_NAME = "<expected subreddit name following command>"

        @JvmStatic
        fun main(_args: Array<String>): Unit = mainBody {
            val args = ArgParser(_args).parseInto(::Args)
            val scraper = RedditScraper(args.dataPath, args.redditClientId, args.redditClientSecret,
                args.redditAppId, args.redditAppVersion, args.redditUsername, args.redditFetchAmount,
                args.redditFetchInterval)
            RedditMarkovTelegramBot(args.dataPath, args.telegramBotToken, scraper).run()
        }

        fun readMarkov(dataPath: String, subreddit: String): RedditMarkovChain =
            RedditMarkovChain.read(getMarkovPath(dataPath, subreddit))

        fun writeMarkov(dataPath: String, subreddit: String, markov: RedditMarkovChain) =
            markov.write(getMarkovPath(dataPath, subreddit))

        fun getMarkovPath(dataPath: String, subreddit: String): String =
            Paths.get(createAndGetDataPath(dataPath), "${subreddit.toLowerCase(Locale.ENGLISH)}.json").toString()

        private fun createAndGetDataPath(dataPath: String): String =
            dataPath.also { File(it).mkdirs() }
    }

    fun run() {
        val bot = bot {
            this.token = this@RedditMarkovTelegramBot.token
            logLevel = HttpLoggingInterceptor.Level.NONE
            dispatch {
                command("comment") { bot, update ->
                    handleUpdate(bot, update)
                }
            }
        }
        bot.startPolling()
    }

    private fun handleUpdate(bot: Bot, update: Update) {
        val message = update.message!!
        val command = message.entities!![0]
        bot.sendChatAction(message.chat.id, ChatAction.TYPING)
        val replyText: String = tryOrNull {
            message.text
                ?.substring(command.offset + command.length)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.split(whitespaceRegex)
                ?.let { texts ->
                    when (texts.size) {
                        0 -> EXPECTED_SUBREDDIT_NAME
                        1 -> tryOrNull { generateComment(texts[0]) }
                        2 -> tryOrNull {
                            when (val result = generateComment(texts[0], texts[1])) {
                                is MarkovChain.GenerateWithSeedResult.Success ->
                                    result.message.takeIf { it.isNotEmpty() }?.joinToString(" ")
                                is MarkovChain.GenerateWithSeedResult.NoSuchSeed ->
                                    "<no such seed exists for /r/${texts[0]}>"
                            }
                        }
                        else -> "<expected only one seed word>"
                    } ?: "<no data available for /r/${texts[0]}>"
                } ?: EXPECTED_SUBREDDIT_NAME
        } ?: "<an error occurred>"
        bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
    }

    private fun generateComment(subreddit: String): String =
        readMarkov(subreddit).generate().joinToString(" ")

    private fun generateComment(subreddit: String, seed: String): MarkovChain.GenerateWithSeedResult =
        readMarkov(subreddit).generateWithCaseInsensitiveSeed(seed)

    private fun readMarkov(subreddit: String): RedditMarkovChain {
        val file = File(getMarkovPath(dataPath, subreddit))
        return if (file.exists()) {
            if ((System.currentTimeMillis() - file.lastModified()) / 1000L > scraper.fetchInterval) {
                log("Current data for requested subreddit \"$subreddit\" is old. Fetching new data now.")
                if (file.delete()) {
                    scraper.scrape(subreddit)
                    readMarkov(dataPath, subreddit)
                } else {
                    throw RuntimeException("Failed to delete markov file for subreddit \"$subreddit\".")
                }
            } else {
                readMarkov(dataPath, subreddit)
            }
        } else {
            log("No data for requested subreddit \"$subreddit\". Fetching it now.")
            scraper.scrape(subreddit)
            readMarkov(dataPath, subreddit)
        }
    }

    private class Args(parser: ArgParser) {
        val telegramBotToken: String by parser.storing("-t", help = "Telegram bot token")
        val redditClientId: String by parser.storing("-i", help = "Reddit client ID")
        val redditClientSecret: String by parser.storing("-s", help = "Reddit client secret")
        val redditAppId: String by parser.storing("-a", help = "Reddit app ID")
        val redditAppVersion: String by parser.storing("-v", help = "Reddit app version")
        val redditUsername: String by parser.storing("-u", help = "Reddit username")
        val redditFetchAmount: Int by parser.storing("-f", help = "Number of comments to fetch",
            transform = String::toInt)
        val redditFetchInterval: Long by parser.storing("-g",
            help = "Comments must be at least this many seconds old before fetching new ones",
            transform = String::toLong)
        val dataPath by parser.storing("-d", "--data", help = "Path to data directory")
    }
}
