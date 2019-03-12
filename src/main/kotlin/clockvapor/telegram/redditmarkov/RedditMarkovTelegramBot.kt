package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import clockvapor.telegram.log
import clockvapor.telegram.tryOrNull
import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.Update
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

class RedditMarkovTelegramBot(private val dataPath: String,
                              private val token: String,
                              private val scraper: RedditScraper) {
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
        val replyText: String = tryOrNull {
            message.text
                ?.substring(command.offset + command.length)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.split(Main.whitespaceRegex)
                ?.let { texts ->
                    when (texts.size) {
                        0 -> EXPECTED_SUBREDDIT_NAME
                        1 -> tryOrNull { generateComment(bot, message.chat.id, dataPath, texts[0]) }
                        2 -> tryOrNull {
                            when (val result = generateComment(bot, message.chat.id, dataPath, texts[0], texts[1])) {
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

    private fun generateComment(bot: Bot, chatId: Long, dataPath: String, subreddit: String): String =
        readMarkov(bot, chatId, dataPath, subreddit).generate().joinToString(" ")

    private fun generateComment(bot: Bot, chatId: Long, dataPath: String, subreddit: String, seed: String)
        : MarkovChain.GenerateWithSeedResult =
        readMarkov(bot, chatId, dataPath, subreddit).generateWithCaseInsensitiveSeed(seed)

    private fun readMarkov(bot: Bot, chatId: Long, dataPath: String, subreddit: String): RedditMarkovChain {
        val file = File(Main.getMarkovPath(dataPath, subreddit))
        return if (file.exists()) {
            if ((System.currentTimeMillis() - file.lastModified()) / 1000L > scraper.fetchInterval) {
                log("Current data for requested subreddit \"$subreddit\" is old. Fetching new data now.")
                bot.sendChatAction(chatId, ChatAction.TYPING)
                if (file.delete()) {
                    scraper.scrape(subreddit)
                    Main.readMarkov(dataPath, subreddit)
                } else {
                    throw RuntimeException("Failed to delete markov file for subreddit \"$subreddit\".")
                }
            } else {
                Main.readMarkov(dataPath, subreddit)
            }
        } else {
            log("No data for requested subreddit \"$subreddit\". Fetching it now.")
            bot.sendChatAction(chatId, ChatAction.TYPING)
            scraper.scrape(subreddit)
            Main.readMarkov(dataPath, subreddit)
        }
    }

    companion object {
        private const val EXPECTED_SUBREDDIT_NAME = "<expected subreddit name following command>"
    }
}
