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
import me.ivmg.telegram.entities.ParseMode
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
                args.redditAppId, args.redditAppVersion, args.redditUsername, args.redditCommentFetchAmount,
                args.redditPostFetchAmount, args.redditFetchInterval)
            RedditMarkovTelegramBot(args.dataPath, args.telegramBotToken, scraper).run()
        }

        fun readCommentMarkov(dataPath: String, subreddit: String): RedditCommentMarkovChain =
            RedditCommentMarkovChain.read(getCommentMarkovPath(dataPath, subreddit))

        fun readPostMarkov(dataPath: String, subreddit: String): RedditPostMarkovChain =
            RedditPostMarkovChain.read(getPostMarkovPath(dataPath, subreddit))

        fun writeCommentMarkov(dataPath: String, subreddit: String, markov: RedditCommentMarkovChain) =
            markov.write(getCommentMarkovPath(dataPath, subreddit))

        fun writePostMarkov(dataPath: String, subreddit: String, markov: RedditPostMarkovChain) =
            markov.write(getPostMarkovPath(dataPath, subreddit))

        fun getCommentMarkovPath(dataPath: String, subreddit: String): String =
            Paths.get(createAndGetDataPath(dataPath),
                "${subreddit.toLowerCase(Locale.ENGLISH)}_comment.json").toString()

        fun getPostMarkovPath(dataPath: String, subreddit: String): String =
            Paths.get(createAndGetDataPath(dataPath), "${subreddit.toLowerCase(Locale.ENGLISH)}_post.json").toString()

        private fun createAndGetDataPath(dataPath: String): String =
            dataPath.also { File(it).mkdirs() }
    }

    fun run() {
        val bot = bot {
            this.token = this@RedditMarkovTelegramBot.token
            logLevel = HttpLoggingInterceptor.Level.NONE
            dispatch {
                command("start") { bot, update -> giveHelp(bot, update) }
                command("help") { bot, update -> giveHelp(bot, update) }
                command("comment") { bot, update ->
                    doCommentCommand(bot, update)
                }
                command("post") { bot, update ->
                    doPostCommand(bot, update)
                }
            }
        }
        bot.startPolling()
    }

    private fun giveHelp(bot: Bot, update: Update) {
        bot.sendMessage(update.message!!.chat.id,
            "I create Markov chains for subreddits and use them to generate \"new\" posts and comments.\n\n" +
                "Use the /comment command like so:\n`/comment <subreddit> [seed]`\n" +
                "Provide the subreddit name without the `/r/`, and optionally provide a seed word to start the " +
                "generated comment with.\n\n" +
                "Use the /post command like so:\n`/post <subreddit>`\n" +
                "Provide the subreddit name without the `/r/`.",
            parseMode = ParseMode.MARKDOWN)
    }

    private fun doCommentCommand(bot: Bot, update: Update) {
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

    private fun doPostCommand(bot: Bot, update: Update) {
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
                        else -> tryOrNull { generatePost(texts[0]) }
                    } ?: "<no data available for /r/${texts[0]}>"
                } ?: EXPECTED_SUBREDDIT_NAME
        } ?: "<an error occurred>"
        bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
    }

    private fun generateComment(subreddit: String): String =
        readCommentMarkov(subreddit).generate().joinToString(" ")

    private fun generateComment(subreddit: String, seed: String): MarkovChain.GenerateWithSeedResult =
        readCommentMarkov(subreddit).generateWithCaseInsensitiveSeed(seed)

    private fun generatePost(subreddit: String): String {
        val markov = readPostMarkov(subreddit)
        val title = markov.generateTitle().joinToString(" ")
        val body = markov.generate().joinToString(" ")
        return "$title\n-----\n$body"
    }

    private fun readCommentMarkov(subreddit: String): RedditCommentMarkovChain {
        val file = File(getCommentMarkovPath(dataPath, subreddit))
        return if (file.exists()) {
            if ((System.currentTimeMillis() - file.lastModified()) / 1000L > scraper.fetchInterval) {
                log("Current data for requested subreddit \"$subreddit\" is old. Fetching new data now.")
                if (file.delete()) {
                    scraper.scrapeComments(subreddit)
                    readCommentMarkov(dataPath, subreddit)
                } else {
                    throw RuntimeException("Failed to delete markov file for subreddit \"$subreddit\".")
                }
            } else {
                readCommentMarkov(dataPath, subreddit)
            }
        } else {
            log("No data for requested subreddit \"$subreddit\". Fetching it now.")
            scraper.scrapeComments(subreddit)
            readCommentMarkov(dataPath, subreddit)
        }
    }

    private fun readPostMarkov(subreddit: String): RedditPostMarkovChain {
        val file = File(getPostMarkovPath(dataPath, subreddit))
        return if (file.exists()) {
            if ((System.currentTimeMillis() - file.lastModified()) / 1000L > scraper.fetchInterval) {
                log("Current data for requested subreddit \"$subreddit\" is old. Fetching new data now.")
                if (file.delete()) {
                    scraper.scrapePosts(subreddit)
                    readPostMarkov(dataPath, subreddit)
                } else {
                    throw RuntimeException("Failed to delete markov file for subreddit \"$subreddit\".")
                }
            } else {
                readPostMarkov(dataPath, subreddit)
            }
        } else {
            log("No data for requested subreddit \"$subreddit\". Fetching it now.")
            scraper.scrapePosts(subreddit)
            readPostMarkov(dataPath, subreddit)
        }
    }

    private class Args(parser: ArgParser) {
        val telegramBotToken: String by parser.storing("-t", help = "Telegram bot token")
        val redditClientId: String by parser.storing("-i", help = "Reddit client ID")
        val redditClientSecret: String by parser.storing("-s", help = "Reddit client secret")
        val redditAppId: String by parser.storing("-a", help = "Reddit app ID")
        val redditAppVersion: String by parser.storing("-v", help = "Reddit app version")
        val redditUsername: String by parser.storing("-u", help = "Reddit username")
        val redditCommentFetchAmount: Int by parser.storing("-f", help = "Number of comments to fetch",
            transform = String::toInt)
        val redditPostFetchAmount: Int by parser.storing("-p", help = "Number of posts to fetch",
            transform = String::toInt)
        val redditFetchInterval: Long by parser.storing("-g",
            help = "Comments/posts must be at least this many seconds old before fetching new ones",
            transform = String::toLong)
        val dataPath by parser.storing("-d", "--data", help = "Path to data directory")
    }
}
