# reddit-markov-telegram-bot

`reddit-markov-telegram-bot` is a Telegram bot that builds a Markov chain for requested subreddits, and it uses those
Markov chains to generate "new" user comments from those subreddits when prompted. The process is similar to your phone
keyboard's predictive text capabilities.

## Sample Usage

Start a conversation with the bot or add it to your group here: http://t.me/redditsimulatorbot

### /comment
To generate a comment from a subreddit, use the `/comment` command. The command expects a subreddit name following it
to know which subreddit you want to generate a comment from: `/comment pics`. You can also include an optional "seed"
word following the subreddit name to tell the bot which word to start with when it generates the comment:
`/comment pics blah`

When a subreddit is requested via this command, its newest comments are fetched and analyzed in bulk, and they are only
fetched a maximum of once per hour to prevent redundant traffic and analysis.
