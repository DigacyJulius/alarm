@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.shiftalarm.app.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiftalarm.app.core.L10n
import com.shiftalarm.app.core.TimerReceiver
import com.shiftalarm.app.core.t
import com.shiftalarm.app.data.AppData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A world-clock location: English name, Chinese name, IANA zone id. */
data class WorldCity(val en: String, val zh: String, val zone: String)

/** Every country (plus major extra cities), sorted by English name. */
val WORLD_CITIES: List<WorldCity> = listOf(
    WorldCity("Afghanistan (Kabul)", "阿富汗 喀布爾", "Asia/Kabul"),
    WorldCity("Albania (Tirana)", "阿爾巴尼亞 地拉那", "Europe/Tirane"),
    WorldCity("Algeria (Algiers)", "阿爾及利亞 阿爾及爾", "Africa/Algiers"),
    WorldCity("Andorra", "安道爾", "Europe/Andorra"),
    WorldCity("Angola (Luanda)", "安哥拉 羅安達", "Africa/Luanda"),
    WorldCity("Antigua & Barbuda", "安提瓜和巴布達", "America/Antigua"),
    WorldCity("Argentina (Buenos Aires)", "阿根廷 布宜諾斯艾利斯", "America/Argentina/Buenos_Aires"),
    WorldCity("Armenia (Yerevan)", "亞美尼亞 埃里溫", "Asia/Yerevan"),
    WorldCity("Australia: Sydney", "澳洲 悉尼", "Australia/Sydney"),
    WorldCity("Australia: Melbourne", "澳洲 墨爾本", "Australia/Melbourne"),
    WorldCity("Australia: Brisbane", "澳洲 布里斯班", "Australia/Brisbane"),
    WorldCity("Australia: Perth", "澳洲 珀斯", "Australia/Perth"),
    WorldCity("Australia: Adelaide", "澳洲 阿德萊德", "Australia/Adelaide"),
    WorldCity("Australia: Darwin", "澳洲 達爾文", "Australia/Darwin"),
    WorldCity("Austria (Vienna)", "奧地利 維也納", "Europe/Vienna"),
    WorldCity("Azerbaijan (Baku)", "阿塞拜疆 巴庫", "Asia/Baku"),
    WorldCity("Bahamas (Nassau)", "巴哈馬 拿騷", "America/Nassau"),
    WorldCity("Bahrain (Manama)", "巴林 麥納麥", "Asia/Bahrain"),
    WorldCity("Bangladesh (Dhaka)", "孟加拉 達卡", "Asia/Dhaka"),
    WorldCity("Barbados (Bridgetown)", "巴貝多 布里奇敦", "America/Barbados"),
    WorldCity("Belarus (Minsk)", "白俄羅斯 明斯克", "Europe/Minsk"),
    WorldCity("Belgium (Brussels)", "比利時 布魯塞爾", "Europe/Brussels"),
    WorldCity("Belize", "伯利茲", "America/Belize"),
    WorldCity("Benin (Porto-Novo)", "貝寧", "Africa/Porto-Novo"),
    WorldCity("Bhutan (Thimphu)", "不丹 廷布", "Asia/Thimphu"),
    WorldCity("Bolivia (La Paz)", "玻利維亞 拉巴斯", "America/La_Paz"),
    WorldCity("Bosnia & Herzegovina (Sarajevo)", "波黑 薩拉熱窩", "Europe/Sarajevo"),
    WorldCity("Botswana (Gaborone)", "博茨瓦納 哈博羅內", "Africa/Gaborone"),
    WorldCity("Brazil (São Paulo)", "巴西 聖保羅", "America/Sao_Paulo"),
    WorldCity("Brazil (Manaus)", "巴西 馬瑙斯", "America/Manaus"),
    WorldCity("Brunei", "文萊", "Asia/Brunei"),
    WorldCity("Bulgaria (Sofia)", "保加利亞 索非亞", "Europe/Sofia"),
    WorldCity("Burkina Faso (Ouagadougou)", "布基納法索 瓦加杜古", "Africa/Ouagadougou"),
    WorldCity("Burundi (Gitega)", "布隆迪 基特加", "Africa/Bujumbura"),
    WorldCity("Cambodia (Phnom Penh)", "柬埔寨 金邊", "Asia/Phnom_Penh"),
    WorldCity("Cameroon (Yaoundé)", "喀麥隆 雅溫得", "Africa/Douala"),
    WorldCity("Canada: Toronto", "加拿大 多倫多", "America/Toronto"),
    WorldCity("Canada: Vancouver", "加拿大 溫哥華", "America/Vancouver"),
    WorldCity("Canada: Edmonton", "加拿大 埃德蒙頓", "America/Edmonton"),
    WorldCity("Canada: Winnipeg", "加拿大 溫尼伯", "America/Winnipeg"),
    WorldCity("Canada: Halifax", "加拿大 哈利法克斯", "America/Halifax"),
    WorldCity("Cabo Verde (Praia)", "佛得角 普拉亞", "Atlantic/Cape_Verde"),
    WorldCity("Central African Rep. (Bangui)", "中非共和國 班吉", "Africa/Bangui"),
    WorldCity("Chad (N'Djamena)", "乍得 恩賈梅納", "Africa/Ndjamena"),
    WorldCity("Chile (Santiago)", "智利 聖地亞哥", "America/Santiago"),
    WorldCity("China: Beijing / Shanghai", "中國 北京／上海", "Asia/Shanghai"),
    WorldCity("China: Hong Kong", "中國 香港", "Asia/Hong_Kong"),
    WorldCity("China: Macau", "中國 澳門", "Asia/Macau"),
    WorldCity("China: Urumqi", "中國 烏魯木齊", "Asia/Urumqi"),
    WorldCity("Colombia (Bogotá)", "哥倫比亞 波哥大", "America/Bogota"),
    WorldCity("Comoros (Moroni)", "科摩羅 莫羅尼", "Indian/Comoro"),
    WorldCity("Congo (Brazzaville)", "剛果（布） 布拉柴維爾", "Africa/Brazzaville"),
    WorldCity("Congo DR (Kinshasa)", "剛果（金） 金沙薩", "Africa/Kinshasa"),
    WorldCity("Congo DR (Lubumbashi)", "剛果（金） 盧本巴希", "Africa/Lubumbashi"),
    WorldCity("Costa Rica (San José)", "哥斯達黎加 聖何塞", "America/Costa_Rica"),
    WorldCity("Croatia (Zagreb)", "克羅地亞 薩格勒布", "Europe/Zagreb"),
    WorldCity("Cuba (Havana)", "古巴 哈瓦那", "America/Havana"),
    WorldCity("Cyprus (Nicosia)", "塞浦路斯 尼科西亞", "Asia/Nicosia"),
    WorldCity("Czechia (Prague)", "捷克 布拉格", "Europe/Prague"),
    WorldCity("Denmark (Copenhagen)", "丹麥 哥本哈根", "Europe/Copenhagen"),
    WorldCity("Djibouti", "吉布提", "Africa/Djibouti"),
    WorldCity("Dominica (Roseau)", "多米尼克 羅索", "America/Dominica"),
    WorldCity("Dominican Rep. (Santo Domingo)", "多米尼加 聖多明各", "America/Santo_Domingo"),
    WorldCity("Ecuador (Quito)", "厄瓜多爾 基多", "America/Guayaquil"),
    WorldCity("Egypt (Cairo)", "埃及 開羅", "Africa/Cairo"),
    WorldCity("El Salvador", "薩爾瓦多", "America/El_Salvador"),
    WorldCity("Equatorial Guinea (Malabo)", "赤道幾內亞 馬拉博", "Africa/Malabo"),
    WorldCity("Eritrea (Asmara)", "厄立特里亞 阿斯馬拉", "Africa/Asmara"),
    WorldCity("Estonia (Tallinn)", "愛沙尼亞 塔林", "Europe/Tallinn"),
    WorldCity("Eswatini (Mbabane)", "斯威士蘭 姆巴巴內", "Africa/Mbabane"),
    WorldCity("Ethiopia (Addis Ababa)", "埃塞俄比亞 亞的斯亞貝巴", "Africa/Addis_Ababa"),
    WorldCity("Fiji (Suva)", "斐濟 蘇瓦", "Pacific/Fiji"),
    WorldCity("Finland (Helsinki)", "芬蘭 赫爾辛基", "Europe/Helsinki"),
    WorldCity("France (Paris)", "法國 巴黎", "Europe/Paris"),
    WorldCity("Gabon (Libreville)", "加蓬 利伯維爾", "Africa/Libreville"),
    WorldCity("Gambia (Banjul)", "岡比亞 班珠爾", "Africa/Banjul"),
    WorldCity("Georgia (Tbilisi)", "格魯吉亞 第比利斯", "Asia/Tbilisi"),
    WorldCity("Germany (Berlin)", "德國 柏林", "Europe/Berlin"),
    WorldCity("Germany (Frankfurt)", "德國 法蘭克福", "Europe/Berlin"),
    WorldCity("Ghana (Accra)", "加納 阿克拉", "Africa/Accra"),
    WorldCity("Greece (Athens)", "希臘 雅典", "Europe/Athens"),
    WorldCity("Grenada (St George's)", "格林納達 聖喬治", "America/Grenada"),
    WorldCity("Guatemala City", "危地馬拉", "America/Guatemala"),
    WorldCity("Guinea (Conakry)", "幾內亞 科納克里", "Africa/Conakry"),
    WorldCity("Guinea-Bissau", "幾內亞比紹", "Africa/Bissau"),
    WorldCity("Guyana (Georgetown)", "圭亞那 喬治敦", "America/Guyana"),
    WorldCity("Haiti (Port-au-Prince)", "海地 太子港", "America/Port-au-Prince"),
    WorldCity("Honduras (Tegucigalpa)", "洪都拉斯 特古西加爾巴", "America/Tegucigalpa"),
    WorldCity("Hungary (Budapest)", "匈牙利 布達佩斯", "Europe/Budapest"),
    WorldCity("Iceland (Reykjavik)", "冰島 雷克雅未克", "Atlantic/Reykjavik"),
    WorldCity("India: New Delhi", "印度 新德里", "Asia/Kolkata"),
    WorldCity("India: Mumbai", "印度 孟買", "Asia/Kolkata"),
    WorldCity("Indonesia: Jakarta", "印尼 雅加達", "Asia/Jakarta"),
    WorldCity("Indonesia: Bali (Denpasar)", "印尼 峇里島", "Asia/Makassar"),
    WorldCity("Indonesia: Jayapura", "印尼 查亞普拉", "Asia/Jayapura"),
    WorldCity("Iran (Tehran)", "伊朗 德黑蘭", "Asia/Tehran"),
    WorldCity("Iraq (Baghdad)", "伊拉克 巴格達", "Asia/Baghdad"),
    WorldCity("Ireland (Dublin)", "愛爾蘭 都柏林", "Europe/Dublin"),
    WorldCity("Israel (Jerusalem)", "以色列 耶路撒冷", "Asia/Jerusalem"),
    WorldCity("Italy (Rome)", "意大利 羅馬", "Europe/Rome"),
    WorldCity("Italy (Milan)", "意大利 米蘭", "Europe/Rome"),
    WorldCity("Ivory Coast (Yamoussoukro)", "科特迪瓦 亞穆蘇克羅", "Africa/Abidjan"),
    WorldCity("Jamaica (Kingston)", "牙買加 金斯敦", "America/Jamaica"),
    WorldCity("Japan (Tokyo)", "日本 東京", "Asia/Tokyo"),
    WorldCity("Japan (Osaka)", "日本 大阪", "Asia/Tokyo"),
    WorldCity("Jordan (Amman)", "約旦 安曼", "Asia/Amman"),
    WorldCity("Kazakhstan (Almaty)", "哈薩克 阿拉木圖", "Asia/Almaty"),
    WorldCity("Kenya (Nairobi)", "肯尼亞 內羅畢", "Africa/Nairobi"),
    WorldCity("Kiribati (Tarawa)", "基里巴斯 塔拉瓦", "Pacific/Tarawa"),
    WorldCity("Kuwait", "科威特", "Asia/Kuwait"),
    WorldCity("Kyrgyzstan (Bishkek)", "吉爾吉斯斯坦 比什凱克", "Asia/Bishkek"),
    WorldCity("Laos (Vientiane)", "老撾 萬象", "Asia/Vientiane"),
    WorldCity("Latvia (Riga)", "拉脫維亞 里加", "Europe/Riga"),
    WorldCity("Lebanon (Beirut)", "黎巴嫩 貝魯特", "Asia/Beirut"),
    WorldCity("Lesotho (Maseru)", "萊索托 馬塞盧", "Africa/Maseru"),
    WorldCity("Liberia (Monrovia)", "利比里亞 蒙羅維亞", "Africa/Monrovia"),
    WorldCity("Libya (Tripoli)", "利比亞 的黎波里", "Africa/Tripoli"),
    WorldCity("Liechtenstein (Vaduz)", "列支敦士登 瓦杜茲", "Europe/Vaduz"),
    WorldCity("Lithuania (Vilnius)", "立陶宛 維爾紐斯", "Europe/Vilnius"),
    WorldCity("Luxembourg", "盧森堡", "Europe/Luxembourg"),
    WorldCity("Madagascar (Antananarivo)", "馬達加斯加 塔那那利佛", "Indian/Antananarivo"),
    WorldCity("Malawi (Lilongwe)", "馬拉維 利隆圭", "Africa/Blantyre"),
    WorldCity("Malaysia (Kuala Lumpur)", "馬來西亞 吉隆坡", "Asia/Kuala_Lumpur"),
    WorldCity("Maldives (Malé)", "馬爾代夫 馬累", "Indian/Maldives"),
    WorldCity("Mali (Bamako)", "馬里 巴馬科", "Africa/Bamako"),
    WorldCity("Malta (Valletta)", "馬耳他 瓦萊塔", "Europe/Malta"),
    WorldCity("Marshall Islands (Majuro)", "馬紹爾群島 馬朱羅", "Pacific/Majuro"),
    WorldCity("Mauritania (Nouakchott)", "毛里塔尼亞 努瓦克肖特", "Africa/Nouakchott"),
    WorldCity("Mauritius (Port Louis)", "毛里求斯 路易港", "Indian/Mauritius"),
    WorldCity("Mexico: Mexico City", "墨西哥 墨西哥城", "America/Mexico_City"),
    WorldCity("Mexico: Tijuana", "墨西哥 蒂華納", "America/Tijuana"),
    WorldCity("Micronesia (Palikir)", "密克羅尼西亞 帕利基爾", "Pacific/Pohnpei"),
    WorldCity("Moldova (Chisinau)", "摩爾多瓦 基希訥烏", "Europe/Chisinau"),
    WorldCity("Monaco", "摩納哥", "Europe/Monaco"),
    WorldCity("Mongolia (Ulaanbaatar)", "蒙古 烏蘭巴托", "Asia/Ulaanbaatar"),
    WorldCity("Montenegro (Podgorica)", "黑山 波德戈里察", "Europe/Podgorica"),
    WorldCity("Morocco (Rabat)", "摩洛哥 拉巴特", "Africa/Casablanca"),
    WorldCity("Mozambique (Maputo)", "莫桑比克 馬普托", "Africa/Maputo"),
    WorldCity("Myanmar (Yangon)", "緬甸 仰光", "Asia/Yangon"),
    WorldCity("Namibia (Windhoek)", "納米比亞 溫得和克", "Africa/Windhoek"),
    WorldCity("Nauru", "瑙魯", "Pacific/Nauru"),
    WorldCity("Nepal (Kathmandu)", "尼泊爾 加德滿都", "Asia/Kathmandu"),
    WorldCity("Netherlands (Amsterdam)", "荷蘭 阿姆斯特丹", "Europe/Amsterdam"),
    WorldCity("New Zealand (Auckland)", "新西蘭 奧克蘭", "Pacific/Auckland"),
    WorldCity("Nicaragua (Managua)", "尼加拉瓜 馬那瓜", "America/Managua"),
    WorldCity("Niger (Niamey)", "尼日爾 尼亞美", "Africa/Niamey"),
    WorldCity("Nigeria (Lagos)", "尼日利亞 拉各斯", "Africa/Lagos"),
    WorldCity("North Korea (Pyongyang)", "朝鮮 平壤", "Asia/Pyongyang"),
    WorldCity("North Macedonia (Skopje)", "北馬其頓 斯科普里", "Europe/Skopje"),
    WorldCity("Norway (Oslo)", "挪威 奧斯陸", "Europe/Oslo"),
    WorldCity("Oman (Muscat)", "阿曼 馬斯喀特", "Asia/Muscat"),
    WorldCity("Pakistan (Karachi)", "巴基斯坦 卡拉奇", "Asia/Karachi"),
    WorldCity("Palau", "帕勞", "Pacific/Palau"),
    WorldCity("Palestine (Ramallah)", "巴勒斯坦 拉姆安拉", "Asia/Hebron"),
    WorldCity("Panama City", "巴拿馬城", "America/Panama"),
    WorldCity("Papua New Guinea (Port Moresby)", "巴布亞新幾內亞 莫爾茲比港", "Pacific/Port_Moresby"),
    WorldCity("Paraguay (Asunción)", "巴拉圭 亞松森", "America/Asuncion"),
    WorldCity("Peru (Lima)", "秘魯 利馬", "America/Lima"),
    WorldCity("Philippines (Manila)", "菲律賓 馬尼拉", "Asia/Manila"),
    WorldCity("Poland (Warsaw)", "波蘭 華沙", "Europe/Warsaw"),
    WorldCity("Portugal (Lisbon)", "葡萄牙 里斯本", "Europe/Lisbon"),
    WorldCity("Qatar (Doha)", "卡塔爾 多哈", "Asia/Qatar"),
    WorldCity("Romania (Bucharest)", "羅馬尼亞 布加勒斯特", "Europe/Bucharest"),
    WorldCity("Russia: Moscow", "俄羅斯 莫斯科", "Europe/Moscow"),
    WorldCity("Russia: Kaliningrad", "俄羅斯 加里寧格勒", "Europe/Kaliningrad"),
    WorldCity("Russia: Novosibirsk", "俄羅斯 新西伯利亞", "Asia/Novosibirsk"),
    WorldCity("Russia: Vladivostok", "俄羅斯 海參崴", "Asia/Vladivostok"),
    WorldCity("Rwanda (Kigali)", "盧旺達 基加利", "Africa/Kigali"),
    WorldCity("Samoa (Apia)", "薩摩亞 阿皮亞", "Pacific/Apia"),
    WorldCity("San Marino", "聖馬力諾", "Europe/San_Marino"),
    WorldCity("São Tomé & Príncipe", "聖多美和普林西比", "Africa/Sao_Tome"),
    WorldCity("Saudi Arabia (Riyadh)", "沙特阿拉伯 利雅得", "Asia/Riyadh"),
    WorldCity("Senegal (Dakar)", "塞內加爾 達喀爾", "Africa/Dakar"),
    WorldCity("Serbia (Belgrade)", "塞爾維亞 貝爾格萊德", "Europe/Belgrade"),
    WorldCity("Seychelles (Victoria)", "塞舌爾 維多利亞", "Indian/Mahe"),
    WorldCity("Sierra Leone (Freetown)", "塞拉利昂 弗里敦", "Africa/Freetown"),
    WorldCity("Singapore", "新加坡", "Asia/Singapore"),
    WorldCity("Slovakia (Bratislava)", "斯洛伐克 布拉迪斯拉發", "Europe/Bratislava"),
    WorldCity("Slovenia (Ljubljana)", "斯洛文尼亞 盧布爾雅那", "Europe/Ljubljana"),
    WorldCity("Solomon Islands (Honiara)", "所羅門群島 霍尼亞拉", "Pacific/Guadalcanal"),
    WorldCity("Somalia (Mogadishu)", "索馬里 摩加迪沙", "Africa/Mogadishu"),
    WorldCity("South Africa (Johannesburg)", "南非 約翰內斯堡", "Africa/Johannesburg"),
    WorldCity("South Korea (Seoul)", "韓國 首爾", "Asia/Seoul"),
    WorldCity("South Sudan (Juba)", "南蘇丹 朱巴", "Africa/Juba"),
    WorldCity("Spain (Madrid)", "西班牙 馬德里", "Europe/Madrid"),
    WorldCity("Spain (Barcelona)", "西班牙 巴塞羅那", "Europe/Madrid"),
    WorldCity("Sri Lanka (Colombo)", "斯里蘭卡 科倫坡", "Asia/Colombo"),
    WorldCity("Sudan (Khartoum)", "蘇丹 喀土穆", "Africa/Khartoum"),
    WorldCity("Suriname (Paramaribo)", "蘇里南 帕拉馬里博", "America/Paramaribo"),
    WorldCity("Sweden (Stockholm)", "瑞典 斯德哥爾摩", "Europe/Stockholm"),
    WorldCity("Switzerland (Zurich)", "瑞士 蘇黎世", "Europe/Zurich"),
    WorldCity("Syria (Damascus)", "敘利亞 大馬士革", "Asia/Damascus"),
    WorldCity("Taiwan (Taipei)", "台灣 台北", "Asia/Taipei"),
    WorldCity("Tajikistan (Dushanbe)", "塔吉克斯坦 杜尚別", "Asia/Dushanbe"),
    WorldCity("Tanzania (Dar es Salaam)", "坦桑尼亞 達累斯薩拉姆", "Africa/Dar_es_Salaam"),
    WorldCity("Thailand (Bangkok)", "泰國 曼谷", "Asia/Bangkok"),
    WorldCity("Timor-Leste (Dili)", "東帝汶 帝力", "Asia/Dili"),
    WorldCity("Togo (Lomé)", "多哥 洛美", "Africa/Lome"),
    WorldCity("Tonga (Nuku'alofa)", "湯加 努庫阿洛法", "Pacific/Tongatapu"),
    WorldCity("Trinidad & Tobago", "特立尼達和多巴哥", "America/Port_of_Spain"),
    WorldCity("Tunisia (Tunis)", "突尼斯 突尼斯市", "Africa/Tunis"),
    WorldCity("Türkiye (Istanbul)", "土耳其 伊斯坦布爾", "Europe/Istanbul"),
    WorldCity("Turkmenistan (Ashgabat)", "土庫曼斯坦 阿什哈巴德", "Asia/Ashgabat"),
    WorldCity("Tuvalu", "圖瓦盧", "Pacific/Funafuti"),
    WorldCity("Uganda (Kampala)", "烏干達 坎帕拉", "Africa/Kampala"),
    WorldCity("Ukraine (Kyiv)", "烏克蘭 基輔", "Europe/Kyiv"),
    WorldCity("UAE: Dubai", "阿聯酋 杜拜", "Asia/Dubai"),
    WorldCity("UAE: Abu Dhabi", "阿聯酋 阿布扎比", "Asia/Dubai"),
    WorldCity("UK (London)", "英國 倫敦", "Europe/London"),
    WorldCity("USA: New York", "美國 紐約", "America/New_York"),
    WorldCity("USA: Boston", "美國 波士頓", "America/New_York"),
    WorldCity("USA: Chicago", "美國 芝加哥", "America/Chicago"),
    WorldCity("USA: Denver", "美國 丹佛", "America/Denver"),
    WorldCity("USA: Los Angeles", "美國 洛杉磯", "America/Los_Angeles"),
    WorldCity("USA: San Francisco", "美國 三藩市", "America/Los_Angeles"),
    WorldCity("USA: Seattle", "美國 西雅圖", "America/Los_Angeles"),
    WorldCity("USA: Anchorage", "美國 安克雷奇", "America/Anchorage"),
    WorldCity("USA: Honolulu", "美國 檀香山", "Pacific/Honolulu"),
    WorldCity("Uruguay (Montevideo)", "烏拉圭 蒙得維的亞", "America/Montevideo"),
    WorldCity("Uzbekistan (Tashkent)", "烏茲別克斯坦 塔什干", "Asia/Tashkent"),
    WorldCity("Vanuatu (Port Vila)", "瓦努阿圖 維拉港", "Pacific/Efate"),
    WorldCity("Vatican City", "梵蒂岡", "Europe/Vatican"),
    WorldCity("Venezuela (Caracas)", "委內瑞拉 加拉加斯", "America/Caracas"),
    WorldCity("Vietnam (Hanoi)", "越南 河內", "Asia/Ho_Chi_Minh"),
    WorldCity("Vietnam (Ho Chi Minh City)", "越南 胡志明市", "Asia/Ho_Chi_Minh"),
    WorldCity("Yemen (Aden)", "也門 亞丁", "Asia/Aden"),
    WorldCity("Zambia (Lusaka)", "贊比亞 盧薩卡", "Africa/Lusaka"),
    WorldCity("Zimbabwe (Harare)", "津巴布韋 哈拉雷", "Africa/Harare")
).sortedBy { it.en }

/** Display name for a zone id in the current language. */
fun zoneLabel(zoneId: String): String =
    WORLD_CITIES.firstOrNull { it.zone == zoneId }?.let { cityLabel(it) } ?: zoneId

fun cityLabel(c: WorldCity): String = if (L10n.lang == "zh") c.zh else c.en

/** Sub-tab header shown ABOVE the pager while a Tools page is visible.
 *  It is hoisted out of the pages themselves, so during a swipe you see
 *  ONE fixed tab bar with only the content sliding — not two copies of
 *  the bar sliding past each other. ScrollableTabRow gives every label
 *  its full width ("Stopwatch" is never truncated). The four tool
 *  sub-pages are TOP-LEVEL pages of the main pager, so swiping between
 *  them feels exactly like swiping between the other tabs. */
@Composable
fun ToolTabs(selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        listOf(
            t("Alarms", "鬧鐘") to 0,
            t("Clock", "時鐘") to 1,
            t("Stopwatch", "碼錶") to 2,
            t("Timer", "計時") to 3
        ).forEach { (label, i) ->
            Tab(
                selected = selected == i,
                onClick = { onSelect(i) },
                text = { Text(label, maxLines = 1, softWrap = false) }
            )
        }
    }
}

// ---------- 世界時鐘 ----------

@Composable
fun WorldClockView(data: AppData, persistThenSync: ((AppData) -> AppData) -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // No default city: the list starts empty until the user adds one.
    val zones = data.worldClocks
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                t("World Clock", "世界時鐘"),
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                t(
                    "Add cities to see their local time at a glance — handy when crossing time zones.",
                    "加入城市之後，隨時睇到各地時間，去旅行換時區都唔怕。"
                ),
                fontSize = 13.sp
            )
        }
        if (zones.isEmpty()) {
            item {
                Text(
                    t(
                        "No cities yet — tap “＋ Add city” below to add one.",
                        "仲未加城市——撳下面「＋ 加入城市」加一個。"
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(zones, key = { it }) { zone ->
            val zoneId = remember(zone) { runCatching { ZoneId.of(zone) }.getOrNull() }
            if (zoneId != null) {
                val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId)
                val localDate = LocalDate.now()
                val dayDiff = time.toLocalDate().compareTo(localDate)
                val dayText = when {
                    dayDiff > 0 -> t(" (+1d)", "（+1日）")
                    dayDiff < 0 -> t(" (−1d)", "（−1日）")
                    else -> ""
                }
                Card(Modifier.fillMaxWidth()) {
                    // Delete button stays hidden until the row is LONG-PRESSED
                    // so it can't be hit accidentally while scrolling.
                    var showDelete by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .padding(14.dp).fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (showDelete) showDelete = false },
                                onLongClick = { showDelete = true }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                zoneLabel(zone),
                                fontSize = 17.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                DateTimeFormatter.ofPattern(
                                    if (L10n.lang == "zh") "M月d日 EEEE" else "EEE, MMM d",
                                    L10n.locale
                                ).format(time) + dayText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            time.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        if (showDelete) {
                            IconButton(onClick = {
                                persistThenSync { d -> d.copy(worldClocks = d.worldClocks - zone) }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = t("Delete", "刪除"))
                            }
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(t("＋ Add city", "＋ 加入城市")) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        val candidates = WORLD_CITIES.filter {
            it.en.contains(query, ignoreCase = true) ||
                it.zh.contains(query) ||
                it.zone.contains(query, ignoreCase = true)
        }.filter { it.zone !in zones }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(t("Add city", "加入城市")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(t("Search city / country", "搜尋城市／國家")) }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.height(340.dp)) {
                        items(candidates, key = { it.en + it.zone }) { city ->
                            Text(
                                cityLabel(city),
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        persistThenSync { d ->
                                            d.copy(worldClocks = (d.worldClocks + city.zone).distinct())
                                        }
                                        showPicker = false
                                    }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(t("Close", "關閉")) }
            }
        )
    }
}

// ---------- 碼錶 ----------

@Composable
fun StopwatchView() {
    var running by remember { mutableStateOf(false) }
    var accumulated by remember { mutableStateOf(0L) }
    var anchorTime by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(running) {
        while (running) {
            tick = SystemClock.elapsedRealtime()
            delay(47)
        }
    }
    val elapsed = accumulated + (if (running) tick - anchorTime else 0L)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t("Stopwatch", "碼錶"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        // Monospace font: digits keep a constant width so the display does
        // not jitter while counting.
        Text(
            formatStopwatch(elapsed),
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (running) {
                    accumulated += SystemClock.elapsedRealtime() - anchorTime
                    running = false
                } else {
                    anchorTime = SystemClock.elapsedRealtime()
                    tick = anchorTime
                    running = true
                }
            }) {
                Text(
                    when {
                        running -> t("Pause", "暫停")
                        elapsed > 0 -> t("Resume", "繼續")
                        else -> t("Start", "開始")
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    if (running) laps.add(0, elapsed)
                    else { accumulated = 0; laps.clear() }
                },
                enabled = running || elapsed > 0
            ) { Text(if (running) t("Lap", "圈數") else t("Reset", "重設")) }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(laps.size) { i ->
                val lap = laps[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        t("Lap ", "第 ") + (laps.size - i) + t("", " 圈"),
                        Modifier.weight(1f),
                        fontSize = 14.sp
                    )
                    Text(
                        formatStopwatch(lap),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun formatStopwatch(elapsed: Long): String = String.format(
    Locale.US, "%02d:%02d:%02d.%02d",
    elapsed / 3600000, elapsed / 60000 % 60, elapsed / 1000 % 60, elapsed / 10 % 100
)

// ---------- 計時 ----------

@Composable
fun TimerView(
    data: AppData,
    persistThenSync: ((AppData) -> AppData) -> Unit,
    selH: Int, selM: Int, selS: Int,
    onSel: (Int, Int, Int) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val h = selH
    val m = selM
    val s = selS
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val active = data.timerEndAt > now

    LaunchedEffect(data.timerEndAt) {
        while (data.timerEndAt > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    val remaining = (data.timerEndAt - now).coerceAtLeast(0L)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t("Timer", "計時器"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (active) {
            Text(
                String.format(
                    Locale.US, "%02d:%02d:%02d",
                    remaining / 3600000, remaining / 60000 % 60, remaining / 1000 % 60
                ),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                TimerReceiver.cancel(ctx)
                persistThenSync { d -> d.copy(timerEndAt = 0) }
                now = System.currentTimeMillis()
            }) { Text(t("Cancel timer", "取消計時")) }
            Text(
                t(
                    "Keeps running with a notification even if you close the app.",
                    "就算閂咗 app，時間到都會響通知。"
                ),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            // Live preview of the selected duration so the h/m/s wheels are
            // unmistakable.
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", h, m, s),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                NumberWheel(
                    (0..23).toList(), h, onSelected = { onSel(it, m, s) },
                    modifier = Modifier.weight(1f)
                ) { t("Hours", "時") }
                NumberWheel(
                    (0..59).toList(), m, onSelected = { onSel(h, it, s) },
                    modifier = Modifier.weight(1f)
                ) { t("Minutes", "分") }
                NumberWheel(
                    (0..59).toList(), s, onSelected = { onSel(h, m, it) },
                    modifier = Modifier.weight(1f)
                ) { t("Seconds", "秒") }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val duration = (h * 3600 + m * 60 + s) * 1000L
                    if (duration > 0) {
                        val endAt = System.currentTimeMillis() + duration
                        persistThenSync { d -> d.copy(timerEndAt = endAt) }
                        TimerReceiver.schedule(ctx, endAt)
                        now = System.currentTimeMillis()
                    }
                },
                enabled = h > 0 || m > 0 || s > 0
            ) { Text(t("Start timer", "開始計時")) }
            Text(
                t(
                    "Runs on the system alarm clock — it fires a notification on time even when the app is closed.",
                    "計時用系統鬧鐘運行，閂咗 app 都會準時響通知。"
                ),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * Vertical scroll wheel (iOS-style). Three rows visible, the middle one is
 * the selected value; snaps after fling.
 */
@Composable
private fun NumberWheel(
    values: List<Int>,
    initial: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    val itemHeight = 44.dp
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = initial.coerceIn(0, values.size - 1)
    )
    val centered by remember {
        derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, values.size - 1) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) onSelected(values[centered])
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        label()
        LazyColumn(
            state = state,
            modifier = Modifier.height(itemHeight * 3),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state)
        ) {
            item { Box(Modifier.height(itemHeight)) } // top spacer
            items(values.size) { i ->
                val isCenter = i == centered
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        String.format(Locale.US, "%02d", values[i]),
                        fontSize = if (isCenter) 30.sp else 20.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            item { Box(Modifier.height(itemHeight)) } // bottom spacer
        }
    }
}
