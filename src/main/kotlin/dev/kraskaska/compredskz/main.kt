package dev.kraskaska.compredskz

import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.answers.answer
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.*
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.message
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.reply_to_message
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.requests.answers.InlineQueryResultsButton
import dev.inmo.tgbotapi.types.InlineQueries.InlineQueryResult.InlineQueryResultArticle
import dev.inmo.tgbotapi.types.InlineQueries.InputMessageContent.InputTextMessageContent
import dev.inmo.tgbotapi.types.InlineQueryId
import dev.inmo.tgbotapi.types.LinkPreviewOptions
import dev.inmo.tgbotapi.types.message.textsources.BotCommandTextSource
import dev.inmo.tgbotapi.types.message.textsources.linkTextSource
import dev.inmo.tgbotapi.types.message.textsources.mentionTextSource
import dev.inmo.tgbotapi.types.message.textsources.regularTextSource
import dev.inmo.tgbotapi.utils.row
import java.sql.DriverManager
import kotlin.random.Random

data class Prediction(val id: Int, val author: Long, val prediction: String, val voteScore: Int)

val updootLinks = mutableListOf<Pair<String, Pair<Int, Boolean>>>()
fun genUpdootLink(to: Int, vote: Boolean): String {
    if (updootLinks.size > 200) {
        updootLinks.removeAt(0)
    }
    val s = Random.nextBytes(4).toHexString(HexFormat.Default)
    updootLinks.add(s to (to to vote))
    return s
}

fun findUpdootLink(l: String): Pair<Int, Boolean>? {
    return updootLinks.firstOrNull { it.first == l }?.second
}

suspend fun main() {
    val postgres = DriverManager.getConnection(System.getenv("POSTGRES_URL"))
    val botToken = System.getenv("TG_TOKEN")
    val bot = telegramBot(botToken)
    println(bot.getMe())
    bot.buildBehaviourWithLongPolling {
        onCommand("start") {
            reply(
                it,
                "Привет! Я собираю предсказания и случайно выбираю их!\n\nУпомянуйте ${bot.getMe().username} чтобы получить предсказание!\nОтправьте сообщение чтобы добавить предсказание!"
            )
        }
        onDeepLink { deepLink ->
            val deep = deepLink.second
            if(deep == "s") {
                reply(
                    deepLink.first,
                    "Привет! Я собираю предсказания и случайно выбираю их!\n\nУпомянуйте ${bot.getMe().username} чтобы получить предсказание!\nОтправьте сообщение чтобы добавить предсказание!"
                )
                return@onDeepLink
            } else {
                val link = findUpdootLink(deep)
                if(link == null) {
                    reply(
                        deepLink.first,
                        "Ссылка недействительна."
                    )
                    return@onDeepLink
                }
                postgres.prepareStatement(
                    "INSERT INTO votes (user_id, prediction_id, vote)\n" +
                            "VALUES (?, ?, ?)\n" +
                            "ON CONFLICT (user_id, prediction_id) DO UPDATE SET vote = ?;"
                ).use {
                    it.setLong(1, deepLink.first.from!!.id.chatId.long)
                    it.setInt(2, link.first)
                    it.setBoolean(3, link.second)
                    it.setBoolean(4, link.second)
                    it.execute()
                }
                reply(
                    deepLink.first,
                    "Оценка ${if(link.second) "+1" else "-1"} к ${link.first} записана."
                )
            }
        }
        onCommand("deleteprediction", requireOnlyCommandInMessage = false) { message ->
//            println(message.content.textSources.firstOrNull { it !is BotCommandTextSource }?.asText)
            val pred =
                message.content.textSources.firstOrNull { it !is BotCommandTextSource }?.asText?.trim()?.toIntOrNull()
            if (pred == null) {
                reply(message, "Аргумент нераспознан.")
                return@onCommand
            }
            if (!postgres.prepareStatement(
                    "SELECT EXISTS(\n" +
                            "    SELECT 1 FROM predictions\n" +
                            "    WHERE id = ? AND author_id = ?\n" +
                            ");"
                ).use {
                    it.setInt(1, pred)
                    it.setLong(2, message.from!!.id.chatId.long)
                    it.executeQuery().use { it.next(); it.getBoolean(1) }
                }
            ) {
                reply(message, "Вы не владелец этого предсказания!")
                return@onCommand;
            }
            postgres.prepareStatement("DELETE FROM predictions WHERE id = ?;").use {
                it.setInt(1, pred)
                it.execute()
            }
            reply(message, "Предсказание $pred удалено.")
        }
        onCommand("mypredictions") { message ->
            val preds = postgres.prepareStatement(
                "SELECT p.id, p.prediction,\n" +
                        "       COALESCE(v.score, 0) AS vote_score\n" +
                        "FROM predictions p\n" +
                        "LEFT JOIN (\n" +
                        "    SELECT prediction_id,\n" +
                        "           SUM(CASE WHEN vote THEN 1 ELSE -1 END) AS score\n" +
                        "    FROM votes\n" +
                        "    GROUP BY prediction_id\n" +
                        ") v ON p.id = v.prediction_id\n" +
                        "WHERE p.author_id = ?\n" +
                        "ORDER BY vote_score DESC;"
            ).use {
                it.setLong(1, message.from!!.id.chatId.long)
                it.executeQuery().use { res ->
                    generateSequence {
                        if (!res.next()) null else Prediction(
                            res.getInt("id"),
                            message.from!!.id.chatId.long,
                            res.getString("prediction"),
                            res.getInt("vote_score")
                        )
                    }.toList()
                }
            }
            reply(
                message,
                "Ваши предсказания (${message.from!!.id.chatId.long}):\n\n${
                    if (preds.isEmpty()) "У вас нет предсказаний!" else preds.joinToString("\n") { "${it.id} - ${it.prediction} [${it.voteScore}]" } + "\n\nИспользуйте /deleteprediction <номер предсказания> чтобы удалить предсказание!"
                }"
            )
        }
        onContentMessage {
            if (it.text?.startsWith("/") == true) return@onContentMessage
            if (it.text != null) {
                reply(
                    it,
                    "Нажмите на кнопку чтобы добавить как предсказание:",
                    replyMarkup = inlineKeyboard { row { dataButton("Добавить предсказание", "submit_prediction") } })
            }
        }
        onDataCallbackQuery("submit_prediction") {
            val predText = it.message!!.reply_to_message!!.text
            val newId = postgres.prepareStatement(
                "INSERT INTO predictions (author_id, prediction)\n" +
                        "VALUES (?, ?)\n" +
                        "RETURNING id;"
            ).use { statement ->
                statement.setLong(1, it.message!!.reply_to_message!!.from!!.id.chatId.long)
                statement.setString(2, predText)
                statement.executeQuery().use { it.next(); it.getInt("id") }
            }
            reply(it.message!!, "Добавлено предсказание $newId: $predText")
            answer(it, "Добавлено предсказание $newId: $predText")
        }
//        val upvoteRegex = Regex("""upvote:(?<id>.+)""")
//        onDataCallbackQuery(upvoteRegex) { dataCallbackQuery ->
//            val predId = upvoteRegex.matchEntire(dataCallbackQuery.data)?.groups?.get("id")?.value?.toIntOrNull()
//            if (predId == null) {
//                answer(dataCallbackQuery, "Что-то пошло не так.")
//                return@onDataCallbackQuery
//            }
//            postgres.prepareStatement(
//                "INSERT INTO votes (user_id, prediction_id, vote)\n" +
//                        "VALUES (?, ?, true)\n" +
//                        "ON CONFLICT (user_id, prediction_id) DO UPDATE SET vote = true;"
//            ).use {
//                it.setLong(1, dataCallbackQuery.from.id.chatId.long)
//                it.setInt(2, predId)
//                it.execute()
//            }
//            answer(dataCallbackQuery, "+1 к предсказанию $predId")
//        }
//        val downvoteRegex = Regex("""downvote:(?<id>.+)""")
//        onDataCallbackQuery(downvoteRegex) { dataCallbackQuery ->
//            val predId = downvoteRegex.matchEntire(dataCallbackQuery.data)?.groups?.get("id")?.value?.toIntOrNull()
//            if (predId == null) {
//                answer(dataCallbackQuery, "Что-то пошло не так.")
//                return@onDataCallbackQuery
//            }
//            postgres.prepareStatement(
//                "INSERT INTO votes (user_id, prediction_id, vote)\n" +
//                        "VALUES (?, ?, false)\n" +
//                        "ON CONFLICT (user_id, prediction_id) DO UPDATE SET vote = false;"
//            ).use {
//                it.setLong(1, dataCallbackQuery.from.id.chatId.long)
//                it.setInt(2, predId)
//                it.execute()
//            }
//            answer(dataCallbackQuery, "-1 к предсказанию $predId")
//        }
        onAnyInlineQuery {
            val randomPrediction: String
            val randomPredictionId: Int
            postgres.prepareStatement(
                "SELECT p.prediction, p.id\n" +
                        "FROM predictions p\n" +
                        "LEFT JOIN (\n" +
                        "    SELECT prediction_id,\n" +
                        "           SUM(CASE WHEN vote THEN 1 ELSE -1 END) AS score\n" +
                        "    FROM votes\n" +
                        "    GROUP BY prediction_id\n" +
                        ") v ON p.id = v.prediction_id\n" +
                        "ORDER BY LOG(COALESCE(v.score, 0) + 1) * RANDOM() DESC\n" +
                        "LIMIT 1;"
            ).use {
                it.executeQuery().use {
                    it.next(); randomPrediction = it.getString("prediction"); randomPredictionId = it.getInt("id")
                }
            }
            answer(
                it, listOf(
                    InlineQueryResultArticle(
                        InlineQueryId("prediction${System.currentTimeMillis()}"),
                        "Получить предсказание",
                        InputTextMessageContent(
                            listOf(
                                regularTextSource("Предсказание для "),
                                mentionTextSource(it.from),
                                regularTextSource(":\n\n"),
                                regularTextSource(randomPrediction),
                                regularTextSource(" ("),
                                linkTextSource("+1", "https://t.me/${bot.getMe().username!!.withoutAt}?start=${genUpdootLink(randomPredictionId, true)}"),
                                regularTextSource(" / "),
                                linkTextSource("-1", "https://t.me/${bot.getMe().username!!.withoutAt}?start=${genUpdootLink(randomPredictionId, false)}"),
                                regularTextSource(")"),
                            ),
                            linkPreviewOptions = LinkPreviewOptions.Disabled
                        ),
//                        replyMarkup = inlineKeyboard {
//                            row {
//                                dataButton(
//                                    "+1",
//                                    "upvote:$randomPredictionId",
//                                    style = KeyboardButtonStyle.Success
//                                ); dataButton(
//                                "-1",
//                                "downvote:$randomPredictionId", style = KeyboardButtonStyle.Danger
//                            )
//                            }
////                            row {
////                                urlButton("Добавьте своё предсказание!", "tg://user?id=${bot.getMe().id.chatId.long}")
////                            }
//                        }
                    ),
                ),
                button = InlineQueryResultsButton.invoke("Добавьте своё предсказание!", "s"),
                cachedTime = 0
            )
        }
    }.join()
    postgres.close()
}