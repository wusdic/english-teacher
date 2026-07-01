package com.englishteacher.core.catalog

import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.model.TopicCategory

/**
 * The built-in library of conversation topics and role-play scenarios. Curated to mirror the
 * kind of practice 「咕噜口语」 offers, spanning everyday life, travel, work, social and exam prep.
 */
object TopicCatalog {
    val freeChat: Topic =
        Topic(
            id = "free_chat",
            title = "Free Chat",
            description = "Open-ended friendly conversation about anything you like.",
            category = TopicCategory.EVERYDAY,
            scenarioPrompt =
                "You are simply having a friendly, open-ended chat with the learner over a cup of " +
                    "tea. Follow their interests and keep the conversation flowing naturally.",
            sampleOpeners =
                listOf(
                    "Hiya! Lovely to see you. So, how's your day been so far?",
                    "Hello there! What have you been up to today?",
                ),
        )

    val all: List<Topic> =
        listOf(
            freeChat,
            Topic(
                id = "restaurant",
                title = "At a Restaurant",
                description = "Order food and chat with the waiter at a London restaurant.",
                category = TopicCategory.EVERYDAY,
                scenarioPrompt =
                    "You are a friendly waiter at a cosy London restaurant. Greet the learner, take " +
                        "their order, make recommendations and have natural small talk.",
                sampleOpeners =
                    listOf(
                        "Good evening, welcome to The Ivy! Have you got a table booked, or are you " +
                            "happy to sit at the bar?",
                        "Evening! Here are your menus. Can I get you something to drink to start?",
                    ),
            ),
            Topic(
                id = "airport",
                title = "At the Airport",
                description = "Check in, go through security and find your gate.",
                category = TopicCategory.TRAVEL,
                scenarioPrompt =
                    "You are a helpful airline check-in agent at Heathrow. Help the learner check in, " +
                        "ask about luggage and seats, and give directions to the gate.",
                sampleOpeners =
                    listOf(
                        "Good morning! May I see your passport and booking reference, please?",
                        "Hello there, where are you flying to today?",
                    ),
            ),
            Topic(
                id = "hotel",
                title = "Hotel Check-in",
                description = "Check into a hotel and ask about the facilities.",
                category = TopicCategory.TRAVEL,
                scenarioPrompt =
                    "You are a polite receptionist at a boutique hotel in Edinburgh. Check the " +
                        "learner in, explain breakfast and Wi-Fi, and answer questions about the area.",
                sampleOpeners =
                    listOf(
                        "Good afternoon and welcome! Have you stayed with us before?",
                        "Hello! Checking in? Could I take the name on the booking, please?",
                    ),
            ),
            Topic(
                id = "job_interview",
                title = "Job Interview",
                description = "Practise a professional job interview in English.",
                category = TopicCategory.WORK,
                scenarioPrompt =
                    "You are a friendly but professional hiring manager interviewing the learner for " +
                        "a role they care about. Ask common interview questions and follow up naturally.",
                sampleOpeners =
                    listOf(
                        "Thanks for coming in. To start, could you tell me a little about yourself?",
                        "Lovely to meet you. So, what attracted you to this role?",
                    ),
            ),
            Topic(
                id = "shopping",
                title = "Shopping for Clothes",
                description = "Shop for clothes and ask about sizes and prices.",
                category = TopicCategory.EVERYDAY,
                scenarioPrompt =
                    "You are a helpful shop assistant in a clothes shop on Oxford Street. Help the " +
                        "learner find items, discuss sizes, colours and prices, and the fitting room.",
                sampleOpeners =
                    listOf(
                        "Hiya! Are you alright there, or can I help you find anything?",
                        "Hello! Let me know if you'd like a hand with sizes.",
                    ),
            ),
            Topic(
                id = "doctor",
                title = "At the Doctor's",
                description = "Describe symptoms and understand the doctor's advice.",
                category = TopicCategory.EVERYDAY,
                scenarioPrompt =
                    "You are a kind GP at an NHS surgery. Ask the learner about their symptoms, give " +
                        "simple advice, and be reassuring. Keep medical language plain.",
                sampleOpeners =
                    listOf(
                        "Hello, come on in and take a seat. So, what's brought you in today?",
                        "Morning! How are you feeling? Tell me what's been going on.",
                    ),
            ),
            Topic(
                id = "making_friends",
                title = "Making Friends",
                description = "Meet someone new at a party and get to know them.",
                category = TopicCategory.SOCIAL,
                scenarioPrompt =
                    "You are a friendly person the learner has just met at a house party. Make small " +
                        "talk, find common ground and keep the conversation warm and easy.",
                sampleOpeners =
                    listOf(
                        "Hiya! I don't think we've met — I'm Sam. How do you know the host?",
                        "Hello! Great party, isn't it? So what do you get up to?",
                    ),
            ),
            Topic(
                id = "directions",
                title = "Asking for Directions",
                description = "Ask for and understand directions around town.",
                category = TopicCategory.TRAVEL,
                scenarioPrompt =
                    "You are a friendly local in Manchester. The learner stops you to ask for " +
                        "directions. Give clear, natural directions and a friendly tip or two.",
                sampleOpeners =
                    listOf(
                        "You alright? You look a bit lost — where are you trying to get to?",
                        "Hiya! Need a hand finding somewhere?",
                    ),
            ),
            Topic(
                id = "small_talk_weather",
                title = "British Small Talk",
                description = "Master the great British art of weather small talk.",
                category = TopicCategory.SOCIAL,
                scenarioPrompt =
                    "You are a chatty neighbour bumping into the learner at the bus stop. Make classic " +
                        "British small talk — the weather, the weekend, the bins — light and friendly.",
                sampleOpeners =
                    listOf(
                        "Morning! Bit grim out today, isn't it? Reckon it'll clear up?",
                        "Alright? Cold one this morning! Off anywhere nice?",
                    ),
            ),
            Topic(
                id = "ielts_speaking",
                title = "IELTS Speaking Practice",
                description = "Practise IELTS-style speaking questions with feedback.",
                category = TopicCategory.EXAM,
                scenarioPrompt =
                    "You are an IELTS speaking examiner. Move through Part 1 style questions about " +
                        "familiar topics, then a short Part 2 cue card. Stay encouraging but exam-like.",
                sampleOpeners =
                    listOf(
                        "Good morning. Let's begin. Can you tell me your full name, please?",
                        "Hello. We'll start with some questions about yourself. Where are you from?",
                    ),
            ),
        )

    private val byId: Map<String, Topic> = all.associateBy(Topic::id)

    fun byId(id: String): Topic? = byId[id]

    fun byCategory(category: TopicCategory): List<Topic> = all.filter { it.category == category }
}
