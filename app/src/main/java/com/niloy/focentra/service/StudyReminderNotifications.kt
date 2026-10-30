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
    const val ACTION_DAILY_TARGET_7AM = "com.niloy.focentra.action.DAILY_TARGET_7AM"
    const val ACTION_DAILY_TARGET_730AM = "com.niloy.focentra.action.DAILY_TARGET_730AM"
    const val ACTION_MOTIVATION_7AM = "com.niloy.focentra.action.MOTIVATION_7AM"
    const val ACTION_MOTIVATION_630PM = "com.niloy.focentra.action.MOTIVATION_630PM"
    const val ACTION_MOTIVATION_1020PM = "com.niloy.focentra.action.MOTIVATION_1020PM"
    const val ACTION_RECAP_ALARM = "com.niloy.focentra.action.RECAP_ALARM"

    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_SUBJECT_NAME = "subject_name"
    const val EXTRA_CHAPTER_NAME = "chapter_name"
    const val EXTRA_RECAP_STAGE = "recap_stage"

    val MOTIVATIONAL_TITLES = listOf(
        "তুই পারবি",
        "চালিয়ে যা",
        "ভাই কষ্ট কর",
        "তোকে পারতেই হবে",
        "হার মানলে চলবে না",
        "অপমানের প্রতিশোধ নিতে হবে",
        "তোকে সফল হতেই হবে",
        "আজকের পরিশ্রম কালকের জয়",
        "স্বপ্নগুলো সত্যি কর",
        "পড়াশোনায় মন দাও",
        "নিজের ভবিষ্যৎ গড়ো",
        "অলসতা দূর কর",
        "লক্ষ্য স্থির রাখো",
        "সফলতা তোমার অপেক্ষায়",
        "কঠিন পরিশ্রমই সেরা উত্তর",
        "নিজের ওপর বিশ্বাস রাখো",
        "ভয় পেয়ে থেমো না",
        "জয়ের গান গাইতে হবে",
        "নিজের যোগ্যতা প্রমাণ করো",
        "সময় নষ্ট করো না",
        "পড়াশোনাতেই মুক্তি",
        "চেষ্টা চালিয়ে যাও",
        "প্রতিটি সেকেন্ড মূল্যবান",
        "আরেকটু বাড়িয়ে দাও চেষ্টা",
        "পরাজয় মেনে নিও না",
        "কষ্টের ফল মিষ্টি হবেই",
        "তোমার মধ্যে অসীম শক্তি",
        "সাফল্য অর্জন করতেই হবে",
        "বইয়ের পাতায় চোখ রাখো",
        "ধৈর্য ধরো ও কাজ করো",
        "নিজেকে প্রস্তুত করো",
        "বিজয় হবে তোমারই",
        "আজকের দিনটি কাজে লাগাও",
        "একবিন্দু ছাড় নয়",
        "স্বপ্নের পেছনে দৌড়াও",
        "পরাজিত হওয়ার জন্য জন্ম হয়নি",
        "নিজের ইতিহাস তৈরি করো",
        "অধ্যবসায়ই আসল চাবিকাঠি",
        "আজকের ঘাম কালকের আনন্দ",
        "পড়ার টেবিলে ফিরে এসো",
        "নিজের পথ নিজেই গড়ো",
        "বাধা অতিক্রম করো",
        "লড়াই চালিয়ে যাও",
        "সফলতার শিখরে পৌঁছাতে হবে",
        "ভবিষ্যৎ তোমার হাতে",
        "এক মুহূর্তও অপচয় নয়",
        "বই হোক তোমার সঙ্গী",
        "প্রচেষ্টায় কোনো কমতি নেই",
        "সফলতা অর্জন বাধ্যতামূলক",
        "তুমিই বিজয়ী হবে",
        "মনোযোগ ধরে রাখো",
        "সেরা ফলাফল আনতেই হবে"
    )

    val MOTIVATIONAL_QUOTES = listOf(
        "\"তোমার অধিকার কেবল কর্মে, ফলের ওপর কখনও নয়। তাই ফলের আশায় ব্যাকুল না হয়ে তোমার অর্পিত কাজ (পড়াশোনা) নিষ্ঠার সাথে করে যাও।\" — শ্রীকৃষ্ণ",
        "\"স্বপ্ন সেটা নয় যা তুমি ঘুমিয়ে দেখো, স্বপ্ন সেটাই যা তোমাকে ঘুমাতে দেয় না। আজকের কঠোর পরিশ্রমই তোমার কালকের সুন্দর ভবিষ্যতের চাবিকাঠি।\" — ড. এ পি জে আব্দুল কালাম",
        "\"উঠো, জাগো এবং লক্ষ্য অর্জিত না হওয়া পর্যন্ত থেমো না। তোমার ভেতরের অসীম শক্তিকে বিশ্বাস করো; তুমি যা ভাববে, তাই হতে পারবে।\" — স্বামী বিবেকানন্দ",
        "\"শিক্ষা হলো সবচেয়ে শক্তিশালী অস্ত্র, যা তুমি পুরো বিশ্বকে বদলে দেওয়ার জন্য ব্যবহার করতে পারো।\" — নেলসন ম্যান্ডেলা",
        "\"যে ব্যক্তি নিজের মনকে জয় করেছে, তার মনই তার সবচেয়ে বড় বন্ধু। আর যে মনকে জয় করতে পারেনি, তার মনই তার সবচেয়ে বড় শত্রু। তাই পড়াশোনায় মনকে স্থির রাখাই প্রথম পদক্ষেপ।\" — শ্রীকৃষ্ণ",
        "\"যদি একটি গাছ কাটার জন্য আমাকে ৬ ঘণ্টা সময় দেওয়া হয়, তবে আমি প্রথম ৪ ঘণ্টা কুড়াল ধার দিতে ব্যয় করব। তেমনি পরীক্ষার আগে সঠিক পরিকল্পনা ও রুটিন বানিয়ে পড়াটাই আসল বুদ্ধিমত্তা।\" — আব্রাহাম লিংকন",
        "\"সাফল্যের গোপন সূত্র হলো নিরলস পরিশ্রম। যদি তুমি প্রতিদিন একটু একটু করে পড়াশোনায় সময় দাও, তবে অসম্ভবকেও সম্ভব করতে পারবে।\" — আলবার্ট আইনস্টাইন",
        "\"জ্ঞান হলো এমন এক সম্পদ, যা ব্যয় করলে বাড়ে কিন্তু চুরি করা যায় না। আজকের প্রতিটি কষ্টের মুহূর্ত কাল তোমাকে জ্ঞানের আলোয় প্রতিষ্ঠিত করবে।\" — হযরত আলী (রা.)",
        "\"সংশয়যুক্ত ব্যক্তি কখনো সিদ্ধি লাভ করতে পারে না। নিজের সামর্থ্যের ওপর কোনো সন্দেহ না রেখে পূর্ণ একাগ্রতা নিয়ে পড়াশোনায় ডুবে যাও।\" — শ্রীকৃষ্ণ",
        "\"সাফল্যের ১ শতাংশ আসে মেধা থেকে, আর ৯৯ শতাংশ আসে কঠোর পরিশ্রম থেকে।\" — থমাস এডিসন",
        "\"জ্ঞানের রত্ন সহজে পাওয়া যায় না। গভীর রাতে বা ভোরের আলোয় টেবিলের সামনে বসে যে ঘাম ঝরানো পরিশ্রম, সেটাই জীবনকে অর্থপূর্ণ করে।\" — রবীন্দ্রনাথ ঠাকুর",
        "\"তুমি কত ধীরে এগোচ্ছ তা বড় কথা নয়, বড় কথা হলো তুমি কখনো থেমে যাচ্ছ কিনা। প্রতিদিন কিছুটা হলেও পড়া শেষ করো।\" — কনফুসিয়াস",
        "\"অভ্যাস এবং বৈরাগ্যের মাধ্যমেই মনকে বশে আনা যায়। প্রথম দিকে পড়ার টেবিলে বসতে কষ্ট হলেও প্রতিদিন নিয়ম মেনে বসলে মন ঠিকই নিয়ন্ত্রণে আসবে।\" — শ্রীকৃষ্ণ",
        "\"আমি সেই মানুষকে ভয় পাই না যে ১০,০০০ কিক একবার প্র্যাকটিস করেছে; আমি তাকে ভয় পাই যে ১টি কিক ১০,০০০ বার অনুশীলন করেছে। বারবার রিভিশন ও প্র্যাকটিসই তোমার সেরা ফলাফল এনে দেবে।\" — ব্রুস লি",
        "\"তোমার কাজ তোমার জীবনের এক বিশাল অংশ জুড়ে থাকবে। আর সত্যি সন্তুষ্ট হওয়ার একমাত্র উপায় হলো দারুণ কিছু কাজ করা। আজকের পড়াশোনাই সেই ভিত্তি গড়ে দিচ্ছে।\" — স্টিভ জবস",
        "\"পরিশ্রম কখনো বৃথা যায় না। আজকে যে অধ্যায়গুলো শেষ করতে কষ্ট হচ্ছে, কাল সেগুলোই তোমার আত্মবিশ্বাসের মূল শক্তি হয়ে দাঁড়াবে।\" — মনীষী বাণী / প্রবাদ",
        "\"যে ব্যক্তি আসক্তি ত্যাগ করে নিজের কর্তব্য পালন করে, পাপ তাকে স্পর্শ করতে পারে না। ছাত্রজীবনের প্রধান কর্তব্য পড়ালেখা, তাই অন্য সব আকর্ষণ বাদ দিয়ে নিজের পড়ায় মনোযোগী হও।\" — শ্রীকৃষ্ণ",
        "\"বল বীর— বল উন্নত মম শির! শিক্ষা মানুষের মেরুদণ্ডকে সোজা করে দাঁড়াতে শেখায়। নিজের যোগ্যতায় সবার ওপরে ওঠার সংকল্প নাও।\" — কাজী নজরুল ইসলাম",
        "\"মানুষ মনে মনে যা চিন্তা করে, সে ঠিক তেমনই হয়ে ওঠে। তুমি যদি ভাবো তুমি কঠিন পড়াগুলো বুঝতে পারবে, তবে মন সেই পথ নিজেই তৈরি করে নেবে।\" — জেমস অ্যালেন",
        "\"আমরা হয়তো সব সময় বড় কাজ করতে পারি না, কিন্তু ছোট ছোট কাজগুলো বড় ভালোবাসার সাথে করতে পারি। প্রতিদিনের ছোট ছোট পড়া শেষ করার অভ্যাসই তোমাকে বিশাল সাফল্যের দিকে নিয়ে যাবে।\" — মাদার তেরেসা",
        "\"অর্জুন! দুর্বলতাকে মনে স্থান দিও না, এটি তোমার ব্যক্তিত্বের শোভা পায় না। হৃদয়ের সমস্ত ক্ষুদ্র দুর্বলতা ত্যাগ করে উঠে দাঁড়াও এবং নিজের সর্বোচ্চ দিয়ে পড়াশোনা করো।\" — শ্রীকৃষ্ণ",
        "\"যে পর্যন্ত না তোমার পড়াশোনা শেষ হচ্ছে, সে পর্যন্ত কোনো অজুহাত বা ক্লান্তি যেন তোমাকে থামাতে না পারে। আজকের পরিশ্রমই তোমার আগামীদিনের সম্মান।\" — ড. মুহম্মদ শহীদুল্লাহ্",
        "\"কঠোর পরিশ্রমের কোনো বিকল্প নেই। ভাগ্যের ওপর ভরসা করে বসে না থেকে নিজের কর্ম এবং পড়াশোনার জোরে ভাগ্যকে পরিবর্তন করো।\" — সর্দার বল্লভভাই প্যাটেল",
        "\"নিজের নির্ধারিত দায়িত্ব বা কর্তব্য নিষ্ঠার সাথে পালন করাটাই সবচেয়ে বড় ইবাদত বা ধর্ম। ছাত্রাবস্থায় তোমার একমাত্র ধর্ম হলো বইয়ের পাতায় মনোযোগ দেওয়া।\" — শ্রীকৃষ্ণ",
        "\"কাজের পরিমাণ যতই বেশি হোক না কেন, যদি তোমার মনের জোর থাকে, তবে তুমি সব সিলেবাস শেষ করতে পারবে। কেবল প্রতিদিন নিজের লক্ষ্য ঠিক রাখো।\" — মাদার তেরেসা",
        "\"জীবনের টিকে থাকার লড়াইয়ে সেই সফল হয়, যে পরিস্থিতির সাথে মানিয়ে নিয়ে সবচেয়ে বেশি পরিশ্রম করতে পারে। পড়াশোনার এই কঠিন সময়ে হাল ছেড়ো না।\" — চার্লস ডারউইন",
        "\"কাজের অলসতা মানুষকে ব্যর্থতার দিকে নিয়ে যায়। আজকের পড়া কালকের জন্য জমিয়ে রেখো না, কারণ সময় কারো জন্য অপেক্ষা করে না।\" — হযরত ওমর (রা.)",
        "\"যে ব্যক্তি নিজের ইন্দ্রিয়গুলোকে বশে আনতে পেরেছে এবং পড়াশোনায় মনকে স্থির করেছে, জ্ঞান আপনাআপনি তার কাছে ধরা দেয়।\" — শ্রীকৃষ্ণ",
        "\"অসম্ভব শব্দটি কেবল বোকাদের অভিধানেই পাওয়া যায়। তুমি যদি দৃঢ়প্রতিজ্ঞ হও, তবে যেকোনো কঠিন বিষয় বা অঙ্ক তোমার কাছে সহজ হয়ে যাবে।\" — নেপোলিয়ন বোনাপার্ট",
        "\"যদি সূর্যর মতো উজ্জ্বল হতে চাও, তবে প্রথমে সূর্যের মতো পোড়াও—অর্থাৎ কঠোর পরিশ্রমের আগুনে নিজেকে প্রস্তুত করো।\" — ড. এ পি জে আব্দুল কালাম",
        "\"আজকে তুমি যে সময়টুকু পড়ার টেবিলে নষ্ট না করে কাজে লাগাবে, কাল তার সুফল হাজার গুণ বেশি হয়ে ফিরে আসবে। সময় বাঁচাও, পরিশ্রম করো।\" — বেঞ্জামিন ফ্রাঙ্কলিন",
        "\"যিনি সকল কাজ ঈশ্বরের চরণে সঁপে দিয়ে বা নিজের দায়িত্ব মনে করে ফলের আকাঙ্ক্ষা ছাড়াই পরিশ্রম করেন, তাকে কোনো দুঃখ স্পর্শ করতে পারে না।\" — শ্রীকৃষ্ণ",
        "\"ব্রহ্মচর্য ও একাগ্রতাই হলো সাফল্যের মূল চাবিকাঠি। নিজের মনকে বিক্ষিপ্ত হতে দিও না, সব শক্তি পড়াশোনায় নিয়োজিত করো।\" — স্বামী বিবেকানন্দ",
        "\"যারা ইতিহাস সৃষ্টি করতে চায়, তারা অলসতার বিছানায় ঘুমিয়ে থাকে না; তারা রাতের পর রাত টেবিলের সামনে জেগে থেকে নিজের ভবিষ্যৎ লেখে।\" — লর্ড বায়রন",
        "\"আলাপ করে সময় নষ্ট না করে নিজের খাতায় কলম চালাও। এই বিশ্বকে শিশুর বাসযোগ্য করে তুলতে হলে আগে নিজেকে একজন যোগ্য মানুষ হিসেবে গড়ে তোলো।\" — সুকান্ত ভট্টাচার্য",
        "\"মন চঞ্চল এবং অস্থির, তাকে সহজে নিয়ন্ত্রণ করা কঠিন। কিন্তু নিয়মিত অভ্যাস ও বৈরাগ্যের মাধ্যমে তাকে অবশ্যই বশে আনা সম্ভব।\" — শ্রীকৃষ্ণ",
        "\"যখন মনে হবে সবকিছু তোমার বিরুদ্ধে চলে যাচ্ছে, তখন মনে রেখো উড়োজাহাজ বাতাসের প্রতিকূলেই ওড়ে, অনুকূলে নয়। কঠিন পড়াশোনাই তোমাকে উপরে তুলতে সাহায্য করবে।\" — হেনরি ফোর্ড",
        "\"আপনারে লয়ে বিব্রত রহিতে আসে নাই কেহ অবনী পরে, সকলের তরে সকলে আমরা, প্রত্যেকে আমরা পরের তরে—নিজের পড়াশোনা ও জ্ঞান অর্জন দিয়ে দেশ ও সমাজের উপকারে ব্রতী হও।\" — রবীন্দ্রনাথ ঠাকুর",
        "\"তুমি কাল কী হবে, তা নির্ভর করছে আজ তুমি তোমার পড়াশোনায় কী করছ তার ওপর। অলসতা বাদ দিয়ে আজ থেকেই নিজের সেরাটা দিয়ে চেষ্টা করো।\" — জন সি. ম্যাক্সওয়েল",
        "\"যদি তুমি তোমার কাজকে সালাম করো, তবে তোমাকে কাউকে সালাম করতে হবে না।\" — ড. এ পি জে আব্দুল কালাম",
        "\"তোমার সময় সীমিত, তাই অন্য কারো জীবন বেঁচে তা নষ্ট করো না।\" — স্টিভ জবস",
        "\"আমরা যা বারবার করি, আমরা সেটাই হয়ে উঠি। তাই শ্রেষ্ঠত্ব কোনো কাজ নয়, এটি একটি অভ্যাস।\" — অ্যারিস্টটল",
        "\"নিজেকে জানো। জ্ঞান অর্জনের প্রথম ধাপ হলো নিজের অজ্ঞতা স্বীকার করা।\" — সক্রেটিস",
        "\"প্রশিক্ষণের প্রতিটা মুহূর্ত আমি অপছন্দ করেছি, কিন্তু নিজেকে বলেছি—থেমো না, এখন কষ্ট করো আর বাকি জীবন বিজয়ী হয়ে বাঁচো।\" — মহাম্মদ আলী",
        "\"শুরুটাই হলো কাজের সবচেয়ে গুরুত্বপূর্ণ অংশ।\" — প্লেটো",
        "\"যে ব্যক্তি নিজের ভুল সংশোধন করে না, সে আরেকটি ভুল করে।\" — কনফুসিয়াস",
        "\"এমনভাবে শেখো যেন তুমি চিরকাল বাঁচবে, এমনভাবে বাঁচো যেন তুমি কালই মারা যাবে।\" — মহাত্মা গান্ধী",
        "\"কল্পনাশক্তির চেয়ে জ্ঞান বড় নয়, তবে কল্পনা বিশ্বকে পরিভ্রমণ করে।\" — আলবার্ট আইনস্টাইন",
        "\"আমি চিরদূর্দম, দুর্বিনীত, নৃশংস, প্রলয়ের আমি নটরাজ, আমি সাইক্লোন, আমি ধ্বংস!\" — কাজী নজরুল ইসলাম",
        "\"আলো বলে, 'অন্ধকার, তুই বড়ো কালো।' অন্ধকার বলে, 'ভাই, তাই তুমি আলো।'\" — রবীন্দ্রনাথ ঠাকুর",
        "\"যে শক্তি তোমাকে ভেতর থেকে স্বাধীন করে না, তা আসল শিক্ষা নয়।\" — স্বামী বিবেকানন্দ",
        "\"স্বপ্নের পেছনে ধাওয়া করো, বাধাগুলো আপনাতেই পথ ছেড়ে দেবে।\" — ড. মুহাম্মদ ইউনুস",
        "\"যদি আপনি সফল হতে চান, তবে ব্যর্থতার হার দ্বিগুণ করে দিন।\" — ডেল কার্নেগী",
        "\"যদি তুমি দরিদ্র হয়ে জন্মাও তবে তা তোমার দোষ নয়, কিন্তু তুমি যদি দরিদ্র হয়ে মারা যাও তবে তা তোমার দোষ।\" — বিল গেটস",
        "\"চিন্তা করো সতর্কভাবে, কারণ চিন্তা থেকে কথা তৈরি হয়, কথা থেকে কাজ, কাজ থেকে চরিত্র।\" — মার্গারেট থ্যাচার",
        "\"হাসি ছাড়া কাটানো একটি দিন মানে অপচয় হওয়া একটি দিন।\" — চার্লি চ্যাপলিন",
        "\"আত্মার বিনাশ নেই, কর্মের পথেই আত্মার উন্নয়ন ঘটে।\" — শ্রীকৃষ্ণ",
        "\"সত্য কথা বলা আমানত, আর মিথ্যা বলা খিয়ানত।\" — হযরত আবু বকর (রা.)",
        "\"নিজের হিসাব নেওয়ার আগে নিজেই নিজের হিসাব নাও।\" — হযরত ওমর (রা.)"
    )

    fun scheduleRecapReminders(
        context: Context,
        subjectName: String,
        chapterName: String,
        recapTimestamp: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1 Hour Before Alarm
        val oneHourBeforeMs = recapTimestamp - 3600000L
        if (oneHourBeforeMs > System.currentTimeMillis()) {
            val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                action = ACTION_RECAP_ALARM
                data = Uri.parse("focentra://recap/1h/$recapTimestamp")
                putExtra(EXTRA_SUBJECT_NAME, subjectName)
                putExtra(EXTRA_CHAPTER_NAME, chapterName)
                putExtra(EXTRA_RECAP_STAGE, "1h")
            }
            val pi = PendingIntent.getBroadcast(
                context,
                (recapTimestamp xor 0x1111).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, oneHourBeforeMs, pi)
        }

        // 30 Minutes Before Alarm
        val thirtyMinsBeforeMs = recapTimestamp - 1800000L
        if (thirtyMinsBeforeMs > System.currentTimeMillis()) {
            val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                action = ACTION_RECAP_ALARM
                data = Uri.parse("focentra://recap/30m/$recapTimestamp")
                putExtra(EXTRA_SUBJECT_NAME, subjectName)
                putExtra(EXTRA_CHAPTER_NAME, chapterName)
                putExtra(EXTRA_RECAP_STAGE, "30m")
            }
            val pi = PendingIntent.getBroadcast(
                context,
                (recapTimestamp xor 0x3333).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, thirtyMinsBeforeMs, pi)
        }
    }

    fun scheduleEbbinghausRevisions(context: Context, subjectName: String, chapterName: String, completionTimestamp: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intervals = listOf(
            Pair("1d", 1 * 24 * 3600 * 1000L),
            Pair("3d", 3 * 24 * 3600 * 1000L),
            Pair("7d", 7 * 24 * 3600 * 1000L),
            Pair("15d", 15 * 24 * 3600 * 1000L),
            Pair("30d", 30 * 24 * 3600 * 1000L)
        )

        intervals.forEachIndexed { index, (stage, offsetMs) ->
            val triggerTime = completionTimestamp + offsetMs
            if (triggerTime > System.currentTimeMillis()) {
                val intent = Intent(context, StudyReminderReceiver::class.java).apply {
                    action = ACTION_RECAP_ALARM
                    data = Uri.parse("focentra://ebbinghaus/$stage/$completionTimestamp")
                    putExtra(EXTRA_SUBJECT_NAME, subjectName)
                    putExtra(EXTRA_CHAPTER_NAME, chapterName)
                    putExtra(EXTRA_RECAP_STAGE, stage)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    (completionTimestamp xor (offsetMs + index)).toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pi)
            }
        }
    }

    fun post(context: Context, notificationId: Int, title: String, message: String): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "NOTIFICATIONS")
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
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
            ACTION_DAILY_TARGET_7AM,
            ACTION_DAILY_TARGET_730AM,
            ACTION_MOTIVATION_7AM,
            ACTION_MOTIVATION_630PM,
            ACTION_MOTIVATION_1020PM,
            ACTION_RECAP_ALARM
        )

    internal fun systemNotificationId(reminderId: Long): Int =
        0x40000000 or (reminderId.toInt() and 0x3FFFFFFF)
}

object StudyReminderScheduler {
    fun scheduleAll(context: Context, reminders: List<ReminderEntity>) {
        reminders.forEach { schedule(context, it) }
        scheduleDailyAlarms(context)
    }

    fun scheduleDailyAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Daily Target 7:00 AM Every Day
        scheduleRecurringAlarm(context, alarmManager, 7, 0, StudyReminderNotifications.ACTION_DAILY_TARGET_7AM, 1001)

        // Daily Target 7:30 AM Every Day Except Monday
        scheduleRecurringAlarm(context, alarmManager, 7, 30, StudyReminderNotifications.ACTION_DAILY_TARGET_730AM, 1002, excludeMonday = true)

        // Motivational 7:00 AM
        scheduleRecurringAlarm(context, alarmManager, 7, 0, StudyReminderNotifications.ACTION_MOTIVATION_7AM, 1003)

        // Motivational 6:30 PM (18:30)
        scheduleRecurringAlarm(context, alarmManager, 18, 30, StudyReminderNotifications.ACTION_MOTIVATION_630PM, 1004)

        // Motivational 10:20 PM (22:20)
        scheduleRecurringAlarm(context, alarmManager, 22, 20, StudyReminderNotifications.ACTION_MOTIVATION_1020PM, 1005)
    }

    private fun scheduleRecurringAlarm(
        context: Context,
        alarmManager: AlarmManager,
        hour: Int,
        minute: Int,
        action: String,
        requestCode: Int,
        excludeMonday: Boolean = false
    ) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            if (excludeMonday && get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, StudyReminderReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("focentra://recurring/$requestCode")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            target.timeInMillis,
            pendingIntent
        )
    }

    fun schedule(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, reminder.id)
        val triggerAtMillis = nextReminderTimeMillis(reminder, System.currentTimeMillis())
        if (!reminder.enabled || triggerAtMillis == null) {
            alarmManager.cancel(pendingIntent)
            return
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
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
                    StudyReminderNotifications.ACTION_DAILY_TARGET_7AM,
                    StudyReminderNotifications.ACTION_DAILY_TARGET_730AM -> {
                        val title = "Daily Study Target"
                        val message = "আজকের স্টাডি টার্গেট পূরণ করার জন্য প্রস্তুত হও! প্রতিদিনের ছোট ছোট পদক্ষেপই বড় সফলতা এনে দেবে।"
                        val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

                        StudyReminderNotifications.post(context, notifId, title, message)
                        app.repository.insertNotification(
                            NotificationEntity(
                                id = "dt_${System.currentTimeMillis()}",
                                type = NotificationType.DAILY_TARGET.name,
                                title = title,
                                message = message,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        StudyReminderScheduler.scheduleDailyAlarms(context)
                    }

                    StudyReminderNotifications.ACTION_MOTIVATION_7AM,
                    StudyReminderNotifications.ACTION_MOTIVATION_630PM,
                    StudyReminderNotifications.ACTION_MOTIVATION_1020PM -> {
                        val random = Random()
                        val title = StudyReminderNotifications.MOTIVATIONAL_TITLES[random.nextInt(StudyReminderNotifications.MOTIVATIONAL_TITLES.size)]
                        val quote = StudyReminderNotifications.MOTIVATIONAL_QUOTES[random.nextInt(StudyReminderNotifications.MOTIVATIONAL_QUOTES.size)]
                        val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

                        StudyReminderNotifications.post(context, notifId, title, quote)
                        app.repository.insertNotification(
                            NotificationEntity(
                                id = "mot_${System.currentTimeMillis()}",
                                type = NotificationType.MOTIVATION.name,
                                title = title,
                                message = quote,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        StudyReminderScheduler.scheduleDailyAlarms(context)
                    }

                    StudyReminderNotifications.ACTION_RECAP_ALARM -> {
                        val subjectName = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_SUBJECT_NAME) ?: "General"
                        val chapterName = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_CHAPTER_NAME) ?: "Chapter"
                        val stage = validIntent.getStringExtra(StudyReminderNotifications.EXTRA_RECAP_STAGE) ?: "1h"
                        val timeText = if (stage == "1h") "১ ঘণ্টা" else "৩০ মিনিট"
                        val title = "রিভিশন রিক্যাপ রিমাইন্ডার ($timeText বাকি)"
                        val message = "«$chapterName» ($subjectName) এর রিভিশন রিক্যাপ করার সময় হয়েছে। আপনি পূর্বে এই রিভিশনে গ্যাপ দিয়েছিলেন।"
                        val notifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()

                        StudyReminderNotifications.post(context, notifId, title, message)
                        app.repository.insertNotification(
                            NotificationEntity(
                                id = "recap_alarm_${System.currentTimeMillis()}",
                                type = NotificationType.RECAP_REMINDER.name,
                                title = title,
                                message = message,
                                subjectName = subjectName,
                                chapterName = chapterName,
                                timestamp = System.currentTimeMillis()
                            )
                        )
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
                                        id = "rem_${reminder.id}_${System.currentTimeMillis()}",
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
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FocentraApp
                val reminders = app.repository.allReminders.first()
                StudyReminderScheduler.scheduleAll(context, reminders)
            } catch (exception: Exception) {
                Log.e("StudyReminders", "Failed to restore scheduled reminders.", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
