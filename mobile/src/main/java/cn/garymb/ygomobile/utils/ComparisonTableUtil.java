package cn.garymb.ygomobile.utils;

public interface ComparisonTableUtil {
    /*23333先行密码与正式密码对照数组
     *来源网站：https://ygocdb.com/api/v0/idChangelogArray.jsonp
     */
    int[] oldIDsArray = {
            100200252,
            100200245,
            100200246,
            100200247,
            100219001,
            100219002,
            100216001,
            101203081,
            101203082,
            101203083,
            101203084,
            101203085,
            101203086,
            101203087,
            101203088,
            101203089,
            101203090,
            101203091,
            101203092,
            101203093,
            101203094,
            101203095,
            101203096,
            100230201,
            100200253,
            101204081,
            101204082,
            101204083,
            101204084,
            101204085,
            101204086,
            101204087,
            101204088,
            101204089,
            101204090,
            101204091,
            101204092,
            101204093,
            101204094,
            101204095,
            101204096,
            101205000,
            101205001,
            101205002,
            101205003,
            101205004,
            101205005,
            101205006,
            101205007,
            101205008,
            101205009,
            101205010,
            101205011,
            101205012,
            101205013,
            101205014,
            101205015,
            101205016,
            101205017,
            101205018,
            101205019,
            101205020,
            101205021,
            101205022,
            101205023,
            101205024,
            101205025,
            101205026,
            101205027,
            101205028,
            101205029,
            101205030,
            101205031,
            101205032,
            101205033,
            101205034,
            101205035,
            101205036,
            101205037,
            101205038,
            101205039,
            101205040,
            101205041,
            101205042,
            101205043,
            101205044,
            101205045,
            101205046,
            101205047,
            101205048,
            101205049,
            101205050,
            101205051,
            101205052,
            101205053,
            101205054,
            101205055,
            101205056,
            101205057,
            101205058,
            101205059,
            101205060,
            101205061,
            101205062,
            101205063,
            101205064,
            101205065,
            101205066,
            101205067,
            101205068,
            101205069,
            101205070,
            101205071,
            101205072,
            101205073,
            101205074,
            101205075,
            101205076,
            101205077,
            101205079,
            101205078,
            101205080,
            100200254,
            100220001,
            100220002,
            100220003,
            100220004,
            100220005,
            100220006,
            100220007,
            100220019,
            100220020,
            100220021,
            100220022,
            100220023,
            100220024,
            100220025,
            100220201,
            100220037,
            100220202,
            100220203,
            100220204,
            100220205,
            100220206,
            100200255,
            100222001,
            100222002,
            100222003,
            100222004,
            100222005,
            100222006,
            100222007,
            100222008,
            100222009,
            100222010,
            100222201,
            100222012,
            100222013,
            100222202,
            100222015,
            100222016,
            100222203,
            100222018,
            100222019,
            100222020,
            100222021,
            100222022,
            100222023,
            100222024,
            100222025,
            100222204,
            100222027,
            100222028,
            100222029,
            100230001,
            100200259,
            101206000,
            101206201,
            101206202,
            101206203,
            101206004,
            101206005,
            101206006,
            101206007,
            101206008,
            101206009,
            101206010,
            101206011,
            101206012,
            101206013,
            101206014,
            101206015,
            101206016,
            101206017,
            101206018,
            101206019,
            101206020,
            101206021,
            101206022,
            101206023,
            101206024,
            101206025,
            101206026,
            101206027,
            101206028,
            101206029,
            101206030,
            101206031,
            101206032,
            101206033,
            101206034,
            101206035,
            101206036,
            101206037,
            101206038,
            101206039,
            101206040,
            101206204,
            101206205,
            101206043,
            101206044,
            101206045,
            101206046,
            101206047,
            101206206,
            101206207,
            101206050,
            101206051,
            101206052,
            101206053,
            101206054,
            101206055,
            101206056,
            101206057,
            101206058,
            101206059,
            101206060,
            101206061,
            101206062,
            101206063,
            101206064,
            101206065,
            101206066,
            101206067,
            101206208,
            101206209,
            101206070,
            101206071,
            101206072,
            101206073,
            101206074,
            101206075,
            101206076,
            101206077,
            101206078,
            101206079,
            101206080,
            101206167,
            101205081,
            101205082,
            101205083,
            101205084,
            101205085,
            101205086,
            101205087,
            101205088,
            101205089,
            101205090,
            101205091,
            101205092,
            101205093,
            101205094,
            101205095,
            101205096,
            100200260,
            100223001,
            100223002,
            100223003,
            100223004,
            100223005,
            100223006,
            100223007,
            100223008,
            100223009,
            100223010,
            100223015,
            100223016,
            100223017,
            100223018,
            100223019,
            100223020,
            100223021,
            100223022,
            100223023,
            100223024,
            100223031,
            100223032,
            100223033,
            100223034,
            100223035,
            100223036,
            100223037,
            100223038,
            100223039,
            100223040,
            100200256,
            100200257,
            100200258,
            100200261,
            100221000,
            100224002,
            100224003,
            100224018,
            100224019,
            100224027,
            100224032,
            100224033,
            100224034,
            101207000,
            101207001,
            101207002,
            101207003,
            101207004,
            101207005,
            101207006,
            101207007,
            101207008,
            101207009,
            101207010,
            101207011,
            101207012,
            101207013,
            101207014,
            101207015,
            101207016,
            101207017,
            101207018,
            101207019,
            101207020,
            101207021,
            101207022,
            101207023,
            101207024,
            101207025,
            101207026,
            101207027,
            101207028,
            101207029,
            101207030,
            101207031,
            101207032,
            101207033,
            101207034,
            101207035,
            101207036,
            101207037,
            101207038,
            101207039,
            101207040,
            101207041,
            101207042,
            101207043,
            101207044,
            101207045,
            101207046,
            101207047,
            101207048,
            101207049,
            101207050,
            101207051,
            101207052,
            101207053,
            101207054,
            101207055,
            101207056,
            101207057,
            101207058,
            101207059,
            101207060,
            101207061,
            101207062,
            101207063,
            101207064,
            101207065,
            101207066,
            101207067,
            101207068,
            101207069,
            101207070,
            101207071,
            101207072,
            101207073,
            101207074,
            101207075,
            101207076,
            101207077,
            101207078,
            101207079,
            101207080,
            100228001,
            100225001,
            100230401,
            100200262,
            101206081,
            101206082,
            101206083,
            101206084,
            101206085,
            101206086,
            101206087,
            101206088,
            101206089,
            101206090,
            101206091,
            101206092,
            101206093,
            101206094,
            101206095,
            101206100,
            100200263,
            100227001,
            100227002,
            100227003,
            100227004,
            100227027,
            100227028,
            100227029,
            100227030,
            100227042,
            100227043,
            100227044,
            100227045,
            100227072,
            100227073,
            100227074,
            100227075,
            100231002,
            100200264,
            100229001,
            100229002,
            100229019,
            100229020,
            100229033,
            100229034,
            100232001,
            100232002,
            100232003,
            100232004,
            100232005,
            100233001,
            100233002,
            100233003,
            100233004,
            100233005,
            100233006,
            100233007,
            100233008,
            100233009,
            100233010,
            100233011,
            100233012,
            100233013,
            100233014,
            100233015,
            100233016,
            100233201,
            100233018,
            100233019,
            100233020,
            100230501,
            100200265,
            101207081,
            101207082,
            101207083,
            101207084,
            101207085,
            101207086,
            101207087,
            101207088,
            101207089,
            101207090,
            101207091,
            101207092,
            101207093,
            101207094,
            101207095,
            101207096,
            101208000,
            101208201,
            101208202,
            101208203,
            101208004,
            101208005,
            101208006,
            101208007,
            101208008,
            101208009,
            101208010,
            101208011,
            101208012,
            101208013,
            101208014,
            101208015,
            101208016,
            101208017,
            101208018,
            101208019,
            101208020,
            101208021,
            101208022,
            101208023,
            101208024,
            101208025,
            101208026,
            101208027,
            101208028,
            101208029,
            101208030,
            101208031,
            101208032,
            101208033,
            101208034,
            101208035,
            101208036,
            101208037,
            101208204,
            101208039,
            101208040,
            101208041,
            101208042,
            101208043,
            101208205,
            101208045,
            101208046,
            101208047,
            101208048,
            101208049,
            101208050,
            101208206,
            101208052,
            101208053,
            101208207,
            101208055,
            101208056,
            101208057,
            101208058,
            101208059,
            101208060,
            101208061,
            101208062,
            101208063,
            101208064,
            101208065,
            101208066,
            101208067,
            101208068,
            101208069,
            101208208,
            101208071,
            101208072,
            101208073,
            101208074,
            101208075,
            101208076,
            101208077,
            101208078,
            101208079,
            101208080,
            100200266,
            100237001,
            100237002,
            100237003,
            100237004,
            100237005,
            100237006,
            100237007,
            100237008,
            100237009,
            100237010,
            100200270,
            100200267,
            100200268,
            100200269,
            100236001,
            100236002,
            100236003,
            100236004,
            100236005,
            100236006,
            100236007,
            100236008,
            100236009,
            100236010,
            100236011,
            100236016,
            100236017,
            100236018,
            100236019,
            100236020,
            100236021,
            100236022,
            100236023,
            100236024,
            100236025,
            100236026,
            100236031,
            100236032,
            100236033,
            100236034,
            100236035,
            100236036,
            100236037,
            100236038,
            100236039,
            100236040,
            100236041,
            101208081,
            101208082,
            101208083,
            101208084,
            101208085,
            101208086,
            101208087,
            101208088,
            101208089,
            101208090,
            101208091,
            101208092,
            101208093,
            101208094,
            101208095,
            101208096,
            100238001,
            100228002,
            100200271,
            100239001,
            100234001,
            101301001,
            101301002,
            101301003,
            101301004,
            101301005,
            101301006,
            101301007,
            101301008,
            101301009,
            101301010,
            101301011,
            101301012,
            101301013,
            101301014,
            101301015,
            101301016,
            101301017,
            101301018,
            101301019,
            101301020,
            101301021,
            101301022,
            101301023,
            101301024,
            101301025,
            101301026,
            101301027,
            101301028,
            101301029,
            101301030,
            101301031,
            101301032,
            101301033,
            101301034,
            101301035,
            101301036,
            101301037,
            101301038,
            101301039,
            101301040,
            101301041,
            101301042,
            101301043,
            101301044,
            101301045,
            101301046,
            101301047,
            101301048,
            101301049,
            101301050,
            101301051,
            101301052,
            101301053,
            101301054,
            101301055,
            101301056,
            101301057,
            101301058,
            101301059,
            101301060,
            101301061,
            101301062,
            101301063,
            101301064,
            101301065,
            101301066,
            101301067,
            101301068,
            101301069,
            101301070,
            101301071,
            101301072,
            101301073,
            101301074,
            101301075,
            101301076,
            101301077,
            101301078,
            101301079,
            101301080,
            100241001,
            100241002,
            100241003,
            100242001,
            100242002,
            100242003,
            100242024,
            100242025,
            100242026,
            100242057,
            100242058,
            100242059,
            100200273
    };

    int[] newIDsArray = {
            75892194,
            26857786,
            53246495,
            70117791,
            53008933,
            80453041,
            44455560,
            70843274,
            16238373,
            43633088,
            79627627,
            6116731,
            42510430,
            78905039,
            5993144,
            31398842,
            78783557,
            4271596,
            30676200,
            67660909,
            3055018,
            30453613,
            66848311,
            46533533,
            19316241,
            48228390,
            74213995,
            1607603,
            37006702,
            73490417,
            885016,
            36974120,
            63378869,
            9763474,
            35035985,
            62156277,
            8540986,
            35151572,
            61434639,
            98828338,
            34813443,
            96823189,
            38775407,
            74169516,
            1164211,
            37552929,
            63947968,
            342673,
            36436372,
            63825486,
            99229085,
            35614780,
            62002838,
            98007437,
            25592142,
            61980241,
            98385955,
            24779554,
            60764609,
            97262307,
            23657016,
            50042011,
            96030710,
            22435424,
            59829423,
            85314178,
            22712877,
            58707981,
            84192580,
            11590299,
            57985393,
            84079032,
            10474647,
            56863746,
            83257450,
            19652159,
            46640168,
            82135803,
            14529511,
            41924516,
            77313225,
            14307929,
            40702028,
            76290637,
            3685372,
            49689480,
            76078185,
            2463794,
            49867899,
            75352507,
            1340142,
            38745241,
            74139959,
            1528054,
            37613663,
            63017368,
            402416,
            36890111,
            63295720,
            99289828,
            35778533,
            62173132,
            98567237,
            35552985,
            61950680,
            97345699,
            24839398,
            60238002,
            97223101,
            23617756,
            59016454,
            36400569,
            62995268,
            99989863,
            25388971,
            61773610,
            98167225,
            24166324,
            51650038,
            97045737,
            24440742,
            50838440,
            46789706,
            45710945,
            72705654,
            18294799,
            44698398,
            71083002,
            7477101,
            44466810,
            70860415,
            6355563,
            33744268,
            75748977,
            2133971,
            38527680,
            75926389,
            1410324,
            37405032,
            64804137,
            298846,
            37683441,
            63181559,
            99176254,
            66532962,
            18046862,
            45445571,
            71939275,
            8324284,
            44728989,
            70717628,
            7102732,
            33506331,
            70095046,
            6089145,
            32484853,
            69873498,
            5267507,
            32762201,
            68756810,
            4145915,
            31539614,
            7934362,
            34323367,
            70417076,
            6812770,
            33206889,
            69655484,
            6659193,
            32044231,
            69533836,
            95937545,
            31322640,
            68316358,
            28103028,
            3859859,
            3149401,
            55697723,
            81096431,
            27480536,
            54475145,
            80870883,
            27268998,
            53753697,
            29157292,
            56146300,
            92530005,
            29925614,
            55320758,
            81418467,
            28803166,
            54207171,
            81696879,
            17080584,
            53085623,
            80570228,
            16968936,
            43363035,
            89357740,
            15746348,
            42141493,
            84635192,
            11024707,
            47028805,
            73413514,
            10807219,
            46396218,
            73391962,
            9785661,
            46174776,
            72578374,
            8963089,
            35057188,
            71456737,
            8841431,
            34235530,
            70634245,
            7628844,
            33113958,
            60517697,
            6906306,
            32991300,
            69385019,
            95784714,
            32278723,
            68663427,
            94661166,
            21056275,
            67441879,
            94845588,
            20934683,
            66328392,
            93723936,
            29111045,
            56506740,
            92501449,
            29095457,
            55484152,
            81878201,
            28273805,
            54261514,
            81756619,
            17151328,
            53545926,
            80534031,
            16938770,
            53323475,
            89812483,
            15216188,
            42201897,
            88695895,
            15094540,
            41488249,
            77573354,
            14972952,
            40366667,
            77751766,
            53545927,
            67694706,
            93683815,
            20087414,
            66472129,
            93860227,
            29265962,
            55359571,
            92744676,
            28143384,
            55537983,
            81522098,
            23920796,
            50415441,
            86809440,
            13204145,
            59293853,
            87640391,
            35844557,
            72238166,
            8633261,
            34022970,
            61116514,
            7511613,
            34909328,
            60394026,
            6798031,
            33787730,
            69272449,
            96676583,
            32061192,
            21848500,
            95454996,
            68059897,
            68337209,
            94722358,
            20726052,
            57111661,
            93509766,
            20904475,
            56499179,
            92487128,
            29882827,
            55276522,
            82661630,
            28669235,
            55154344,
            81549048,
            72782945,
            8170654,
            44175358,
            48705086,
            93053159,
            17947697,
            54332792,
            80326401,
            17725109,
            43219114,
            89604813,
            16699558,
            42097666,
            99217226,
            3598351,
            30583090,
            76978105,
            3376703,
            39761418,
            65155517,
            2254222,
            38648860,
            65033975,
            91438674,
            37426272,
            64911387,
            90315086,
            27704731,
            63198739,
            99193444,
            26582143,
            62076252,
            99471856,
            25865565,
            52854600,
            98248208,
            24643913,
            51132012,
            87126721,
            54611591,
            81005500,
            24521325,
            50915474,
            86304179,
            13708888,
            59893882,
            86282581,
            12686296,
            58071334,
            85065943,
            11464648,
            48958757,
            84343351,
            10732060,
            47736165,
            73121813,
            10515412,
            46014517,
            72409226,
            9453320,
            45852939,
            72246674,
            4731783,
            41739381,
            77124096,
            3519195,
            30913809,
            76302448,
            3496543,
            39881252,
            65289956,
            2674965,
            38669664,
            5063379,
            31552317,
            77946022,
            4341721,
            30339825,
            67724434,
            3129133,
            39613288,
            66002986,
            2006591,
            39491690,
            65889305,
            91284003,
            38379052,
            64767757,
            91152455,
            27556460,
            63941169,
            90939874,
            26434972,
            53829527,
            42544773,
            29325276,
            47643326,
            10218411,
            23738096,
            50123605,
            86527709,
            22916418,
            59901153,
            85395151,
            12890860,
            58288565,
            84673574,
            11677278,
            43066927,
            80551022,
            16955631,
            42940335,
            19338434,
            55733143,
            34149150,
            68897338,
            94292987,
            21281085,
            57775790,
            93170499,
            20568404,
            52553102,
            89948817,
            25342956,
            51831560,
            88225269,
            24220368,
            51618973,
            87003671,
            13408726,
            50596425,
            98588427,
            20714553,
            12163590,
            48658295,
            75046994,
            11441009,
            48835607,
            74820316,
            6696168,
            42081767,
            79480466,
            5574510,
            31969219,
            65515667,
            44459942,
            91019775,
            97476032,
            88477149,
            65569724,
            80447641,
            17832359,
            43236494,
            79625003,
            16110708,
            42104806,
            79509511,
            5997110,
            42382265,
            74387963,
            875572,
            37260677,
            73664385,
            59080,
            66429798,
            93413793,
            21960890,
            58354899,
            94749594,
            21147203,
            57232301,
            83626916,
            20011655,
            56410769,
            83404468,
            82782870,
            18176525,
            19899073,
            55397172,
            45171524,
            81560239,
            17954937,
            58931850,
            3723262,
            30118811,
            66102515,
            2501624,
            35095329,
            61480937,
            98888032,
            34873741,
            60268386,
            97662494,
            23151193,
            60145298,
            96540807,
            22938501,
            59323650,
            95718355,
            22812963,
            58201062,
            94655777,
            21050476,
            57448410,
            84433129,
            20938824,
            56322832,
            83711531,
            19715246,
            56100345,
            82699999,
            19093698,
            45488703,
            81476402,
            18861006,
            44265115,
            71750854,
            17749468,
            43143567,
            70538272,
            16926971,
            43321985,
            79415624,
            5800323,
            42209438,
            78693036,
            5088741,
            31086840,
            77571454,
            4965193,
            30350202,
            67359907,
            3743515,
            39138610,
            6636319,
            42021064,
            79015062,
            5414777,
            31809476,
            78293584,
            4398189,
            31786838,
            67171933,
            4575541,
            30964246,
            66059345,
            93453053,
            39848658,
            66236707,
            92221402,
            38625110,
            65114115,
            91509824,
            28903523,
            64998567,
            90386276,
            27781371,
            53276089,
            90664684,
            22669793,
            58053438,
            85442146,
            21846145,
            24514503,
            97522863,
            34926568,
            60411677,
            97800311,
            23804920,
            69299029,
            96687733,
            22082432,
            59576447,
            95561146,
            1035143,
            74941992,
            336601,
            37720300,
            75003700,
            1498449,
            44482554,
            70871153,
            7375867,
            33760966,
            79755671,
            6153210,
            32548318,
            69932023,
            5431722,
            31425736,
            68810435,
            4215180,
            31603289,
            67098897,
            93192592,
            30581601,
            66975205,
            93360904,
            29369059,
            65853758,
            92248362,
            28642461,
            55031170,
            91025875,
            27420823,
            54919528,
            90303227,
            27308231,
            53792930,
            80181649,
            26585784,
            12500059,
            49904658,
            75493362,
            12888461,
            48882106,
            74271714,
            1665819,
            47060528,
            14554127,
            40543231,
            76948970,
            13332685,
            49721684,
            76725398,
            12210097,
            49604192,
            57649113,
            38423248,
            25072579,
            10949074,
            36608728,
            62006866,
            9491461,
            35886170,
            62880279,
            8379983,
            35763582,
            61168637,
            97556336,
            34541940,
            60946049,
            97434754,
            23829452,
            9213491,
            36218106,
            62606805,
            95091919,
            31596518,
            67584223,
            94979322,
            30373970,
            67768675,
            93156774,
            29251488,
            66646087,
            92034192,
            29439831,
            55423549,
            91818544,
            28306253,
            54701958,
            81196066,
            27184601,
            53589300,
            80073414,
            26462013,
            53466722,
            89851827,
            16246535,
            52644170,
            88139289,
            15123983,
            41522092,
            88917691,
            14301396,
            40706444,
            77894049,
            13289758,
            40673853,
            76072561,
            12067160,
            49451215,
            75956913,
            2344618,
            74733322,
            48739627,
            37517035,
            1122030,
            74011784,
            483,
            36494597,
            63899196,
            9283801,
            36672909,
            62767644,
            99161253,
            35550352,
            61944066,
            98349765,
            34433770,
            61822419,
            97227123,
            23611122,
            60600821,
            96004535,
            23599634,
            59983249,
            95382988,
            22377092,
            58761791,
            85150300,
            19222426,
            46221535,
            82616239,
            59400890,
            85899505,
            22283204,
            10808715,
            58288218,
            85672957,
            11161666,
            57566760,
            84550369,
            71440209
    };
}