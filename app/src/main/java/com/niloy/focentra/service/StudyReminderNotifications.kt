package com.niloy.focentra.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.niloy.focentra.FocentraApp
import com.niloy.focentra.MainActivity
import com.niloy.focentra.R
import com.niloy.focentra.data.local.entity.NotificationEntity
import com.niloy.focentra.data.local.entity.ReminderEntity
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.viewmodel.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

object StudyReminderNotifications {
    const val ALERT_CHANNEL_ID = "focentra_alerts_channel"
    private const val TAG = "StudyReminders"

    const val ACTION_FIRE_REMINDER = "com.niloy.focentra.action.FIRE_REMINDER"
    const val ACTION_SCHEDULED_ALERT = "com.niloy.focentra.action.SCHEDULED_ALERT"
    const val ACTION_REVISION_ALARM = "com.niloy.focentra.action.REVISION_ALARM"
    const val ACTION_RECAP_ALARM = "com.niloy.focentra.action.RECAP_ALARM"
    const val ACTION_DAILY_TARGET_7AM = "com.niloy.focentra.action.DAILY_TARGET_7AM"
    const val ACTION_DAILY_TARGET_730AM = "com.niloy.focentra.action.DAILY_TARGET_730AM"
    const val ACTION_MOTIVATION_7AM = "com.niloy.focentra.action.MOTIVATION_7AM"
    const val ACTION_MOTIVATION_630PM = "com.niloy.focentra.action.MOTIVATION_630PM"
    const val ACTION_MOTIVATION_1020PM = "com.niloy.focentra.action.MOTIVATION_1020PM"

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_CHAPTER_ID = "chapter_id"
    const val EXTRA_SUBJECT_NAME = "subject_name"
    const val EXTRA_CHAPTER_NAME = "chapter_name"
    const val EXTRA_RECAP_STAGE = "recap_stage"
    const val EXTRA_DUE_AT = "due_at"
    const val EXTRA_SKIP_AT = "skip_at"

    val MOTIVATIONAL_TITLES = listOf(
        "ওঠ, বই খোল!",
        "আগুনটা আজই জ্বালা",
        "থামলে চলবে না",
        "যুদ্ধ শুরু, পিছু হটা নেই",
        "জবাব দে রেজাল্টে",
        "ফোন নামা, ভাগ্য ধর",
        "এই সময়টাই তোর অস্ত্র",
        "আজকের ঘাম, কালকের গর্ব",
        "মা-বাবার স্বপ্নটা মনে রাখ",
        "কষ্টই তোর সিঁড়ি",
        "জ্বলে ওঠার সময় এখনই",
        "ফিরে আয় পড়ার টেবিলে",
        "প্রতিটা মিনিট এক-একটা ইট",
        "ক্লান্ত? তবু চল!",
        "ভয়কে হারিয়ে দে",
        "সিংহের মতো লড়, সিংহের মতো জেত",
        "আজ নিজেকে ছাড়িয়ে যা",
        "তোর যুদ্ধ, তোকেই লড়তে হবে",
        "লক্ষ্য একটাই: জয়",
        "কলমই তোর তলোয়ার",
        "দেখিয়ে দে, তুই কী!",
        "নিজের জন্য জ্বলে ওঠ",
        "আজকের জেদ, কালকের জয়",
        "থেমে গেলেই হার",
        "সময় ডাকছে, সাড়া দে!",
        "বইয়ের পাতায় ভাগ্য লেখা",
        "আলসেমির দিন শেষ",
        "সেরাটা এখনো বাকি",
        "শেষ না দেখে ছাড়বি না",
        "রোজ এক ধাপ উপরে",
        "রিভিশনেই রাজা",
        "আজকের পড়া, কালকের পরিচয়",
        "জেদটা ধরে রাখ",
        "ঝড় এলেও মাথা নোয়াস না",
        "তোকে থামাবে কে?",
        "নিজেকে প্রমাণ করার দিন আজ",
        "ছোট ছোট জয়, বড় সাফল্য",
        "পুড়ে পুড়ে সোনা হয়ে ওঠ",
        "আরও এক ঘণ্টা, আরও এক ধাপ",
        "লড়াই এখন, উৎসব পরে",
        "চোখে স্বপ্ন, হাতে কলম",
        "পেছনে নয়, সামনে তাকা",
        "সময় ফুরোনোর আগে জেগে ওঠ",
        "ছাড় নেই, নিজেকেও না",
        "রক্তে জেদ, চোখে আগুন",
        "তুই পারবি, তুইই পারবি",
        "আজকের ঘামে কালকের ফসল",
        "শূন্য থেকে শিখরে",
        "এবার তোর পালা",
        "কাল নয়, এখনই!",
        "অপমানের জবাব হবে জয়",
        "হার মানা তোর স্বভাবে নেই",
        "ইতিহাস লেখার সময় এটাই",
        "মন না চাইলেও বস",
        "স্ক্রল নয়, স্বপ্ন!",
        "ধারাবাহিকতাই তোর অস্ত্র",
        "কঠিনকে জয় কর",
        "শুরু কর, বাকিটা জেদ সামলাবে",
        "আজ নিজের সেরাটা দে!",
        "তোর জয় অপেক্ষা করছে",
        "বই তোর অপেক্ষায়, দেরি করিস না",
        "আজকের রাতটা তোর",
        "স্বপ্নটা বড়, তাই লড়াইটাও বড়",
        "একটু কষ্ট, অনেক গর্ব",
        "থামার অনুমতি নেই",
        "নিজের সঙ্গে নিজের যুদ্ধ",
        "আজ হার মানলে কাল আফসোস",
        "পড়ার টেবিলই তোর রণক্ষেত্র",
        "মনটাকে শাসন কর",
        "এক পাতা আরও, এক ধাপ আরও",
        "ব্যর্থতাকে সিঁড়ি বানা",
        "ফোকাস, ফোকাস, ফোকাস!",
        "যারা হাসছে, তাদের থামিয়ে দে",
        "আজ না পড়লে কবে?",
        "তোর সময় এসে গেছে",
        "স্বপ্নের দাম দিতে হয়",
        "কঠিন দিনেই চেনা যায় যোদ্ধা",
        "কলম ধর, কাঁপুক প্রতিদ্বন্দ্বী",
        "আজকের রুটিন, কালকের রেকর্ড",
        "হাল ছাড়িস না, পাল তোল",
        "তোর পরিশ্রম মিথ্যে হবে না",
        "আরেকটু টান, তারপর বিজয়",
        "চেষ্টাই তোর পরিচয়",
        "নিজের ওপর বিশ্বাস রাখ",
        "নিঃশব্দে খাট, গর্জে ওঠ ফলাফলে",
        "দিন গড়ায়, পরীক্ষা এগোয়",
        "ভিড়ে মিশে যাস না, আলাদা হ",
        "আজকের লক্ষ্য: গতকালের চেয়ে ভালো",
        "অজুহাত বন্ধ, পড়া শুরু",
        "বইয়ে ডুব দে, সাফল্যে ভেস",
        "মাথা উঁচু করে ফিরবি",
        "ধৈর্যই তোর শক্তি",
        "রাত জাগা স্বপ্নের জন্য",
        "এই কষ্ট একদিন গল্প হবে",
        "যুদ্ধ চলছে, থামিস না",
        "সেরা হতে হলে সেরা খাটতে হবে",
        "অর্ধেক পথ পেরিয়েছিস, থামিস না",
        "আজই প্রমাণ কর, তুই কম নোস",
        "লক্ষ্যের দিকে চোখ রাখ",
        "পরাজয় নয়, প্রস্তুতি",
        "জেগে ওঠ, যোদ্ধা!",
        "নোট খোল, নিজেকে গড়",
        "সফলতা ঘুমিয়ে আসে না",
        "ছোট্ট এক ঘণ্টা, বিশাল পরিবর্তন",
        "পরীক্ষার হলে হাসবি, আজ খাট",
        "সন্দেহ ঝেড়ে ফেল, এগিয়ে যা",
        "তোর স্বপ্ন তোর দায়িত্ব",
        "শেষ বেঞ্চ থেকে প্রথম সারিতে",
        "আজ শুরু, কাল জয়",
        "নিজের সেরা সংস্করণ হয়ে ওঠ"
    )

    val MOTIVATIONAL_QUOTES = listOf(
        "যারা বলেছিল তুই পারবি না, তাদের জবাব মুখে দিস না, রেজাল্ট দিয়ে দিবি। আজ একটা একটা করে পাতা শেষ কর, কাল গোটা দুনিয়া তোর নাম বলবে।",
        "মন চাইলে তো সবাই পড়ে। মন না চাইলেও যে বই খুলে বসে, জয় শুধু তারই। আজ সেই মানুষটা হয়ে ওঠ।",
        "কষ্ট কিছুদিনের, আফসোস সারা জীবনের। আজ একটু কষ্ট করে নে, কাল মাথা উঁচু করে হাঁটতে পারবি।",
        "মা-বাবার কপালের ভাঁজটা একবার মনে কর। ওই ভাঁজ মুছে দেওয়ার ক্ষমতা তোর হাতেই আছে, আর তার চাবি আজকের পড়া।",
        "এই মুহূর্তে কেউ একজন ঠিক তোর স্বপ্নের জায়গাটার জন্য পড়ছে। তুই কি তাকে জিততে দিবি? যদি না দিস, তাহলে বই খোল, এখনই!",
        "ফোনটা উপুড় করে রাখ, বইটা সোজা কর। স্ক্রিনে হারানো প্রতিটা মিনিট তোর স্বপ্ন থেকে চুরি হয়ে যাচ্ছে। এই চুরি থামানোর ক্ষমতা শুধু তোর হাতে।",
        "তুই সাধারণ কেউ নস, তুই লড়াকু। লড়াকুরা ক্লান্ত হয়, কিন্তু থেমে যায় না। উঠে দাঁড়া, কলম ধর, নিজের যুদ্ধটা শেষ কর!",
        "সোনা আগুনে পুড়েই খাঁটি হয়, ইস্পাত আগুনেই শক্ত হয়। আজ যে কষ্টে পুড়ছিস, সেটাই তোকে গড়ছে। চালিয়ে যা, আর একটু!",
        "পাহাড় একদিনে ডিঙানো যায় না, কিন্তু প্রতিটা পদক্ষেপ তোকে চূড়ার দিকে নেয়। আজ আরও একটা অধ্যায় শেষ কর, এটাই তোর এক পা এগিয়ে যাওয়া।",
        "আজকের দিনটা আর কোনোদিন ফিরবে না। হয় তুই এটাকে কাজে লাগাবি, নয়তো এটা তোকে পেছনে ফেলে চলে যাবে। সিদ্ধান্ত তোর, কিন্তু ফল বইতে হবে তোকেই।",
        "‘পারব না’ কথাটা আসলে তোর ভয়ের বানানো মিথ্যা। একবার শুরু করে দেখ, পারার ক্ষমতা তোর ভেতরেই ছিল, শুধু তোর ডাকের অপেক্ষায় ছিল।",
        "দশ বছর পরের তুই আজকের তোকে দেখছে। সে কি গর্ব করে বলবে ‘ওই দিনটা আমি ছাড়িনি’, নাকি আফসোসে মাথা নিচু করবে? উত্তরটা এখন তোর হাতে।",
        "পরীক্ষার হলে বসে যেন কখনও ভাবতে না হয়, ‘ইশ, যদি আরেকটু পড়তাম!’ আজকের পড়াটাই সেই আফসোসের একমাত্র জবাব।",
        "এখন কষ্ট, পরে সম্মান। এই হিসাব কখনও ভুল হয় না। যে আজ ঘাম ঝরায়, কাল তার জন্যই হাততালি বাজে।",
        "আরামের খোলসটা ভেঙে বেরিয়ে আয়। স্বপ্ন কখনও আরামে ধরা দেয় না, সাফল্যের ঠিকানা কষ্টের গলির শেষ মাথায়।",
        "প্রতিভা নয়, পরিশ্রমই শেষে জেতে। আজ সবার চেয়ে মাত্র এক ঘণ্টা বেশি পড়, দিনের পর দিন সেই এক ঘণ্টাই তোকে সবার ওপরে তুলে দেবে।",
        "ব্যথা দুই রকম: শৃঙ্খলার ব্যথা আর আফসোসের ব্যথা। শৃঙ্খলার ব্যথাটা আজ হাসিমুখে নে, তাহলে আফসোসের ব্যথা আর কখনও আসবে না।",
        "তোর রণক্ষেত্র ওই পড়ার টেবিল, তোর তলোয়ার ওই কলম। যুদ্ধ শুরুর আগেই ময়দান ছেড়ে পালানো ভীরুতা। আজ ময়দানে নেমে পড়!",
        "যেদিন সবাই তোকে নিয়ে হেসেছিল, সেদিন ভেতরে যে আগুনটা জ্বলেছিল, আজ সেটাকে বইয়ের পাতায় ঢেলে দে। ওই হাসির একমাত্র জবাব তোর সাফল্য।",
        "আজ যে অধ্যায়টা সবচেয়ে কঠিন লাগছে, সবার আগে ওটাকেই ধর। কঠিনটাকে জিতলে বাকিটা খেলা।",
        "ভয় পেলে থেমে যাবি, জেদ ধরলে পেরিয়ে যাবি। ভয়ের ওপর পা রেখেই মানুষ চূড়ায় ওঠে। আজ ভয়টাকেই সিঁড়ি বানিয়ে নে!",
        "তোর ভবিষ্যৎ কোনো ভাগ্যের হাতে নেই, আছে তোর কলমের ডগায়। আজ যা লিখবি, কাল দুনিয়া সেটাই পড়বে।",
        "ক্লান্ত লাগছে? ভালো, মানে তুই এমন কিছু করছিস যা সবাই পারে না। সহজ পথে কেউ ইতিহাস গড়ে না। আরও একটু জোর লাগা!",
        "যখন সবাই আড্ডা আর ফোনে ডুবে থাকে, তখন যে একা বই নিয়ে বসে, একদিন সে-ই সবাইকে ছাড়িয়ে যায়। একা লাগলেও এই পথটাই জয়ের পথ।",
        "একদিনের পড়া নয়, প্রতিদিনের পড়াই জেতায়। ধারাবাহিকতাই তোর গোপন অস্ত্র। আজকের পড়াটা শেষ কর, কাল আরও শক্ত হয়ে ফিরবি।",
        "পরিবার কত আশা নিয়ে তোর দিকে তাকিয়ে আছে, জানিস? ওদের সব ত্যাগের দাম চুকিয়ে দেওয়ার একমাত্র উপায় তোর সাফল্য। আজ সেই পথে আরও এক ধাপ এগিয়ে যা।",
        "রিলস, গেম, চ্যাটিং: এগুলো তোর সময় খায়, বদলে কিছু দেয় না। যে ঘণ্টাটা স্ক্রলে যায়, সেটা তোর স্বপ্ন থেকে কেটে নেওয়া এক ঘণ্টা। ফোনটা সরা, বই ধর!",
        "দুনিয়াকে নয়, আগে নিজেকে প্রমাণ কর। যে নিজের কাছে জিতে যায়, দুনিয়া তার জন্য রাস্তা ছেড়ে দেয়।",
        "এতদূর এসে ফিরে যাওয়ার কোনো মানে হয় না। যতটুকু কষ্ট করেছিস, তার ফল না নিয়ে ছাড়বি না। শেষ পর্যন্ত লড়ে যা!",
        "ঝড়ে সেই গাছই টিকে থাকে, যার শিকড় গভীর। তোর শিকড় তোর প্রস্তুতি। আজ এমন প্রস্তুতি নে, যেন পরীক্ষার কোনো প্রশ্নই তোকে নাড়াতে না পারে।",
        "সময় যাবেই, তুই পড়লেও, না পড়লেও। শুধু পড়লে সময়টা তোর হয়ে যায়। চল, সময়টাকে আজ নিজের করে নে!",
        "তুই জন্মেছিস জয়ের জন্য, ভিড়ে হারিয়ে যাওয়ার জন্য নয়। নিজের ভেতরের সিংহটাকে জাগিয়ে তোল, আজ তোর গর্জনটা শোনাতে হবে রেজাল্টে!",
        "রিভিশন ছাড়া পড়া মানে বালির ওপর ঘর বানানো, একটা ঢেউ এলেই সব শেষ। বারবার ঝালিয়ে নিজের জ্ঞানটাকে ইস্পাতের মতো শক্ত কর।",
        "প্রতিশোধ নিতে চাস? তাহলে সফল হ। অপমানের সবচেয়ে সুন্দর জবাব একটাই: এমন এক জয়, যেটাকে আর কেউ অস্বীকার করতে পারে না।",
        "আজকের পড়া হলো বীজ, কালকের সাফল্য হলো ফসল। যে বীজ বোনে না, সে ফসলের স্বপ্নও দেখতে পারে না। আজ প্রাণ ঢেলে বীজ বুনে দে!",
        "‘কাল থেকে শুরু করব’: এই একটা বাক্য কত স্বপ্নকে খেয়ে ফেলেছে! তোর সেই ‘কাল’ আজই। বই খোল, এখনই!",
        "বড় কিছু যারা করেছে, তারাও একদিন তোর মতোই ক্লান্ত ছিল, ভয় পেয়েছিল, ছেড়ে দিতে চেয়েছিল। পার্থক্য শুধু একটাই, তারা ছাড়েনি।",
        "সময় সোনার চেয়েও দামি, কারণ সোনা ফিরে পাওয়া যায়, সময় যায় না। আজকের প্রতিটা ঘণ্টার হিসাব তোকেই দিতে হবে নিজের কাছে।",
        "চ্যাম্পিয়নরা কখনও মুড দেখে প্র্যাকটিস করে না। তুইও মুড দেখে পড়বি না। নিয়ম করে বসবি, নিয়ম করেই জিতবি!",
        "তোর প্রতিটা সেশন, প্রতিটা অধ্যায় ভবিষ্যতের এক-একটা ইট। আজ যত ইট গাঁথবি, কাল তোর স্বপ্নের প্রাসাদ তত উঁচু হবে।",
        "তোর সবচেয়ে বড় প্রতিদ্বন্দ্বী অন্য কেউ নয়, গতকালের তুই। আজ তাকে হারিয়ে দে: এক ঘণ্টা বেশি, এক পাতা বেশি, এক ধাপ এগিয়ে!",
        "পড়তে বসতে কষ্ট হয় শুধু প্রথম পাঁচ মিনিট। তারপর আগুনটা নিজেই জ্বলে ওঠে। আজ শুধু ওই পাঁচ মিনিট পার করে দে, বাকিটা তোর জেদ সামলে নেবে।",
        "আজ ফাঁকি দিলে কেউ টের পাবে না, শুধু তুই জানবি। আর ওই জানাটাই একদিন তোকে সবচেয়ে বেশি কষ্ট দেবে। নিজের কাছে সৎ থাক, পড়তে বস!",
        "লোকে তোর স্বপ্ন নিয়ে হাসে? হাসুক। ওদের হাসি যত জোরে হবে, তোর জয় তত মিষ্টি হবে। শুধু থামিস না!",
        "সাফল্য কোনো লটারি নয়, এটা প্রতিদিনের ছোট ছোট সিদ্ধান্তের যোগফল। আজকের সিদ্ধান্তটা কী: আরাম, নাকি জয়?",
        "চাপ আর তাপেই কয়লা হীরা হয়। সিলেবাসের চাপ, সময়ের চাপ, সবটাই তোকে ঝলমলে করে গড়ছে। ভেঙে পড়িস না, জ্বলে ওঠ!",
        "যাদের মুখে হাসি ফোটানোর জন্য তোর এই লড়াই, একবার তাদের মুখগুলো মনে কর। ওই হাসির কাছে আজকের ক্লান্তি কিছুই না। আবার শুরু কর!",
        "পরিকল্পনা ছাড়া পড়া অন্ধকারে তীর ছোড়ার মতো। আজকের লক্ষ্যটা ঠিক কর, শেষ না করে উঠবি না। ছোট ছোট জয়ই বড় বিজয়ের সিঁড়ি।",
        "তুই নিজেও জানিস না তোর ভেতরে কতটা ক্ষমতা লুকিয়ে আছে। আজ নিজেকে একটু ঠেলে দিয়ে দেখ, যেটাকে অসম্ভব ভাবতিস সেটাই হার মানবে।",
        "ভাবতে ভাবতে সময় ফুরোয়, শুরু করলেই পথ খোলে। বইটা খোল, এই মুহূর্তেই। শুরুটাই অর্ধেক জয়!",
        "যতবার পড়ে গেছিস, ততবার উঠে দাঁড়া। জয়ের আগে যে একবারও হোঁচট খায়নি, সে জয়ের আসল স্বাদ চেনেই না।",
        "কেউ জানতে চাইবে না তুই কত কষ্ট করেছিস, সবাই শুধু রেজাল্টটা দেখবে। তাই এমন রেজাল্ট কর, যাতে সবাই থমকে দাঁড়ায়!",
        "একদিন তুই নিজের সাফল্যের গল্প বলবি আর সবাই মুগ্ধ হয়ে শুনবে। সেই গল্পের সবচেয়ে কঠিন অধ্যায়টা এখন লেখা হচ্ছে, আজ। কলম থামাস না!",
        "সুযোগ সবসময় দরজায় কড়া নাড়ে না। আজ তৈরি না থাকলে কাল দরজা খুলে দেখবি সে চলে গেছে। তৈরি হ, এখনই!",
        "এক পাতা, এক টপিক, এক অধ্যায়: এভাবেই পাহাড় ডিঙানো যায়। পুরো সিলেবাসের দিকে তাকিয়ে ঘাবড়াস না। সামনের পাতাটা শেষ কর, তারপর পরের পাতা!",
        "ভাগ্যের অপেক্ষায় বসে থাকিস না, ভাগ্য নিজের হাতে বানিয়ে নে। ঘাম ঝরালে ভাগ্যও রাস্তা ছেড়ে দেয়।",
        "২৪ ঘণ্টা সবার কাছেই সমান। পার্থক্য শুধু একটাই: কে সেটা কীভাবে খরচ করে। আজ তোর সময়টা সেরাভাবে খরচ কর!",
        "রোজ মাত্র এক ঘণ্টা বেশি পড়লে বছর শেষে হয় ৩৬৫ ঘণ্টা, মানে টানা পনেরো দিন-রাত বেশি পড়ার সমান! ছোট ছোট ঘণ্টাই বড় জয় গড়ে।",
        "আয়নার সামনে দাঁড়িয়ে নিজেকে জিজ্ঞেস কর: আমি কি আমার সেরাটা দিচ্ছি? উত্তর ‘না’ হলে আজই সেটা ‘হ্যাঁ’ করে দে!",
        "যখন মনে হবে আর পারছি না, তখনই আসল লড়াই শুরু। যখন মনে হবে ছেড়ে দিই, তখনই এক পা এগিয়ে যা। জয় সবসময় ওই এক পায়ের ওপারেই থাকে।",
        "‘পারব না’, ‘হবে না’, ‘অনেক কঠিন’: এই তিনটা কথা আজ থেকে তোর অভিধান থেকে মুছে দে। শুধু মনে রাখ, তুই পারবি, তোর দ্বারাই হবে, আর কঠিন বলেই জয়টা এত মধুর।",
        "দুর্বল মন বলে ‘আজ থাক’, শক্ত মন বলে ‘আজই’। কার কথা শুনবি, সিদ্ধান্ত তোর। তবে জয় শুধু শক্ত মনেরই।",
        "লক্ষ্য যত বড়, লড়াই তত কঠিন, আর জয় তত গৌরবের। ছোট লক্ষ্যে সুখ আছে, বড় লক্ষ্যে ইতিহাস আছে। তুই কোনটা চাস?",
        "‘ঠিক সময়’ বলে কিছু হয় না। যে এই মুহূর্তে শুরু করে, সময় তার জন্যই ঠিক হয়ে যায়। তাই আজ, এখনই!"
        "তোর হাতের বইটা আসলে একটা চাবি। কোন দরজা খুলবে, সেটা তুই জানিস। আজ চাবিটা ঘোরানোর দিন, ফেলে রাখার দিন নয়।",
        "যে রাতটা আজ তুই পড়ে কাটাচ্ছিস, সেই রাতটাই একদিন তোর সবচেয়ে গর্বের স্মৃতি হবে। তাই ঘুম ঘুম ভাবটা ঝেড়ে ফেল, আরও একটু টান।",
        "সবাই শেষ ফলটা দেখে, কিন্তু ফলের পেছনের রাতজাগা, ক্লান্তি আর জেদটা শুধু তুই জানিস। ওই গোপন লড়াইটাই তোকে আলাদা করবে।",
        "আজ যে বিষয়টা ভয় দেখাচ্ছে, কাল সেটাই তোর সবচেয়ে প্রিয় বিষয় হতে পারে। ভয়কে এড়িয়ে নয়, সোজা তার চোখে চোখ রেখে বস।",
        "ঘড়ির কাঁটা কারও জন্য থামে না। তুই বসে থাকলেও ঘোরে, পড়লেও ঘোরে। শুধু পড়লে প্রতিটা ঘোরা তোর জয়ের দিকে যায়।",
        "তুই যত দেরিতে শুরু করবি, পথ তত লম্বা মনে হবে। যত আগে শুরু করবি, গন্তব্য তত কাছে আসবে। সিদ্ধান্তটা এই মুহূর্তের।",
        "ভালো ছাত্র আর সেরা ছাত্রের মধ্যে পার্থক্য প্রতিভার নয়, ধৈর্যের। যে একই অধ্যায় তিনবার ঝালায়, সে-ই পরীক্ষার হলে নিশ্চিন্তে হাসে।",
        "মনে রাখিস, সবাই একদিনে বড় হয়নি। প্রতিদিন একটু একটু করে এগিয়েছে। আজ তোর সেই একটু এগোনোর দিন।",
        "বন্ধুরা ঘুরতে গেছে, তুই বইয়ের সামনে। খারাপ লাগছে জানি, কিন্তু আজ যে ত্যাগটা করছিস, কাল সেটাই তোকে সবার চেয়ে এগিয়ে রাখবে।",
        "কেউ তোকে জোর করে পড়াতে পারবে না, নিজের ভেতর থেকে আগুনটা জ্বালাতে হবে। আজ সেই দেশলাইটা তোর নিজের হাতেই।",
        "একটা ভুল উত্তর মানে তুই হেরে গেছিস না, মানে তুই একটা নতুন জিনিস শিখেছিস। ভুলকে ভয় পাস না, ভুল থেকেই সেরা ছাত্ররা তৈরি হয়।",
        "যে স্বপ্নটা তুই রাতে দেখিস, দিনে সেটার জন্য কী করছিস? স্বপ্ন আর বাস্তবের মাঝখানের সেতুটার নাম পরিশ্রম। আজ সেতুর একটা তক্তা বসা।",
        "তোর পেছনে কেউ নেই ভাবছিস? ভুল। তোর পেছনে তোর মা-বাবার দোয়া আছে, তোর নিজের স্বপ্ন আছে। এই দুটোই একজন যোদ্ধার জন্য যথেষ্ট।",
        "আজ মন খারাপ, শরীর ক্লান্ত, তবু বই খুলে বসেছিস, এটাই তো আসল জয়। বাকি পড়াটা শুধু এই জয়ের ওপর রঙ চড়ানো।",
        "সাফল্যের কোনো শর্টকাট নেই, কিন্তু একটা গোপন নিয়ম আছে: প্রতিদিন হাজির থাকা। কোনোদিন কম, কোনোদিন বেশি, কিন্তু প্রতিদিন।",
        "তুলনা করিস না কারও সাথে। তোর প্রতিযোগিতা তোর গতকালের সাথে। গতকালের চেয়ে সামান্য ভালো হতে পারলেই আজ তুই বিজয়ী।",
        "পরীক্ষার আগের শেষ কয়েকটা দিনই বদলে দিতে পারে সবকিছু। যে এই দিনগুলোয় হাল ছাড়ে না, সে-ই শেষ হাসিটা হাসে।",
        "কঠিন প্রশ্ন দেখে ঘাবড়ে যাস না। যে প্রশ্ন তোকে কাঁদায়, সেটাই অন্যদেরও কাঁদাবে। পার্থক্য হবে ওই একটু বাড়তি প্রস্তুতিতে।",
        "আজ যে রাস্তায় হাঁটছিস, সেটা নির্জন, অন্ধকার, কঠিন। কিন্তু প্রতিটা সফল মানুষই এই রাস্তা পেরিয়েছে। আলোটা একটু সামনেই।",
        "তোর কষ্ট তোর পরিবারের চোখ এড়ায় না। আজ ওরা কিছু বলছে না, কিন্তু রেজাল্টের দিন ওদের চোখের জল হবে আনন্দের। সেই জলের জন্য আজ খাট।",
        "নিজেকে বল: আমি পারব, আমি পারছি, আমি পারবই। কথাটা যতবার বলবি, মনটা ততটাই শক্ত হবে। আর শক্ত মনই সবচেয়ে বড় অস্ত্র।",
        "একদিন ফাঁকি দিলে মনে হয় কিছুই হয়নি। কিন্তু ওই একদিনই পরের দিনের অজুহাত হয়ে দাঁড়ায়। আজ ফাঁকির সেই চেইনটা ভেঙে দে।",
        "জীবনে কেউ সুযোগ দেবে না, সুযোগ বানাতে হয়। আর সবচেয়ে বড় সুযোগ হলো এই পরীক্ষাটা। এটাকে হেলা করিস না।",
        "যে পাতাটা পড়তে ইচ্ছে করছে না, ঠিক ওটাই পরীক্ষায় আসবে। এটাই নিয়ম। তাই যেটা এড়াতে চাস, আগে সেটাই শেষ কর।",
        "তোর ভেতরে একজন চ্যাম্পিয়ন ঘুমিয়ে আছে। ওকে জাগানোর জন্য কোনো অ্যালার্ম লাগে না, শুধু একটা সিদ্ধান্ত লাগে: আজ আমি সেরাটা দেব।",
        "আজ রাতে যখন সবাই ঘুমিয়ে, তখন তুই যে এক ঘণ্টা বেশি পড়বি, ওটাই তোর সিক্রেট। কেউ জানবে না, কিন্তু ফলটা সবাই দেখবে।",
        "নিজেকে ছোট ভাবিস না। যাদের তুই বড় মনে করিস, তারাও একদিন ঠিক তোর জায়গায় ছিল। শুধু তারা থামেনি, তাই আজ ওই জায়গায়।",
        "মাথায় চাপ লাগছে? গভীর একটা শ্বাস নে, এক গ্লাস পানি খা, তারপর আবার বস। বিরতি নেওয়া দুর্বলতা নয়, কিন্তু বিরতিতে হারিয়ে যাওয়া দুর্বলতা।",
        "কখনও কখনও মনে হবে সবাই এগিয়ে গেছে, তুই পিছিয়ে। কিন্তু দৌড়টা তো তোর নিজের সাথে। নিজের গতিতে চল, তবু থামিস না।",
        "সফল মানুষের ঘুম ভাঙে লক্ষ্যে, আরামে নয়। আজ সকালে উঠেই নিজেকে বল: আজকের দিনটা আমার, আর আমি এটা কাজে লাগাব।",
        "একটা লক্ষ্য ঠিক কর, আর তার পেছনে এমনভাবে লেগে থাক যেন ওটা তোর নিঃশ্বাসের মতো জরুরি। লক্ষ্য যার স্পষ্ট, রাস্তা তার সহজ।",
        "আজকের কষ্টটাকে ঘৃণা করিস না, ওটাকে ধন্যবাদ জানা। ও-ই তোকে সেই মানুষটা বানাচ্ছে, যে একদিন সব বাধা পেরিয়ে যাবে।",
        "তোর সামনে এখন দুটো পথ: আরাম আর আফসোস, অথবা কষ্ট আর গৌরব। দ্বিতীয়টা কঠিন, কিন্তু শেষে ওটাই তোকে মাথা উঁচু করে দাঁড় করাবে।",
        "হাজারটা চিন্তা মাথায় ঘুরছে? সব ঝেড়ে ফেলে শুধু একটা কাজ কর: সামনের পাতাটা পড়। বাকি চিন্তা পরে, আগে এই পাতা।",
        "যারা আজ তোকে সন্দেহ করছে, একদিন তারাই তোর উদাহরণ দেবে। শুধু ওই দিনটা আসা পর্যন্ত ধৈর্য ধর আর পড়ে যা।",
        "তুই ক্লান্ত হতে পারিস, বিরক্ত হতে পারিস, কিন্তু হাল ছেড়ে দেওয়ার অধিকার তোর নেই। কারণ তোর স্বপ্নের পেছনে অনেক মানুষের আশা জড়িয়ে আছে।",
        "সময়ের ব্যবহারই বলে দেয় তুই কোথায় পৌঁছাবি। আজ প্রতিটা ঘণ্টা এমনভাবে কাটা, যেন সেটাই তোর শেষ সুযোগ।",
        "তোর নোটের প্রতিটা লাইন একটা করে ছোট্ট জয়। যত লাইন শেষ করবি, জয়ের মালা তত লম্বা হবে। একটা একটা করে গাঁথ।",
        "যখন বইয়ের অক্ষরগুলো ঝাপসা লাগে, তখন চোখে পানি ছিটিয়ে আবার বস। এই পাঁচ মিনিটের জেদটাই পরে পাঁচ বছরের গর্ব হয়ে ফিরবে।",
        "লড়াইয়ের ময়দানে সবচেয়ে বড় বিজয় হলো নিজের আলসেমিকে হারানো। ওকে হারাতে পারলে আর কাউকে ভয় পাওয়ার কিছু নেই।",
        "আজকের দিনটা যেমন কাটাবি, তোর ভবিষ্যৎ তেমন হবে। এটা কোনো ভয় দেখানো কথা নয়, এটা সোজা হিসাব। হিসাবটা নিজের পক্ষে বানা।",
        "একটু একটু করে জমানো জ্ঞানই পরীক্ষার হলে সবচেয়ে বড় সম্পদ হয়ে দাঁড়ায়। আজ আরও কিছুটা জমা কর, ব্যাংকটা তোর নিজের মাথা।",
        "পড়ার সময় কেউ পাশে না থাকলেও তুই একা নস। তোর স্বপ্ন, তোর পরিশ্রম আর তোর জেদ, এই তিনজন সবসময় তোর সঙ্গে আছে।",
        "যে ছাত্র আজ হাল ছাড়ে না, সে একদিন শিক্ষকদের গর্ব হয়, পরিবারের অহংকার হয়, আর নিজের কাছে এক অনুপ্রেরণা হয়। তুই সেই ছাত্রটাই হবি।",
        "ভেঙে পড়ার মতো দিনও আসবে, তখন মনে করিস কেন শুরু করেছিলি। ওই কারণটাই তোকে আবার দাঁড় করাবে, আবার চালাবে।",
        "তোর প্রতিদিনের এই ছোট্ট রুটিনটাই একদিন তোর সাফল্যের গল্প হবে। তাই রুটিনটাকে অবহেলা করিস না, ওটাই তোর ভিত্তি।",
        "মনে রাখিস, পরীক্ষার খাতায় তোর নামটা শুধু একটা নাম নয়, ওটা তোর পুরো পরিশ্রমের স্বাক্ষর। আজ এমন প্রস্তুতি নে, যেন নিজের নামটা নিয়ে গর্ব হয়।",
        "আজকের এক ঘণ্টার মনোযোগ, কালকের এক নম্বর বাড়তি। আর ওই এক নম্বরই কখনও কখনও পুরো ভাগ্য বদলে দেয়। প্রতিটা মিনিট দামি।",
        "হার না মানা মানুষকে কেউ হারাতে পারে না, কারণ সে হারলেও আবার ফিরে আসে। তুইও ফিরে আসার মানুষ। আজ আবার শুরু কর।",
        "শেষ পর্যন্ত লড়ে যাওয়ার মধ্যেই আসল সৌন্দর্য। জেতা হারা পরের কথা, আগে নিজেকে বল: আমি আমার সবটুকু দিয়েছি। আজও তাই দে।",
        "তোর স্বপ্নের জায়গাটা আর বেশি দূরে নয়। মাত্র কয়েকটা দিন, কয়েকটা অধ্যায়, কয়েকটা রাত। এই শেষ ধাক্কাটা পুরো শক্তি দিয়ে দে, আর পেছনে তাকাস না!"
    )

    fun scheduleRecapReminders(
        context: Context,
        subjectName: String,
        chapterName: String,
        recapTimestamp: Long,
        skippedAtTimestamp: Long
    ) {
        val requestCode = "recap:$subjectName:$chapterName:$recapTimestamp".hashCode()
        listOf("1h", "30m").forEach { oldStage ->
            val oldIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                action = ACTION_RECAP_ALARM
                data = Uri.parse("focentra://recap/$oldStage/$recapTimestamp")
            }
            PendingIntent.getBroadcast(
                context,
                (recapTimestamp xor if (oldStage == "1h") 0x1111 else 0x3333).toInt(),
                oldIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it) }
        }
        if (recapTimestamp <= System.currentTimeMillis()) return

        val intent = Intent(context, StudyReminderReceiver::class.java).apply {
            action = ACTION_RECAP_ALARM
            data = Uri.parse("focentra://recap/$requestCode")
            putExtra(EXTRA_SUBJECT_NAME, subjectName)
            putExtra(EXTRA_CHAPTER_NAME, chapterName)
            putExtra(EXTRA_DUE_AT, recapTimestamp)
            putExtra(EXTRA_SKIP_AT, skippedAtTimestamp)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        StudyReminderScheduler.scheduleOneTime(context, recapTimestamp, pendingIntent)
    }

    fun post(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        navigateTo: String = "NOTIFICATIONS"
    ): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("focentra://notification/$notificationId/$navigateTo")
            putExtra("navigate_to", navigateTo)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission was revoked before the reminder could be posted.", exception)
            false
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Focentra Study Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for revision due, study goals, motivational alerts, and scheduled reminders"
            }
        )
    }

    internal fun reminderIntent(context: Context, reminderId: Long): Intent =
        Intent(context, StudyReminderReceiver::class.java).apply {
            action = ACTION_FIRE_REMINDER
            data = Uri.parse("focentra://reminder/$reminderId")
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

    internal fun reminderId(intent: Intent): Long =
        intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

    internal fun isReminderAction(intent: Intent?): Boolean =
        intent?.action in setOf(
            ACTION_FIRE_REMINDER,
            ACTION_SCHEDULED_ALERT,
            ACTION_REVISION_ALARM,
            ACTION_RECAP_ALARM
        )

    internal fun systemNotificationId(reminderId: Long): Int =
        0x40000000 or (reminderId.toInt() and 0x3FFFFFFF)
}

object StudyReminderScheduler {
    fun scheduleAll(
        context: Context,
        reminders: List<ReminderEntity>,
        chapters: List<ChapterEntity> = emptyList()
    ) {
        reminders.forEach { schedule(context, it) }
        chapters.forEach { scheduleChapterRevisions(context, it) }
        scheduleDailyAlarms(context)
    }

    fun scheduleDailyAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            StudyReminderNotifications.ACTION_DAILY_TARGET_7AM to 1001,
            StudyReminderNotifications.ACTION_DAILY_TARGET_730AM to 1002,
            StudyReminderNotifications.ACTION_MOTIVATION_7AM to 1003,
            StudyReminderNotifications.ACTION_MOTIVATION_630PM to 1004,
            StudyReminderNotifications.ACTION_MOTIVATION_1020PM to 1005
        ).forEach { (action, requestCode) ->
            val legacyIntent = Intent(context, StudyReminderReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("focentra://recurring/$requestCode")
            }
            PendingIntent.getBroadcast(
                context,
                requestCode,
                legacyIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let(alarmManager::cancel)
        }

        val now = System.currentTimeMillis()
        scheduledAlertTimes.forEach { slot ->
            val triggerAt = nextScheduleTimeMillis(slot.hour, slot.minute, slot.days, now) ?: return@forEach
            val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                action = StudyReminderNotifications.ACTION_SCHEDULED_ALERT
                data = Uri.parse("focentra://schedule/${slot.id}")
                putExtra(StudyReminderNotifications.EXTRA_SCHEDULE_ID, slot.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slot.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(context, alarmManager, triggerAt, pendingIntent)
        }
    }

    fun scheduleChapterRevisions(context: Context, chapter: ChapterEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val isComplete = chapter.tapCount >= 3 || chapter.status == "অধ্যায় Completely শেষ"
        if (!isComplete) {
            cancelChapterRevisions(context, chapter.id)
            return
        }

        val completedAt = chapter.completedTimestamp.takeIf { it > 0L } ?: chapter.lastStudiedTimestamp
        revisionStages.forEach { stage ->
            val dueAt = revisionDueAt(completedAt, stage)
            val intent = revisionIntent(context, chapter.id, stage.id, dueAt, chapter.subjectName, chapter.chapterName)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                revisionRequestCode(chapter.id, stage.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (dueAt > System.currentTimeMillis()) {
                setAlarm(context, alarmManager, dueAt, pendingIntent)
            } else {
                alarmManager.cancel(pendingIntent)
            }
        }
    }

    fun cancelChapterRevisions(context: Context, chapterId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        revisionStages.forEach { stage ->
            val intent = revisionIntent(context, chapterId, stage.id, 0L, "", "")
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                revisionRequestCode(chapterId, stage.id),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let(alarmManager::cancel)
        }
    }

    internal fun revisionIntent(
        context: Context,
        chapterId: Long,
        stage: String,
        dueAt: Long,
        subjectName: String,
        chapterName: String
    ): Intent = Intent(context, StudyReminderReceiver::class.java).apply {
        action = StudyReminderNotifications.ACTION_REVISION_ALARM
        data = Uri.parse("focentra://revision/$chapterId/$stage")
        putExtra(StudyReminderNotifications.EXTRA_CHAPTER_ID, chapterId)
        putExtra(StudyReminderNotifications.EXTRA_RECAP_STAGE, stage)
        putExtra(StudyReminderNotifications.EXTRA_DUE_AT, dueAt)
        putExtra(StudyReminderNotifications.EXTRA_SUBJECT_NAME, subjectName)
        putExtra(StudyReminderNotifications.EXTRA_CHAPTER_NAME, chapterName)
    }

    internal fun revisionRequestCode(chapterId: Long, stage: String): Int =
        "revision:$chapterId:$stage".hashCode()

    internal fun revisionTitle(stage: String): String? =
        revisionStages.firstOrNull { it.id == stage }?.title

    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (exception: SecurityException) {
            Log.w("StudyReminders", "Exact alarm access was revoked; using an inexact idle alarm.", exception)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleOneTime(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    fun schedule(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, reminder.id)
        val triggerAtMillis = nextReminderTimeMillis(reminder, System.currentTimeMillis())
        if (!reminder.enabled || triggerAtMillis == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        setAlarm(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminderId))
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            StudyReminderNotifications.reminderIntent(context, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class StudyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val validIntent = intent ?: return
        if (!StudyReminderNotifications.isReminderAction(validIntent)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FocentraApp
                val action = validIntent.action ?: ""

                when (action) {
                    StudyReminderNotifications.ACTION_SCHEDULED_ALERT -> {
                        val scheduleId = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_SCHEDULE_ID) ?: return@launch
                        StudyReminderScheduler.scheduleDailyAlarms(context)
                        val now = System.currentTimeMillis()
                        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

                        if (scheduleId != "evening_1830" || dayOfWeek in motivationalDays) {
                            val slotDate = java.text.SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date(now))
                            val index = Math.floorMod("$slotDate:$scheduleId".hashCode(), StudyReminderNotifications.MOTIVATIONAL_TITLES.size)
                            val title = StudyReminderNotifications.MOTIVATIONAL_TITLES[index]
                            val quote = StudyReminderNotifications.MOTIVATIONAL_QUOTES[
                                Math.floorMod("$scheduleId:$slotDate".hashCode(), StudyReminderNotifications.MOTIVATIONAL_QUOTES.size)
                            ]
                            val id = "mot_${slotDate}_$scheduleId"
                            StudyReminderNotifications.post(context, id.hashCode(), title, quote)
                            app.repository.insertNotification(
                                NotificationEntity(
                                    id = id,
                                    type = NotificationType.MOTIVATION.name,
                                    title = title,
                                    message = quote,
                                    timestamp = now
                                )
                            )
                        }

                        if (scheduleId == "evening_1830" &&
                            dayOfWeek in dailyTargetDays
                        ) {
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            val startOfDay = cal.timeInMillis
                            val sessions = app.database.sessionDao().getAllSessions()
                            var focusedSeconds = sessions
                                .filter { it.startTime in startOfDay..now }
                                .sumOf { it.actualFocusedSeconds }
                            val timerState = app.timerEngine.uiState.value
                            val timerStart = timerState.sessionStartTime
                            if (timerState.status == com.niloy.focentra.engine.TimerStatus.RUNNING ||
                                timerState.status == com.niloy.focentra.engine.TimerStatus.PAUSED
                            ) {
                                if (timerStart in startOfDay..now) {
                                    focusedSeconds += timerState.focusedSeconds
                                }
                            }
                            val goalMinutes = app.database.goalDao().getAllGoals()
                                .firstOrNull { it.periodType == "DAILY" && it.active }
                                ?.targetMinutes ?: 480
                            val focusedMinutes = (focusedSeconds / 60).toInt()
                            val percent = (focusedSeconds * 100L / (goalMinutes.coerceAtLeast(1) * 60L))
                                .toInt().coerceAtMost(100)
                            val title = "Daily Study Target"
                            val message = "আজ ${focusedMinutes} মিনিট পড়া হয়েছে—দৈনিক ${goalMinutes} মিনিটের লক্ষ্যের $percent% সম্পন্ন।"
                            val id = "daily_target_${java.text.SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date(now))}"
                            StudyReminderNotifications.post(context, id.hashCode(), title, message)
                            app.repository.insertNotification(
                                NotificationEntity(
                                    id = id,
                                    type = NotificationType.DAILY_TARGET.name,
                                    title = title,
                                    message = message,
                                    timestamp = now
                                )
                            )
                        }
                    }

                    StudyReminderNotifications.ACTION_REVISION_ALARM -> {
                        val chapterId = validIntent.getLongExtra(StudyReminderNotifications.EXTRA_CHAPTER_ID, -1L)
                        val chapter = app.database.chapterDao().getChapterById(chapterId)
                        val stage = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_RECAP_STAGE) ?: return@launch
                        val dueAt = validIntent.getLongExtra(StudyReminderNotifications.EXTRA_DUE_AT, 0L)
                        if (chapter != null &&
                            (chapter.tapCount >= 3 || chapter.status == "অধ্যায় Completely শেষ") &&
                            dueAt > 0L
                        ) {
                            val completedAt = chapter.completedTimestamp.takeIf { it > 0L }
                                ?: chapter.lastStudiedTimestamp
                            val historyJson = app.repository.getSetting("revision_history_json", "")
                            val history = if (historyJson.isBlank()) org.json.JSONArray() else org.json.JSONArray(historyJson)
                            val dueStage = revisionStages.firstOrNull { it.id == stage }
                            val alreadyRevised = (0 until history.length()).any { index ->
                                val record = history.getJSONObject(index)
                                record.optString("subjectName").equals(chapter.subjectName, ignoreCase = true) &&
                                    record.optString("chapterName").equals(chapter.chapterName, ignoreCase = true) &&
                                    record.optLong("startTime", record.optLong("timestamp", 0L)) >= completedAt &&
                                    record.optLong(
                                        "durationSeconds",
                                        record.optInt("durationMinutes", 0) * 60L
                                    ) >= 60L * 60L &&
                                    revisionStageId(record.optString("revisionStage")) == stage
                            }
                            if (alreadyRevised || dueStage == null ||
                                revisionDueAt(completedAt, dueStage) != dueAt ||
                                dueAt > System.currentTimeMillis()
                            ) return@launch
                            val title = StudyReminderScheduler.revisionTitle(stage) ?: "Revision Due"
                            val message = "সময় হয়েছে «${chapter.chapterName}» (${chapter.subjectName}) অধ্যায়টি অন্তত ১ ঘণ্টা রিভিশন করার।"
                            val id = "rev_${chapter.id}_$stage"
                            StudyReminderNotifications.post(
                                context,
                                id.hashCode(),
                                title,
                                message,
                                navigateTo = "REVISION_HUB"
                            )
                            app.repository.insertNotification(
                                NotificationEntity(
                                    id = id,
                                    type = NotificationType.REVISION_DUE.name,
                                    title = title,
                                    message = message,
                                    subjectName = chapter.subjectName,
                                    chapterName = chapter.chapterName,
                                    timestamp = dueAt
                                )
                            )
                        }
                    }

                    StudyReminderNotifications.ACTION_RECAP_ALARM -> {
                        val subjectName = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_SUBJECT_NAME) ?: "General"
                        val chapterName = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_CHAPTER_NAME) ?: "Chapter"
                        val title = "Revision Reminder"
                        val message = "«$chapterName» ($subjectName) এর রিভিশন করার নির্ধারিত সময় হয়েছে।"
                        val dueAt = validIntent.getLongExtra(
                            StudyReminderNotifications.EXTRA_DUE_AT,
                            System.currentTimeMillis()
                        )
                        val skippedAt = validIntent.getLongExtra(
                            StudyReminderNotifications.EXTRA_SKIP_AT,
                            dueAt
                        )
                        val historyJson = app.repository.getSetting("revision_history_json", "")
                        val history = if (historyJson.isBlank()) org.json.JSONArray() else org.json.JSONArray(historyJson)
                        val revisedSinceSkip = (0 until history.length()).any { index ->
                            val record = history.getJSONObject(index)
                            record.optString("subjectName").equals(subjectName, ignoreCase = true) &&
                                record.optString("chapterName").equals(chapterName, ignoreCase = true) &&
                                record.optLong("startTime", record.optLong("timestamp", 0L)) >= skippedAt &&
                                record.optLong(
                                    "durationSeconds",
                                    record.optInt("durationMinutes", 0) * 60L
                                ) >= 60L * 60L
                        }
                        if (revisedSinceSkip) return@launch
                        val id = "rev_recap_${subjectName.hashCode()}_${chapterName.hashCode()}_${dueAt}"

                        StudyReminderNotifications.post(
                            context,
                            id.hashCode(),
                            title,
                            message,
                            navigateTo = "REVISION_HUB"
                        )
                        app.repository.insertNotification(
                            NotificationEntity(
                                id = id,
                                type = NotificationType.REVISION_DUE.name,
                                title = title,
                                message = message,
                                subjectName = subjectName,
                                chapterName = chapterName,
                                timestamp = dueAt
                            )
                        )
                    }

                    StudyReminderNotifications.ACTION_DAILY_TARGET_7AM,
                    StudyReminderNotifications.ACTION_DAILY_TARGET_730AM,
                    StudyReminderNotifications.ACTION_MOTIVATION_7AM,
                    StudyReminderNotifications.ACTION_MOTIVATION_630PM,
                    StudyReminderNotifications.ACTION_MOTIVATION_1020PM -> {
                        StudyReminderScheduler.scheduleDailyAlarms(context)
                    }

                    StudyReminderNotifications.ACTION_FIRE_REMINDER -> {
                        val reminderId = StudyReminderNotifications.reminderId(validIntent)
                        if (reminderId > 0L) {
                            val reminder = app.database.reminderDao().getReminderById(reminderId)
                            if (reminder != null && reminder.enabled) {
                                StudyReminderNotifications.post(
                                    context = context,
                                    notificationId = StudyReminderNotifications.systemNotificationId(reminder.id),
                                    title = reminder.title,
                                    message = reminder.message
                                )
                                app.repository.insertNotification(
                                    NotificationEntity(
                                        id = "rem_${reminder.id}_${java.text.SimpleDateFormat("yyyy_MM_dd", Locale.US).format(Date())}",
                                        type = NotificationType.MOTIVATION.name,
                                        title = reminder.title,
                                        message = reminder.message,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                StudyReminderScheduler.schedule(context, reminder)
                            }
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e("StudyReminders", "Failed to deliver or reschedule alarm.", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FocentraApp
                val reminders = app.repository.allReminders.first()
                val chapters = app.repository.allChapters.first()
                StudyReminderScheduler.scheduleAll(context, reminders, chapters)
            } catch (exception: Exception) {
                Log.e("StudyReminders", "Failed to restore scheduled reminders.", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
