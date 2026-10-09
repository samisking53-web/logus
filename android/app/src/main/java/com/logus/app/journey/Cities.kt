package com.logus.app.journey

/**
 * 도시 추천용 목록 (앱에 내장)
 * 글자를 칠 때마다 인터넷 없이 바로 추천하려고 국내외 주요 여행 도시를 넣어 둔다(한글·영문 검색).
 * 목록에 없으면 "지도에서 찾기"(CityRepository, 오픈스트리트맵)로 세계 도시를 찾는다.
 * 그래도 없으면 입력한 글자 그대로 저장한다.
 * 도시를 더 넣고 싶으면 아래 목록에 한 줄씩 추가하면 된다: c("한글 이름", "국가", "English name")
 */
data class City(
    /** 화면에 보이고 저장되는 이름(한글) */
    val name: String,
    /** 국가(한글) */
    val country: String,
    /** 영문 이름(검색용). 지도에서 찾은 도시는 빈칸 */
    val english: String,
    /** 지도 데이터(OSM)에서 찾은 도시인지(화면에 출처를 표시한다) */
    val fromMap: Boolean = false,
)

object Cities {

    /** 검색: 한글 이름·국가·영문 이름에 입력한 글자가 들어 있는 도시. 이름이 입력으로 시작하는 도시를 먼저 보여 준다. */
    fun search(query: String, limit: Int = 5): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all
            .filter { it.name.contains(q) || it.country.contains(q) || it.english.lowercase().contains(q) }
            .sortedBy {
                when {
                    it.name.startsWith(q) || it.english.lowercase().startsWith(q) -> 0
                    it.name.contains(q) || it.english.lowercase().contains(q) -> 1
                    else -> 2 // 국가 이름만 맞음
                }
            }
            .take(limit)
    }

    private fun c(name: String, country: String, english: String) = City(name, country, english)

    val all: List<City> = listOf(
        // 대한민국
        c("서울", "대한민국", "Seoul"), c("부산", "대한민국", "Busan"), c("제주", "대한민국", "Jeju"),
        c("서귀포", "대한민국", "Seogwipo"), c("인천", "대한민국", "Incheon"), c("대구", "대한민국", "Daegu"),
        c("대전", "대한민국", "Daejeon"), c("광주", "대한민국", "Gwangju"), c("울산", "대한민국", "Ulsan"),
        c("세종", "대한민국", "Sejong"), c("수원", "대한민국", "Suwon"), c("강릉", "대한민국", "Gangneung"),
        c("속초", "대한민국", "Sokcho"), c("양양", "대한민국", "Yangyang"), c("춘천", "대한민국", "Chuncheon"),
        c("평창", "대한민국", "Pyeongchang"), c("가평", "대한민국", "Gapyeong"), c("경주", "대한민국", "Gyeongju"),
        c("전주", "대한민국", "Jeonju"), c("여수", "대한민국", "Yeosu"), c("순천", "대한민국", "Suncheon"),
        c("목포", "대한민국", "Mokpo"), c("통영", "대한민국", "Tongyeong"), c("거제", "대한민국", "Geoje"),
        c("남해", "대한민국", "Namhae"), c("포항", "대한민국", "Pohang"), c("안동", "대한민국", "Andong"),
        c("창원", "대한민국", "Changwon"), c("진주", "대한민국", "Jinju"), c("군산", "대한민국", "Gunsan"),
        c("담양", "대한민국", "Damyang"), c("공주", "대한민국", "Gongju"), c("부여", "대한민국", "Buyeo"),
        c("단양", "대한민국", "Danyang"), c("태안", "대한민국", "Taean"), c("보령", "대한민국", "Boryeong"),
        c("파주", "대한민국", "Paju"), c("울릉도", "대한민국", "Ulleungdo"), c("고성", "대한민국", "Goseong"),
        c("삼척", "대한민국", "Samcheok"), c("동해", "대한민국", "Donghae"), c("영덕", "대한민국", "Yeongdeok"),
        c("하동", "대한민국", "Hadong"), c("완도", "대한민국", "Wando"), c("청주", "대한민국", "Cheongju"),
        // 일본
        c("도쿄", "일본", "Tokyo"), c("오사카", "일본", "Osaka"), c("교토", "일본", "Kyoto"),
        c("나라", "일본", "Nara"), c("고베", "일본", "Kobe"), c("후쿠오카", "일본", "Fukuoka"),
        c("삿포로", "일본", "Sapporo"), c("오키나와", "일본", "Okinawa"), c("나고야", "일본", "Nagoya"),
        c("요코하마", "일본", "Yokohama"), c("가마쿠라", "일본", "Kamakura"), c("하코네", "일본", "Hakone"),
        c("히로시마", "일본", "Hiroshima"), c("가나자와", "일본", "Kanazawa"), c("벳푸", "일본", "Beppu"),
        c("유후인", "일본", "Yufuin"), c("나가사키", "일본", "Nagasaki"), c("오타루", "일본", "Otaru"),
        c("하코다테", "일본", "Hakodate"), c("다카마쓰", "일본", "Takamatsu"), c("마쓰야마", "일본", "Matsuyama"),
        c("센다이", "일본", "Sendai"), c("시즈오카", "일본", "Shizuoka"), c("구마모토", "일본", "Kumamoto"),
        c("가고시마", "일본", "Kagoshima"),
        // 중국·홍콩·마카오·대만·몽골
        c("베이징", "중국", "Beijing"), c("상하이", "중국", "Shanghai"), c("칭다오", "중국", "Qingdao"),
        c("시안", "중국", "Xi'an"), c("청두", "중국", "Chengdu"), c("항저우", "중국", "Hangzhou"),
        c("하얼빈", "중국", "Harbin"), c("장자제", "중국", "Zhangjiajie"), c("광저우", "중국", "Guangzhou"),
        c("선전", "중국", "Shenzhen"), c("홍콩", "홍콩", "Hong Kong"), c("마카오", "마카오", "Macau"),
        c("타이베이", "대만", "Taipei"), c("가오슝", "대만", "Kaohsiung"), c("타이중", "대만", "Taichung"),
        c("타이난", "대만", "Tainan"), c("화롄", "대만", "Hualien"), c("울란바토르", "몽골", "Ulaanbaatar"),
        // 동남아시아
        c("방콕", "태국", "Bangkok"), c("치앙마이", "태국", "Chiang Mai"), c("푸껫", "태국", "Phuket"),
        c("파타야", "태국", "Pattaya"), c("끄라비", "태국", "Krabi"), c("코사무이", "태국", "Koh Samui"),
        c("다낭", "베트남", "Da Nang"), c("호이안", "베트남", "Hoi An"), c("하노이", "베트남", "Hanoi"),
        c("호찌민", "베트남", "Ho Chi Minh City"), c("나트랑", "베트남", "Nha Trang"), c("푸꾸옥", "베트남", "Phu Quoc"),
        c("달랏", "베트남", "Da Lat"), c("하롱베이", "베트남", "Ha Long"), c("사파", "베트남", "Sa Pa"),
        c("세부", "필리핀", "Cebu"), c("보라카이", "필리핀", "Boracay"), c("마닐라", "필리핀", "Manila"),
        c("보홀", "필리핀", "Bohol"), c("팔라완", "필리핀", "Palawan"), c("싱가포르", "싱가포르", "Singapore"),
        c("쿠알라룸푸르", "말레이시아", "Kuala Lumpur"), c("코타키나발루", "말레이시아", "Kota Kinabalu"),
        c("페낭", "말레이시아", "Penang"), c("랑카위", "말레이시아", "Langkawi"), c("발리", "인도네시아", "Bali"),
        c("자카르타", "인도네시아", "Jakarta"), c("롬복", "인도네시아", "Lombok"), c("시엠립", "캄보디아", "Siem Reap"),
        c("프놈펜", "캄보디아", "Phnom Penh"), c("비엔티안", "라오스", "Vientiane"), c("루앙프라방", "라오스", "Luang Prabang"),
        c("방비엥", "라오스", "Vang Vieng"), c("양곤", "미얀마", "Yangon"),
        // 남아시아·중동·중앙아시아
        c("뉴델리", "인도", "New Delhi"), c("뭄바이", "인도", "Mumbai"), c("자이푸르", "인도", "Jaipur"),
        c("아그라", "인도", "Agra"), c("카트만두", "네팔", "Kathmandu"), c("포카라", "네팔", "Pokhara"),
        c("몰디브", "몰디브", "Maldives"), c("콜롬보", "스리랑카", "Colombo"), c("두바이", "아랍에미리트", "Dubai"),
        c("아부다비", "아랍에미리트", "Abu Dhabi"), c("도하", "카타르", "Doha"), c("이스탄불", "튀르키예", "Istanbul"),
        c("카파도키아", "튀르키예", "Cappadocia"), c("안탈리아", "튀르키예", "Antalya"), c("예루살렘", "이스라엘", "Jerusalem"),
        c("페트라", "요르단", "Petra"), c("타슈켄트", "우즈베키스탄", "Tashkent"), c("사마르칸트", "우즈베키스탄", "Samarkand"),
        c("알마티", "카자흐스탄", "Almaty"),
        // 유럽
        c("파리", "프랑스", "Paris"), c("니스", "프랑스", "Nice"), c("리옹", "프랑스", "Lyon"),
        c("마르세유", "프랑스", "Marseille"), c("몽생미셸", "프랑스", "Mont-Saint-Michel"), c("런던", "영국", "London"),
        c("에든버러", "영국", "Edinburgh"), c("맨체스터", "영국", "Manchester"), c("리버풀", "영국", "Liverpool"),
        c("옥스퍼드", "영국", "Oxford"), c("더블린", "아일랜드", "Dublin"), c("로마", "이탈리아", "Rome"),
        c("피렌체", "이탈리아", "Florence"), c("베네치아", "이탈리아", "Venice"), c("밀라노", "이탈리아", "Milan"),
        c("나폴리", "이탈리아", "Naples"), c("아말피", "이탈리아", "Amalfi"), c("친퀘테레", "이탈리아", "Cinque Terre"),
        c("시칠리아", "이탈리아", "Sicily"), c("바르셀로나", "스페인", "Barcelona"), c("마드리드", "스페인", "Madrid"),
        c("세비야", "스페인", "Seville"), c("그라나다", "스페인", "Granada"), c("발렌시아", "스페인", "Valencia"),
        c("말라가", "스페인", "Malaga"), c("마요르카", "스페인", "Mallorca"), c("리스본", "포르투갈", "Lisbon"),
        c("포르투", "포르투갈", "Porto"), c("신트라", "포르투갈", "Sintra"), c("라고스", "포르투갈", "Lagos"),
        c("베를린", "독일", "Berlin"), c("뮌헨", "독일", "Munich"), c("프랑크푸르트", "독일", "Frankfurt"),
        c("함부르크", "독일", "Hamburg"), c("하이델베르크", "독일", "Heidelberg"), c("퓌센", "독일", "Fussen"),
        c("암스테르담", "네덜란드", "Amsterdam"), c("로테르담", "네덜란드", "Rotterdam"), c("브뤼셀", "벨기에", "Brussels"),
        c("브뤼헤", "벨기에", "Bruges"), c("룩셈부르크", "룩셈부르크", "Luxembourg"), c("취리히", "스위스", "Zurich"),
        c("인터라켄", "스위스", "Interlaken"), c("루체른", "스위스", "Lucerne"), c("제네바", "스위스", "Geneva"),
        c("체르마트", "스위스", "Zermatt"), c("빈", "오스트리아", "Vienna"), c("잘츠부르크", "오스트리아", "Salzburg"),
        c("할슈타트", "오스트리아", "Hallstatt"), c("인스브루크", "오스트리아", "Innsbruck"), c("프라하", "체코", "Prague"),
        c("체스키크룸로프", "체코", "Cesky Krumlov"), c("부다페스트", "헝가리", "Budapest"), c("바르샤바", "폴란드", "Warsaw"),
        c("크라쿠프", "폴란드", "Krakow"), c("자그레브", "크로아티아", "Zagreb"), c("두브로브니크", "크로아티아", "Dubrovnik"),
        c("스플리트", "크로아티아", "Split"), c("류블랴나", "슬로베니아", "Ljubljana"), c("블레드", "슬로베니아", "Bled"),
        c("아테네", "그리스", "Athens"), c("산토리니", "그리스", "Santorini"), c("미코노스", "그리스", "Mykonos"),
        c("코펜하겐", "덴마크", "Copenhagen"), c("스톡홀름", "스웨덴", "Stockholm"), c("오슬로", "노르웨이", "Oslo"),
        c("베르겐", "노르웨이", "Bergen"), c("트롬쇠", "노르웨이", "Tromso"), c("헬싱키", "핀란드", "Helsinki"),
        c("로바니에미", "핀란드", "Rovaniemi"), c("레이캬비크", "아이슬란드", "Reykjavik"), c("탈린", "에스토니아", "Tallinn"),
        c("리가", "라트비아", "Riga"), c("빌뉴스", "리투아니아", "Vilnius"), c("부쿠레슈티", "루마니아", "Bucharest"),
        c("소피아", "불가리아", "Sofia"), c("몰타", "몰타", "Malta"), c("모스크바", "러시아", "Moscow"),
        c("상트페테르부르크", "러시아", "Saint Petersburg"), c("블라디보스토크", "러시아", "Vladivostok"),
        c("트빌리시", "조지아", "Tbilisi"),
        // 아프리카
        c("카이로", "이집트", "Cairo"), c("룩소르", "이집트", "Luxor"), c("마라케시", "모로코", "Marrakech"),
        c("카사블랑카", "모로코", "Casablanca"), c("셰프샤우엔", "모로코", "Chefchaouen"), c("케이프타운", "남아프리카공화국", "Cape Town"),
        c("나이로비", "케냐", "Nairobi"), c("잔지바르", "탄자니아", "Zanzibar"),
        // 북아메리카
        c("뉴욕", "미국", "New York"), c("로스앤젤레스", "미국", "Los Angeles"), c("샌프란시스코", "미국", "San Francisco"),
        c("라스베이거스", "미국", "Las Vegas"), c("시애틀", "미국", "Seattle"), c("시카고", "미국", "Chicago"),
        c("보스턴", "미국", "Boston"), c("워싱턴 D.C.", "미국", "Washington, D.C."), c("마이애미", "미국", "Miami"),
        c("올랜도", "미국", "Orlando"), c("샌디에이고", "미국", "San Diego"), c("하와이 호놀룰루", "미국", "Honolulu"),
        c("마우이", "미국", "Maui"), c("괌", "미국", "Guam"), c("사이판", "미국", "Saipan"),
        c("포틀랜드", "미국", "Portland"), c("뉴올리언스", "미국", "New Orleans"), c("그랜드캐니언", "미국", "Grand Canyon"),
        c("앵커리지", "미국", "Anchorage"), c("밴쿠버", "캐나다", "Vancouver"), c("토론토", "캐나다", "Toronto"),
        c("몬트리올", "캐나다", "Montreal"), c("퀘벡", "캐나다", "Quebec City"), c("밴프", "캐나다", "Banff"),
        c("옐로나이프", "캐나다", "Yellowknife"), c("칸쿤", "멕시코", "Cancun"), c("멕시코시티", "멕시코", "Mexico City"),
        c("아바나", "쿠바", "Havana"),
        // 남아메리카
        c("리우데자네이루", "브라질", "Rio de Janeiro"), c("상파울루", "브라질", "Sao Paulo"),
        c("부에노스아이레스", "아르헨티나", "Buenos Aires"), c("엘칼라파테", "아르헨티나", "El Calafate"),
        c("쿠스코", "페루", "Cusco"), c("리마", "페루", "Lima"), c("우유니", "볼리비아", "Uyuni"),
        c("산티아고", "칠레", "Santiago"), c("보고타", "콜롬비아", "Bogota"),
        // 오세아니아
        c("시드니", "호주", "Sydney"), c("멜버른", "호주", "Melbourne"), c("브리즈번", "호주", "Brisbane"),
        c("골드코스트", "호주", "Gold Coast"), c("케언스", "호주", "Cairns"), c("퍼스", "호주", "Perth"),
        c("오클랜드", "뉴질랜드", "Auckland"), c("퀸스타운", "뉴질랜드", "Queenstown"), c("크라이스트처치", "뉴질랜드", "Christchurch"),
        c("피지", "피지", "Fiji"),
    )
}
