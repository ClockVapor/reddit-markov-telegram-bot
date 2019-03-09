package clockvapor.telegram.redditmarkov

import clockvapor.markov.MarkovChain
import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.entities.ChatAction
import me.ivmg.telegram.entities.Update
import okhttp3.logging.HttpLoggingInterceptor

class RedditMarkovTelegramBot(private val token: String, private val dataPath: String) {
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
        val replyText: String = message.text
            ?.substring(command.offset + command.length)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.split(Main.whitespaceRegex)
            ?.let { texts ->
                when (texts.size) {
                    0 -> EXPECTED_SUBREDDIT_NAME
                    1 -> tryOrNull { generateComment(bot, message.chat.id, dataPath, texts[0]) }
                    2 -> tryOrNull {
                        val result =
                            generateComment(bot, message.chat.id, dataPath, texts[0], texts[1])
                        when (result) {
                            is MarkovChain.GenerateWithSeedResult.Success ->
                                result.message.takeIf { it.isNotEmpty() }?.joinToString(" ")
                            is MarkovChain.GenerateWithSeedResult.NoSuchSeed ->
                                "<no such seed exists for /r/${texts[0]}>"
                        }
                    }
                    else -> "<expected only one seed word>"
                } ?: "<no data available for /r/${texts[0]}>"
            } ?: EXPECTED_SUBREDDIT_NAME
        bot.sendMessage(message.chat.id, replyText, replyToMessageId = message.messageId)
    }

    private fun generateComment(bot: Bot, chatId: Long, dataPath: String, subreddit: String): String =
        readMarkov(bot, chatId, dataPath, subreddit).generate().joinToString(" ")

    private fun generateComment(bot: Bot, chatId: Long, dataPath: String, subreddit: String, seed: String)
        : MarkovChain.GenerateWithSeedResult =
        readMarkov(bot, chatId, dataPath, subreddit).generateWithCaseInsensitiveSeed(seed)

    private fun readMarkov(bot: Bot, chatId: Long, dataPath: String, subreddit: String): RedditMarkovChain =
        try {
            Main.readMarkov(dataPath, subreddit)
        } catch (e: Exception) {
            log("No data on requested subreddit \"$subreddit\". Fetching its data manually.")
            bot.sendChatAction(chatId, ChatAction.TYPING)
            RedditScraper.scrape(subreddit)
            Main.readMarkov(dataPath, subreddit)
        }

    companion object {
        private const val EXPECTED_SUBREDDIT_NAME = "<expected subreddit name following command>"
    }
}
